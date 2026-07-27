package paytech.practice.pay.application.identity

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import paytech.practice.pay.application.port.outbound.InternalLoginAuditEntry
import paytech.practice.pay.application.port.outbound.InternalLoginAuditProjection
import paytech.practice.pay.domain.identity.InternalLoginAuditId
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.LoginOutcome
import java.time.Instant

class ListInternalLoginAuditUseCaseTest :
	FunSpec({

		test("returns recent entries straight from the projection with the default limit") {
			val entry =
				InternalLoginAuditEntry(
					auditId = InternalLoginAuditId("ila_001"),
					internalUserId = InternalUserId("iu_001"),
					attemptedLoginId = "admin01",
					userName = "관리자",
					outcome = LoginOutcome.SUCCESS,
					clientIp = "203.0.113.7",
					occurredAt = Instant.parse("2026-07-19T00:00:00Z"),
				)
			val projection = mockk<InternalLoginAuditProjection>()
			every { projection.findRecent(ListInternalLoginAuditUseCase.DEFAULT_LIMIT) } returns listOf(entry)

			val result = ListInternalLoginAuditUseCase(projection).execute()

			result.entries shouldBe listOf(entry)
			verify { projection.findRecent(ListInternalLoginAuditUseCase.DEFAULT_LIMIT) }
		}
	})
