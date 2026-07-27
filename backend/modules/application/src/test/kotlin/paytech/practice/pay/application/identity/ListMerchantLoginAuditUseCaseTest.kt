package paytech.practice.pay.application.identity

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import paytech.practice.pay.application.port.outbound.MerchantLoginAuditEntry
import paytech.practice.pay.application.port.outbound.MerchantLoginAuditProjection
import paytech.practice.pay.domain.identity.LoginOutcome
import paytech.practice.pay.domain.identity.MerchantLoginAuditId
import paytech.practice.pay.domain.merchant.MerchantId
import java.time.Instant

class ListMerchantLoginAuditUseCaseTest :
	FunSpec({

		test("returns recent entries straight from the projection with the default limit") {
			val entry =
				MerchantLoginAuditEntry(
					auditId = MerchantLoginAuditId("mla_001"),
					merchantId = MerchantId("mrc_001"),
					merchantName = "테스트 가맹점",
					attemptedMerchantCode = "test-merchant",
					attemptedLoginId = "owner01",
					userName = "오너",
					outcome = LoginOutcome.SUCCESS,
					clientIp = "203.0.113.7",
					occurredAt = Instant.parse("2026-07-19T00:00:00Z"),
				)
			val projection = mockk<MerchantLoginAuditProjection>()
			every { projection.findRecent(ListMerchantLoginAuditUseCase.DEFAULT_LIMIT) } returns listOf(entry)

			val result = ListMerchantLoginAuditUseCase(projection).execute()

			result.entries shouldBe listOf(entry)
			verify { projection.findRecent(ListMerchantLoginAuditUseCase.DEFAULT_LIMIT) }
		}
	})
