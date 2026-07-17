package paytech.practice.pay.domain.identity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import paytech.practice.pay.domain.merchant.MerchantId
import java.time.Instant

private val CREATED_AT: Instant = Instant.parse("2026-07-17T00:00:00Z")
private val MERCHANT_ID = MerchantId("mrc_test_001")

private fun initialOwner(): MerchantUser =
	MerchantUser.inviteInitialOwner(
		id = MerchantUserId("mu_test_001"),
		merchantId = MERCHANT_ID,
		loginId = LoginId("owner01"),
		email = Email("owner01@example.com"),
		userName = "오너",
		invitedByInternalUserId = InternalUserId("iu_test_001"),
		createdAt = CREATED_AT,
	)

class MerchantUserTest :
	FunSpec({

		test("inviteInitialOwner creates an INVITED OWNER invited by an internal user") {
			val owner = initialOwner()

			owner.status shouldBe AccountStatus.INVITED
			owner.role shouldBe MerchantUserRole.OWNER
			owner.invitedByInternalUserId shouldBe InternalUserId("iu_test_001")
			owner.invitedByMerchantUserId.shouldBeNull()
		}

		test("inviteSubAccount creates an INVITED ADMIN invited by a merchant user") {
			val admin =
				MerchantUser.inviteSubAccount(
					id = MerchantUserId("mu_test_002"),
					merchantId = MERCHANT_ID,
					loginId = LoginId("admin01"),
					email = Email("admin01@example.com"),
					userName = "관리자",
					role = MerchantUserRole.ADMIN,
					invitedByMerchantUserId = MerchantUserId("mu_test_001"),
					createdAt = CREATED_AT,
				)

			admin.status shouldBe AccountStatus.INVITED
			admin.role shouldBe MerchantUserRole.ADMIN
			admin.invitedByMerchantUserId shouldBe MerchantUserId("mu_test_001")
			admin.invitedByInternalUserId.shouldBeNull()
		}

		test("inviteSubAccount rejects the OWNER role") {
			shouldThrow<IllegalArgumentException> {
				MerchantUser.inviteSubAccount(
					id = MerchantUserId("mu_test_003"),
					merchantId = MERCHANT_ID,
					loginId = LoginId("owner02"),
					email = Email("owner02@example.com"),
					userName = "가짜 오너",
					role = MerchantUserRole.OWNER,
					invitedByMerchantUserId = MerchantUserId("mu_test_001"),
					createdAt = CREATED_AT,
				)
			}
		}

		test("canInviteSubAccounts and canManageApiKeys are true only for ACTIVE OWNER/ADMIN") {
			val owner = initialOwner()
			owner.canInviteSubAccounts() shouldBe false // still INVITED
			owner.activate("hashed-password", CREATED_AT.plusSeconds(1))
			owner.canInviteSubAccounts() shouldBe true
			owner.canManageApiKeys() shouldBe true

			val viewer =
				MerchantUser.inviteSubAccount(
					id = MerchantUserId("mu_test_004"),
					merchantId = MERCHANT_ID,
					loginId = LoginId("viewer01"),
					email = Email("viewer01@example.com"),
					userName = "뷰어",
					role = MerchantUserRole.VIEWER,
					invitedByMerchantUserId = MerchantUserId("mu_test_001"),
					createdAt = CREATED_AT,
				)
			viewer.activate("hashed-password", CREATED_AT.plusSeconds(1))
			viewer.canInviteSubAccounts() shouldBe false
			viewer.canManageApiKeys() shouldBe false
		}

		test("activate moves INVITED to ACTIVE") {
			val owner = initialOwner()
			val activatedAt = CREATED_AT.plusSeconds(1)

			owner.activate("hashed-password", activatedAt)

			owner.status shouldBe AccountStatus.ACTIVE
			owner.passwordHash shouldBe "hashed-password"
		}

		test("full account lifecycle: lock/unlock, suspend/reactivate, terminate") {
			val owner = initialOwner()
			owner.activate("hashed-password", CREATED_AT.plusSeconds(1))

			owner.lock(CREATED_AT.plusSeconds(600), CREATED_AT.plusSeconds(2))
			owner.status shouldBe AccountStatus.LOCKED

			owner.unlock(CREATED_AT.plusSeconds(3))
			owner.status shouldBe AccountStatus.ACTIVE

			owner.suspend(CREATED_AT.plusSeconds(4))
			owner.status shouldBe AccountStatus.SUSPENDED

			owner.reactivate(CREATED_AT.plusSeconds(5))
			owner.status shouldBe AccountStatus.ACTIVE

			owner.terminate(CREATED_AT.plusSeconds(6))
			owner.status shouldBe AccountStatus.TERMINATED

			shouldThrow<IllegalStateException> { owner.suspend(CREATED_AT.plusSeconds(7)) }
		}

		test("reconstitute rejects setting both inviter fields") {
			shouldThrow<IllegalArgumentException> {
				MerchantUser.reconstitute(
					id = MerchantUserId("mu_test_005"),
					merchantId = MERCHANT_ID,
					loginId = LoginId("broken"),
					email = Email("broken@example.com"),
					userName = "broken",
					role = MerchantUserRole.ADMIN,
					invitedByInternalUserId = InternalUserId("iu_test_001"),
					invitedByMerchantUserId = MerchantUserId("mu_test_001"),
					createdAt = CREATED_AT,
					status = AccountStatus.INVITED,
					passwordHash = null,
					failedLoginCount = 0,
					lockedUntil = null,
					passwordChangedAt = null,
					lastLoginAt = null,
					invitedAt = CREATED_AT,
					activatedAt = null,
					terminatedAt = null,
					updatedAt = CREATED_AT,
				)
			}
		}
	})
