package paytech.practice.pay.infra.persistence.jooq.payment

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import paytech.practice.pay.dbcore.jooq.tables.Payment.Companion.PAYMENT
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.Payment
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.TokenAmount
import paytech.practice.pay.domain.shared.WalletAddress
import paytech.practice.pay.infra.persistence.jooq.PersistenceTestSupport
import paytech.practice.pay.infra.persistence.jooq.TransactionManagerAdapter
import paytech.practice.pay.infra.persistence.jooq.insertTestMerchant
import paytech.practice.pay.infra.persistence.jooq.uniqueSuffix
import java.time.Instant
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val NOW: Instant = Instant.parse("2026-07-17T00:00:00Z")
private val RECEIVING_WALLET = WalletAddress("0x" + "a".repeat(40))

/**
 * 같은 `Payment`를 두 흐름이 동시에 read-modify-write 할 때 **한쪽 변경이 조용히 사라지지
 * 않는지**(lost update) 고정한다.
 *
 * 실제로 이 경합이 일어나는 경로가 있다 — 고객의 결제 제출(`READY` → `PROCESSING`)과 만료
 * Sweep Worker(`CREATED`/`READY` → `EXPIRED`, 60초 주기)가 같은 결제를 동시에 집을 수 있다.
 * 두 흐름이 각자 자기 사본을 로드해 전이시키면 도메인의 상태 가드는 각 사본에만 적용돼
 * 막지 못하고, 나중에 저장한 쪽이 먼저 저장한 쪽을 덮어쓴다.
 *
 * 방어선은 **변경할 목적의 읽기에 건 행 잠금**이다(`findByIdForUpdate` + 트랜잭션) — 잠금이
 * 없으면 이 테스트는 "둘 다 성공했는데 최종 상태는 하나"로 끝난다.
 */
class PaymentConcurrentWriteTest :
	FunSpec({
		val adapter = PaymentRepositoryAdapter(PersistenceTestSupport.dsl)
		val transactionManager = TransactionManagerAdapter(PersistenceTestSupport.transactionManager)

		test("concurrent read-modify-write on the same Payment does not lose an update") {
			val merchantId = MerchantId(insertTestMerchant())
			val paymentId = PaymentId("pay_${uniqueSuffix()}")
			adapter.save(
				Payment.create(
					id = paymentId,
					merchantId = merchantId,
					merchantOrderId = MerchantOrderId("order-${uniqueSuffix()}"),
					orderName = "테스트 주문",
					orderAmount = Money(10_000),
					paymentAsset = Asset.USDC,
					paymentAmount = TokenAmount(6_666_667),
					tokenDecimals = 6,
					network = BlockchainNetwork.BASE_SEPOLIA,
					receivingWallet = RECEIVING_WALLET,
					expiresAt = NOW.plusSeconds(1_800),
					createdAt = NOW,
				),
			)

			// 두 스레드가 최대한 같은 순간에 "변경할 목적으로 읽기"를 시작하게 맞춘다.
			val barrier = CyclicBarrier(2)
			val pool = Executors.newFixedThreadPool(2)

			/** 로드 → 전이 → 저장을 한 트랜잭션에서 수행한다(실제 Use Case와 같은 모양). */
			fun readModifyWrite(transition: (Payment) -> Unit): Runnable =
				Runnable {
					transactionManager.runInTransaction {
						// 잠금을 잡기 **전에** 두 스레드를 맞춘다 — 잠금 이후에 맞추면 뒤에 온 쪽이
						// 행 잠금에서 대기하느라 barrier에 도달하지 못해 서로를 기다리게 된다.
						barrier.await(10, TimeUnit.SECONDS)
						val payment = adapter.findByIdForUpdate(paymentId)!!
						runCatching { transition(payment) }.onSuccess { adapter.save(payment) }
					}
				}

			// 두 흐름이 **같은 전이**를 시도한다(중복 제출 경합). 순서와 무관하게 결과가 하나로
			// 정해져 단언이 결정적이다 — 서로 다른 전이를 시키면, 잠금 덕에 뒤 흐름이 앞 결과를
			// 보고 또 다른 정당한 전이(예: READY → EXPIRED)를 할 수 있어 순서에 따라 갈린다.
			val first = pool.submit(readModifyWrite { it.ready(NOW.plusSeconds(1)) })
			val second = pool.submit(readModifyWrite { it.ready(NOW.plusSeconds(2)) })
			first.get(30, TimeUnit.SECONDS)
			second.get(30, TimeUnit.SECONDS)
			pool.shutdown()

			adapter.findById(paymentId)!!.status shouldBe PaymentStatus.READY

			// **핵심 단언**: 저장은 딱 한 번만 일어나야 한다. 잠금이 있으면 뒤 흐름은 앞 흐름이
			// 커밋한 `READY`를 보고 도메인이 전이를 거부하므로(`ready()`는 `CREATED`에서만
			// 허용) 저장 자체를 하지 않는다. 잠금이 없으면 둘 다 `CREATED` 사본에서 전이해
			// 두 번 저장하려 든다 — 그게 lost update(또는 version 충돌 실패)다.
			PersistenceTestSupport.dsl
				.select(PAYMENT.VERSION)
				.from(PAYMENT)
				.where(PAYMENT.PAYMENT_ID.eq(paymentId.value))
				.fetchOne(PAYMENT.VERSION) shouldBe 1L
		}
	})
