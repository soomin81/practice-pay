package paytech.practice.pay.application.settlement

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import paytech.practice.pay.application.port.outbound.SettlementHoldAuditEntry
import paytech.practice.pay.application.port.outbound.SettlementHoldAuditProjection
import paytech.practice.pay.application.port.outbound.SettlementReceivableRepository
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.settlement.SettlementHoldAction
import paytech.practice.pay.domain.settlement.SettlementHoldAuditId
import paytech.practice.pay.domain.settlement.SettlementReceivableId
import java.time.Instant

private val RECEIVABLE_ID = SettlementReceivableId("stl_test_001")

private fun entry(action: SettlementHoldAction) =
	SettlementHoldAuditEntry(
		auditId = SettlementHoldAuditId("sha_test_001"),
		internalUserId = InternalUserId("iu_test_001"),
		internalUserName = "운영자",
		action = action,
		reasonCode = null,
		note = "메모",
		occurredAt = Instant.parse("2026-08-02T00:00:00Z"),
	)

class ListSettlementHoldHistoryUseCaseTest :
	FunSpec({

		test("returns the history of the given receivable") {
			val repository = mockk<SettlementReceivableRepository>(relaxed = true)
			val projection = mockk<SettlementHoldAuditProjection>()
			every { repository.findById(RECEIVABLE_ID) } returns heldReadyReceivable()
			every { projection.findByReceivableId(RECEIVABLE_ID) } returns listOf(entry(SettlementHoldAction.RELEASED))

			val result = ListSettlementHoldHistoryUseCase(repository, projection).execute(RECEIVABLE_ID)

			result.entries.single().action shouldBe SettlementHoldAction.RELEASED
		}

		/**
		 * **빈 이력과 없는 채권은 다른 사실이다.** 둘을 같은 응답으로 뭉개면 잘못된 ID로
		 * 조회한 운영자가 "이 채권은 손댄 적이 없다"고 읽는다.
		 */
		test("an unknown receivable is reported as not found, not as an empty history") {
			val repository = mockk<SettlementReceivableRepository>(relaxed = true)
			val projection = mockk<SettlementHoldAuditProjection>(relaxed = true)
			every { repository.findById(RECEIVABLE_ID) } returns null

			shouldThrow<SettlementReceivableNotFoundException> {
				ListSettlementHoldHistoryUseCase(repository, projection).execute(RECEIVABLE_ID)
			}
		}

		test("a receivable that was never touched has an empty history") {
			val repository = mockk<SettlementReceivableRepository>(relaxed = true)
			val projection = mockk<SettlementHoldAuditProjection>()
			every { repository.findById(RECEIVABLE_ID) } returns heldReadyReceivable()
			every { projection.findByReceivableId(RECEIVABLE_ID) } returns emptyList()

			ListSettlementHoldHistoryUseCase(repository, projection).execute(RECEIVABLE_ID).entries shouldBe emptyList()
		}
	})
