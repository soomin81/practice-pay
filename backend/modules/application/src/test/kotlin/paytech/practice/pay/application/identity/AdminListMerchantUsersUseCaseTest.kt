package paytech.practice.pay.application.identity

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import paytech.practice.pay.application.port.outbound.MerchantUserListProjection
import paytech.practice.pay.application.port.outbound.MerchantUserSummary
import paytech.practice.pay.domain.identity.AccountStatus
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.identity.MerchantUserRole
import paytech.practice.pay.domain.merchant.MerchantId
import java.time.Instant

private val MERCHANT_ID = MerchantId("mrc_test_001")

class AdminListMerchantUsersUseCaseTest :
	FunSpec({

		test("returns the roster for the given merchant, straight from the projection") {
			val summary =
				MerchantUserSummary(
					merchantUserId = MerchantUserId("mu_001"),
					loginId = LoginId("owner01"),
					email = Email("owner01@example.com"),
					userName = "오너",
					role = MerchantUserRole.OWNER,
					status = AccountStatus.ACTIVE,
					lastLoginAt = null,
					createdAt = Instant.parse("2026-07-19T00:00:00Z"),
					pendingInvitationExpiresAt = null,
				)
			val projection = mockk<MerchantUserListProjection>()
			every { projection.findByMerchantId(MERCHANT_ID) } returns listOf(summary)

			val result = AdminListMerchantUsersUseCase(projection).execute(MERCHANT_ID)

			result.merchantUsers shouldBe listOf(summary)
		}
	})
