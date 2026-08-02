package paytech.practice.pay.infra.persistence.jooq.payment

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import paytech.practice.pay.application.port.outbound.PaymentListQuery
import paytech.practice.pay.domain.blockchain.BlockchainTransaction
import paytech.practice.pay.domain.blockchain.BlockchainTransactionId
import paytech.practice.pay.domain.blockchain.ChainId
import paytech.practice.pay.domain.blockchain.TransactionHash
import paytech.practice.pay.domain.blockchain.TransactionType
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.infra.persistence.jooq.PersistenceTestSupport
import paytech.practice.pay.infra.persistence.jooq.blockchain.BlockchainTransactionRepositoryAdapter
import paytech.practice.pay.infra.persistence.jooq.insertTestMerchant
import paytech.practice.pay.infra.persistence.jooq.insertTestPayment
import paytech.practice.pay.infra.persistence.jooq.uniqueSuffix
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

private val BASE_TIME: LocalDateTime = LocalDateTime.parse("2026-07-20T10:00:00")

private fun query(
	merchantId: MerchantId? = null,
	status: PaymentStatus? = null,
	createdFrom: Instant? = null,
	createdTo: Instant? = null,
	page: Int = 0,
	size: Int = 50,
) = PaymentListQuery(
	merchantId = merchantId,
	status = status,
	createdFrom = createdFrom,
	createdTo = createdTo,
	page = page,
	size = size,
)

/**
 * 이 테스트는 **DB가 공유된다**(테스트 JVM 전체가 하나의 Testcontainers MySQL을 쓴다).
 * 다른 테스트가 남긴 결제 행이 함께 조회되므로, 단언은 항상 이 테스트가 만든
 * `merchantId`로 범위를 좁힌 뒤에 한다 — 전역 건수를 세지 않는다.
 */
