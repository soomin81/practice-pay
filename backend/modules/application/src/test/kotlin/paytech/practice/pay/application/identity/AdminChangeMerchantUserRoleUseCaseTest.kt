package paytech.practice.pay.application.identity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import paytech.practice.pay.application.port.outbound.MerchantUserRepository
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.MerchantUser
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.identity.MerchantUserRole
import paytech.practice.pay.domain.merchant.MerchantId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private val NOW: Instant = Instant.parse("2026-07-19T00:00:00Z")
private val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
private val MERCHANT_ID = MerchantId("mrc_test_001")
private val TARGET_ID = MerchantUserId("mu_target")

private fun owner(id: MerchantUserId): MerchantUser =
	MerchantUser
		.inviteInitialOwner(
			id = id,
			merchantId = MERCHANT_ID,
			loginId = LoginId("owner-${id.value}"),
			email = Email("${id.value}@example.com"),
			userName = "오너",
			invitedByInternalUserId = InternalUserId("iu_1"),
			createdAt = NOW.minusSeconds(3_600),
		).apply { activate("hashed-password", NOW.minusSeconds(1_800)) }

private fun subAccount(
	id: MerchantUserId,
	role: MerchantUserRole,
): MerchantUser =
	MerchantUser
		.inviteSubAccount(
			id = id,
			merchantId = MERCHANT_ID,
			loginId = LoginId("sub-${id.value}"),
			email = Email("${id.value}@example.com"),
			userName = "하위 계정",
			role = role,
			invitedByMerchantUserId = MerchantUserId("mu_owner"),
			createdAt = NOW.minusSeconds(3_600),
		).apply { activate("hashed-password", NOW.minusSeconds(1_800)) }

private fun command(newRole: MerchantUserRole) =
	AdminChangeMerchantUserRoleCommand(
		merchantId = MERCHANT_ID,
		targetMerchantUserId = TARGET_ID,
		newRole = newRole,
	)

class AdminChangeMerchantUserRoleUseCaseTest :
	FunSpec({

		test("changes a merchant user role") {
			val target = subAccount(TARGET_ID, MerchantUserRole.ADMIN)
			val repository = mockk<MerchantUserRepository>()
			every { repository.findById(TARGET_ID) } returns target
			justRun { repository.save(any()) }

			val result = AdminChangeMerchantUserRoleUseCase(repository, CLOCK).execute(command(MerchantUserRole.VIEWER))

			result.role shouldBe MerchantUserRole.VIEWER
			result.changedAt shouldBe NOW
			verify { repository.save(target) }
		}

		test("promoting to OWNER is rejected by the domain (IllegalArgumentException, mapped to 400)") {
			val repository = mockk<MerchantUserRepository>()
			every { repository.findById(TARGET_ID) } returns subAccount(TARGET_ID, MerchantUserRole.ADMIN)
			justRun { repository.save(any()) }

			shouldThrow<IllegalArgumentException> {
				AdminChangeMerchantUserRoleUseCase(repository, CLOCK).execute(command(MerchantUserRole.OWNER))
			}
		}

		test("the last ACTIVE OWNER cannot be demoted") {
			val repository = mockk<MerchantUserRepository>()
			every { repository.findById(TARGET_ID) } returns owner(TARGET_ID)
			every { repository.countActiveOwners(MERCHANT_ID) } returns 1

			shouldThrow<LastActiveOwnerException> {
				AdminChangeMerchantUserRoleUseCase(repository, CLOCK).execute(command(MerchantUserRole.ADMIN))
			}
		}

		test("an OWNER can be demoted when another ACTIVE OWNER remains") {
			val repository = mockk<MerchantUserRepository>()
			every { repository.findById(TARGET_ID) } returns owner(TARGET_ID)
			every { repository.countActiveOwners(MERCHANT_ID) } returns 2
			justRun { repository.save(any()) }

			val result = AdminChangeMerchantUserRoleUseCase(repository, CLOCK).execute(command(MerchantUserRole.ADMIN))

			result.role shouldBe MerchantUserRole.ADMIN
		}

		test("an unknown target is reported as not found") {
			val repository = mockk<MerchantUserRepository>()
			every { repository.findById(TARGET_ID) } returns null

			shouldThrow<MerchantUserNotFoundException> {
				AdminChangeMerchantUserRoleUseCase(repository, CLOCK).execute(command(MerchantUserRole.VIEWER))
			}
		}

		test("changing the role of a TERMINATED account becomes InvalidMerchantUserTransitionException") {
			val terminated = subAccount(TARGET_ID, MerchantUserRole.ADMIN).apply { terminate(NOW.minusSeconds(60)) }
			val repository = mockk<MerchantUserRepository>()
			every { repository.findById(TARGET_ID) } returns terminated

			shouldThrow<InvalidMerchantUserTransitionException> {
				AdminChangeMerchantUserRoleUseCase(repository, CLOCK).execute(command(MerchantUserRole.VIEWER))
			}
		}
	})
