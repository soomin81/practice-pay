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
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.settlement.SettlementHoldAction
import paytech.practice.pay.domain.settlement.SettlementHoldAudit
import paytech.practice.pay.domain.settlement.SettlementReceivableId
import paytech.practice.pay.domain.settlement.SettlementReceivableStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private val NOW: Instant = Instant.parse("2026-08-02T00:00:00Z")
private val FIXED_CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
private val RECEIVABLE_ID = SettlementReceivableId("stl_test_001")
private val ACTOR_ID = InternalUserId("iu_test_001")

private fun newUseCase(
	receivableRepository: SettlementReceivableRepository,
	auditRepository: SettlementHoldAuditRepository = mockk(relaxed = true),
): CancelSettlementReceivableUseCase =
	CancelSettlementReceivableUseCase(
		settlementReceivableRepository = receivableRepository,
		settlementHoldAuditRepository = auditRepository,
		idGenerator = { "generated-id" },
		transactionManager = SettlementImmediateTransactionManager(),
		clock = FIXED_CLOCK,
	)

private fun command(note: String = "가맹점과 합의해 정산하지 않습니다.") = CancelSettlementReceivableCommand(RECEIVABLE_ID, ACTOR_ID, note)

class CancelSettlementReceivableUseCaseTest :
	FunSpec({

		test("cancels a held receivable") {
			val repository = mockk<SettlementReceivableRepository>(relaxed = true)
			val receivable = heldReadyReceivable()
			every { repository.findById(RECEIVABLE_ID) } returns receivable

			val result = newUseCase(repository).execute(command())

			result.status shouldBe SettlementReceivableStatus.CANCELLED
			receivable.status shouldBe SettlementReceivableStatus.CANCELLED
			verify(exactly = 1) { repository.save(receivable) }
		}

		test("records who cancelled it and why") {
			val repository = mockk<SettlementReceivableRepository>(relaxed = true)
			val auditRepository = mockk<SettlementHoldAuditRepository>(relaxed = true)
			val audit = slot<SettlementHoldAudit>()
			every { repository.findById(RECEIVABLE_ID) } returns heldReadyReceivable()
			every { auditRepository.append(capture(audit)) } returns Unit

			newUseCase(repository, auditRepository).execute(command("가맹점과 합의해 정산하지 않습니다."))

			audit.captured.action shouldBe SettlementHoldAction.CANCELLED
			audit.captured.internalUserId shouldBe ACTOR_ID
			audit.captured.note shouldBe "가맹점과 합의해 정산하지 않습니다."
		}

		test("refuses a blank note") {
			val repository = mockk<SettlementReceivableRepository>(relaxed = true)
			every { repository.findById(RECEIVABLE_ID) } returns heldReadyReceivable()

			shouldThrow<IllegalArgumentException> { newUseCase(repository).execute(command("   ")) }
		}

		test("an unknown receivable is reported as not found") {
			val repository = mockk<SettlementReceivableRepository>(relaxed = true)
			every { repository.findById(RECEIVABLE_ID) } returns null

			shouldThrow<SettlementReceivableNotFoundException> { newUseCase(repository).execute(command()) }
		}

		/**
		 * **종료 상태는 재사용하지 않는다.** 두 번째 취소를 조용히 통과시키면 같은 채권에
		 * 취소 이력만 쌓여 "언제 끝났나"가 흐려진다.
		 */
		test("refuses a receivable that is already cancelled") {
			val repository = mockk<SettlementReceivableRepository>(relaxed = true)
			val cancelled = heldReadyReceivable().apply { cancel(NOW.minusSeconds(50)) }
			every { repository.findById(RECEIVABLE_ID) } returns cancelled

			val exception =
				shouldThrow<SettlementReceivableNotCancellableException> { newUseCase(repository).execute(command()) }

			exception.status shouldBe SettlementReceivableStatus.CANCELLED
			verify(exactly = 0) { repository.save(any()) }
		}

		/**
		 * **도메인이 허용하는 것을 애플리케이션이 다시 좁히지 않는다** — 화면이 `HELD` 행에만
		 * 버튼을 그리는 것은 UX 제약이고, 서버가 그것까지 강제하면 나중에 정당한 경로가
		 * 생겼을 때 두 곳을 고쳐야 한다.
		 */
		test("allows cancelling a receivable that was never held") {
			val repository = mockk<SettlementReceivableRepository>(relaxed = true)
			val pending = heldPendingReceivable().apply { release(NOW.minusSeconds(50)) }
			every { repository.findById(RECEIVABLE_ID) } returns pending

			newUseCase(repository).execute(command()).status shouldBe SettlementReceivableStatus.CANCELLED
		}
	})