class PaymentListProjectionAdapterTest :
	FunSpec({
		val projection = PaymentListProjectionAdapter(PersistenceTestSupport.dsl)

		test("filters by merchant and returns newest first with the total count") {
			val merchantId = insertTestMerchant()
			val other = insertTestMerchant()
			insertTestPayment(merchantId, paymentId = "pay_old_${uniqueSuffix()}", createdAt = BASE_TIME)
			insertTestPayment(merchantId, paymentId = "pay_new_${uniqueSuffix()}", createdAt = BASE_TIME.plusMinutes(10))
			insertTestPayment(other)

			val page = projection.find(query(merchantId = MerchantId(merchantId)))

			page.totalCount shouldBe 2L
			page.entries.size shouldBe 2
			page.entries.first().createdAt shouldBe BASE_TIME.plusMinutes(10).toInstant(ZoneOffset.UTC)
			page.entries.all { it.merchantId == MerchantId(merchantId) } shouldBe true
		}

		test("filters by status and by created-at range") {
			val merchantId = insertTestMerchant()
			insertTestPayment(merchantId, paymentStatus = "SUCCEEDED", createdAt = BASE_TIME)
			insertTestPayment(merchantId, paymentStatus = "EXPIRED", createdAt = BASE_TIME.plusDays(2))

			projection.find(query(merchantId = MerchantId(merchantId), status = PaymentStatus.SUCCEEDED)).totalCount shouldBe 1L

			val rangeOnly =
				projection.find(
					query(
						merchantId = MerchantId(merchantId),
						createdFrom = BASE_TIME.plusDays(1).toInstant(ZoneOffset.UTC),
					),
				)
			rangeOnly.totalCount shouldBe 1L
			rangeOnly.entries.single().status shouldBe PaymentStatus.EXPIRED
		}

		/**
		 * **총 건수는 행이 0건인 페이지에서도 나와야 한다** — 백오피스가 마지막 페이지를
		 * 넘어선 요청에서도 "전체 N건"을 그려야 해서, 윈도우 함수 대신 COUNT를 따로 돌린다.
		 */
		test("keeps the total count on a page beyond the last one") {
			val merchantId = insertTestMerchant()
			insertTestPayment(merchantId)
			insertTestPayment(merchantId)

			val page = projection.find(query(merchantId = MerchantId(merchantId), page = 5, size = 10))

			page.entries.size shouldBe 0
			page.totalCount shouldBe 2L
		}

		/**
		 * **집계는 `SUCCEEDED`만 더한다.** 전체 주문 금액을 더하면 만료·실패한 결제까지
		 * 매출처럼 보이는데, 그 숫자는 사람이 반드시 오해한다.
		 *
		 * 금액을 행마다 다르게 넣는 이유는 `SUM`이 아니라 `건수 × 상수`로 구현해도
		 * 통과하는 테스트가 되지 않게 하기 위해서다.
		 */
		test("sums only SUCCEEDED order amounts and counts them separately") {
			val merchantId = insertTestMerchant()
			insertTestPayment(merchantId, paymentStatus = "SUCCEEDED", orderAmount = 20_000)
			insertTestPayment(merchantId, paymentStatus = "SUCCEEDED", orderAmount = 35_000)
			insertTestPayment(merchantId, paymentStatus = "EXPIRED", orderAmount = 99_000)
			insertTestPayment(merchantId, paymentStatus = "READY", orderAmount = 77_000)

			val page = projection.find(query(merchantId = MerchantId(merchantId)))

			page.totalCount shouldBe 4L
			page.succeededCount shouldBe 2L
			page.succeededAmount shouldBe Money(55_000)
		}

		/**
		 * 집계는 목록과 **같은 필터**에 걸려야 한다 — 필터를 좁혔는데 합계가 전체 값을
		 * 그대로 두면 화면의 두 숫자가 서로를 부정한다.
		 */
		test("applies the same filters to the aggregates as to the rows") {
			val merchantId = insertTestMerchant()
			val other = insertTestMerchant()
			insertTestPayment(merchantId, paymentStatus = "SUCCEEDED", orderAmount = 20_000)
			insertTestPayment(other, paymentStatus = "SUCCEEDED", orderAmount = 50_000)

			val page = projection.find(query(merchantId = MerchantId(merchantId)))

			page.succeededAmount shouldBe Money(20_000)
		}

		/** 조건에 맞는 행이 없으면 `SUM`은 NULL이라 화면이 "—"가 된다 — 0으로 내려야 한다. */
		test("reports zero rather than null when nothing matches") {
			val page = projection.find(query(merchantId = MerchantId("mrc_no_such_${uniqueSuffix()}")))

			page.totalCount shouldBe 0L
			page.succeededCount shouldBe 0L
			page.succeededAmount shouldBe Money(0)
		}

		test("paginates without repeating or dropping rows when created_at ties") {
			val merchantId = insertTestMerchant()
			repeat(4) { insertTestPayment(merchantId, createdAt = BASE_TIME) }

			val first = projection.find(query(merchantId = MerchantId(merchantId), page = 0, size = 2))
			val second = projection.find(query(merchantId = MerchantId(merchantId), page = 1, size = 2))

			val seen = (first.entries + second.entries).map { it.paymentId.value }
			seen.size shouldBe 4
			seen.toSet().size shouldBe 4
		}

		// 고객이 Hash를 제출하기 전에는 blockchain_transaction이 없다 — LEFT JOIN이라
		// 그 결제도 목록에서 빠지지 않아야 한다.
		test("includes payments without a blockchain transaction and exposes the hash once submitted") {
			val merchantId = insertTestMerchant()
			val withoutHash = insertTestPayment(merchantId)
			val withHash = insertTestPayment(merchantId)
			val hash = TransactionHash("0x" + "c".repeat(64))
			BlockchainTransactionRepositoryAdapter(PersistenceTestSupport.dsl).save(
				BlockchainTransaction.create(
					id = BlockchainTransactionId("btx_${uniqueSuffix()}"),
					paymentId = PaymentId(withHash),
					transactionType = TransactionType.PAYMENT,
					network = BlockchainNetwork.BASE_SEPOLIA,
					chainId = ChainId(84_532),
					transactionHash = hash,
					fromAddress = null,
					toAddress = null,
					tokenContractAddress = null,
					tokenAsset = Asset.USDC,
					amountMinor = null,
					requiredConfirmationCount = 12,
					submittedAt = Instant.parse("2026-07-20T10:05:00Z"),
				),
			)

			val entries = projection.find(query(merchantId = MerchantId(merchantId))).entries

			entries.size shouldBe 2
			entries
				.single { it.paymentId.value == withHash }
				.transactionHash
				.shouldNotBeNull()
				.value shouldBe hash.value
			entries.single { it.paymentId.value == withoutHash }.transactionHash.shouldBeNull()
		}
	})
