package paytech.practice.pay.application.settlement

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import paytech.practice.pay.application.port.outbound.SettlementHoldAuditRepository
import paytech.practice.pay.application.port.outbound.SettlementReceivableRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.domain.exchange.ExchangeOrderId
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.settlement.SettlementHoldAction
import paytech.practice.pay.domain.settlement.SettlementHoldAudit
import paytech.practice.pay.domain.settlement.SettlementReceivable
import paytech.practice.pay.domain.settlement.SettlementReceivableId
import paytech.practice.pay.domain.settlement.SettlementReceivableStatus
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.SignedMoney
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val NOW: Instant = Instant.parse("2026-08-02T00:00:00Z")
private val FIXED_CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
private val RECEIVABLE_ID = SettlementReceivableId("stl_test_001")
private val ACTOR_ID = InternalUserId("iu_test_001")

internal class SettlementImmediateTransactionManager : TransactionManager {
	override fun <T> runInTransaction(block: () -> T): T = block()
}

/** 매도 전(`PENDING`)에 막힌 채권. */
internal fun heldPendingReceivable(): SettlementReceivable =
	SettlementReceivable
		.create(
			id = RECEIVABLE_ID,
			paymentId = PaymentId("pay_test_001"),
			merchantId = MerchantId("mrc_test_001"),
			grossAmount = Money(20_000),
			feeRate = BigDecimal("0.015"),
			feeAmount = Money(300),
			adjustmentAmount = SignedMoney(0),
			eligibleDate = LocalDate.parse("2026-08-01"),
			createdAt = NOW.minusSeconds(300),
		).apply { hold("TRANSACTION_REORGED", NOW.minusSeconds(100)) }

/** 매도가 끝난 뒤(`READY`)에 막힌 채권. */
internal fun heldReadyReceivable(): SettlementReceivable =
	SettlementReceivable
		.create(
			id = RECEIVABLE_ID,
			paymentId = PaymentId("pay_test_001"),
			merchantId = MerchantId("mrc_test_001"),
			grossAmount = Money(20_000),
			feeRate = BigDecimal("0.015"),
			feeAmount = Money(300),
			adjustmentAmount = SignedMoney(0),
			eligibleDate = LocalDate.parse("2026-08-01"),
			createdAt = NOW.minusSeconds(300),
		).apply {
			markReady(ExchangeOrderId("exo_test_001"), Money(20_101), SignedMoney(101), NOW.minusSeconds(200))
			hold("TRANSACTION_REORGED", NOW.minusSeconds(100))
		}

private fun newUseCase(
	receivableRepository: SettlementReceivableRepository,
	auditRepository: SettlementHoldAuditRepository = mockk(relaxed = true),
): ReleaseSettlementHoldUseCase =
	ReleaseSettlementHoldUseCase(
		settlementReceivableRepository = receivableRepository,
		settlementHoldAuditRepository = auditRepository,
		idGenerator = { "generated-id" },
		transactionManager = SettlementImmediateTransactionManager(),
		clock = FIXED_CLOCK,
	)

private fun command(note: String = "탐지 오류로 확인되어 해제합니다.") = ReleaseSettlementHoldCommand(RECEIVABLE_ID, ACTOR_ID, note)

class ReleaseSettlementHoldUseCaseTest :
	FunSpec({

		/**
		 * **돌아갈 상태를 요청이 정하지 않는다**는 것이 이 Use Case의 핵심이다 — 매도가
		 * 끝났으면 `READY`, 아니면 `PENDING`이다. 화면이 정하게 두면 매도 전 채권이 `READY`가
		 * 되어 근거 없는 정산 금액이 생긴다.
		 */
		test("returns a receivable held after the exchange to READY") {
			val repository = mockk<SettlementReceivableRepository>(relaxed = true)
			val receivable = heldReadyReceivable()
			every { repository.findById(RECEIVABLE_ID) } returns receivable

			val result = newUseCase(repository).execute(command())

			result.status shouldBe SettlementReceivableStatus.READY
			receivable.status shouldBe SettlementReceivableStatus.READY
			receivable.holdReasonCode shouldBe null
			verify(exactly = 1) { repository.save(receivable) }
		}

		test("returns a receivable held before the exchange to PENDING") {
			val repository = mockk<SettlementReceivableRepository>(relaxed = true)
			every { repository.findById(RECEIVABLE_ID) } returns heldPendingReceivable()

			val result = newUseCase(repository).execute(command())

			result.status shouldBe SettlementReceivableStatus.PENDING
		}

		/**
		 * `holdReasonCode`가 지워지므로 **막혔던 사실은 이력에만 남는다** — 이력이 함께
		 * 저장되지 않으면 "누가 왜 풀었나"에 영영 답할 수 없다.
		 */
		test("records who released it and why") {
			val repository = mockk<SettlementReceivableRepository>(relaxed = true)
			val auditRepository = mockk<SettlementHoldAuditRepository>(relaxed = true)
			val audit = slot<SettlementHoldAudit>()
			every { repository.findById(RECEIVABLE_ID) } returns heldReadyReceivable()
			every { auditRepository.append(capture(audit)) } returns Unit

			newUseCase(repository, auditRepository).execute(command("탐지 오류로 확인되어 해제합니다."))

			audit.captured.action shouldBe SettlementHoldAction.RELEASED
			audit.captured.internalUserId shouldBe ACTOR_ID
			audit.captured.note shouldBe "탐지 오류로 확인되어 해제합니다."
			// 사유 코드는 보류에만 붙는다 — 해제는 자유 메모가 그 자리를 대신한다.
			audit.captured.reasonCode shouldBe null
			audit.captured.occurredAt shouldBe NOW
		}

		/** 자동 경로가 없는 전이라 실행한 사람 말고는 이유를 아는 곳이 없다. */
		test("refuses a blank note") {
			val repository = mockk<SettlementReceivableRepository>(relaxed = true)
			every { repository.findById(RECEIVABLE_ID) } returns heldReadyReceivable()

			shouldThrow<IllegalArgumentException> { newUseCase(repository).execute(command("   ")) }
		}

		/** 사유 검사가 조회보다 먼저라 빈 사유로는 아무것도 건드리지 않는다. */
		test("does not touch anything when the note is blank") {
			val repository = mockk<SettlementReceivableRepository>(relaxed = true)

			shouldThrow<IllegalArgumentException> { newUseCase(repository).execute(command("")) }

			verify(exactly = 0) { repository.save(any()) }
		}

		test("an unknown receivable is reported as not found") {
			val repository = mockk<SettlementReceivableRepository>(relaxed = true)
			every { repository.findById(RECEIVABLE_ID) } returns null

			shouldThrow<SettlementReceivableNotFoundException> { newUseCase(repository).execute(command()) }
		}

		/** 이미 풀렸는지 취소됐는지에 따라 운영자의 다음 행동이 달라진다 — 현재 상태를 담는다. */
		test("refuses a receivable that is not held and says its current status") {
			val repository = mockk<SettlementReceivableRepository>(relaxed = true)
			val released = heldReadyReceivable().apply { release(NOW.minusSeconds(50)) }
			every { repository.findById(RECEIVABLE_ID) } returns released

			val exception =
				shouldThrow<SettlementReceivableNotReleasableException> { newUseCase(repository).execute(command()) }

			exception.status shouldBe SettlementReceivableStatus.READY
			verify(exactly = 0) { repository.save(any()) }
		}
	})
