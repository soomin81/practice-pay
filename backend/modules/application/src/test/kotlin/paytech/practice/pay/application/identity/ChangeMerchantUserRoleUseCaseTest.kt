package paytech.practice.pay.application.identity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
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
private val REQUESTER_ID = MerchantUserId("mu_requester")
private val TARGET_ID = MerchantUserId("mu_target")

private fun activeOwner(id: MerchantUserId): MerchantUser =
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

private fun activeSubAccount(
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
	ChangeMerchantUserRoleCommand(
		targetMerchantUserId = TARGET_ID,
		newRole = newRole,
		requestedByMerchantUserId = REQUESTER_ID,
	)

class ChangeMerchantUserRoleUseCaseTest :
	FunSpec({

		test("an OWNER changes a sub-account's role") {
			val target = activeSubAccount(TARGET_ID, MerchantUserRole.ADMIN)
			val repository = mockk<MerchantUserRepository>()
			every { repository.findById(REQUESTER_ID) } returns activeOwner(REQUESTER_ID)
			every { repository.findById(TARGET_ID) } returns target
			justRun { repository.save(any()) }

			val result = ChangeMerchantUserRoleUseCase(repository, CLOCK).execute(command(MerchantUserRole.VIEWER))

			result.role shouldBe MerchantUserRole.VIEWER
			result.changedAt shouldBe NOW
		}

		test("promoting to OWNER is rejected by the domain") {
			val repository = mockk<MerchantUserRepository>()
			every { repository.findById(REQUESTER_ID) } returns activeOwner(REQUESTER_ID)
			every { repository.findById(TARGET_ID) } returns activeSubAccount(TARGET_ID, MerchantUserRole.ADMIN)
			every { repository.countActiveOwners(any()) } returns 2

			shouldThrow<IllegalArgumentException> {
				ChangeMerchantUserRoleUseCase(repository, CLOCK).execute(command(MerchantUserRole.OWNER))
			}
		}

		test("the last ACTIVE OWNER cannot be demoted") {
			val repository = mockk<MerchantUserRepository>()
			every { repository.findById(REQUESTER_ID) } returns activeOwner(REQUESTER_ID)
			every { repository.findById(TARGET_ID) } returns activeOwner(TARGET_ID)
			every { repository.countActiveOwners(MERCHANT_ID) } returns 1

			shouldThrow<LastActiveOwnerException> {
				ChangeMerchantUserRoleUseCase(repository, CLOCK).execute(command(MerchantUserRole.ADMIN))
			}
		}

		test("an ADMIN cannot change an OWNER's role") {
			// docs/architecture/identity-access-api-key.md 4.4: ADMIN은 기존 OWNER의 권한을 변경할 수 없다.
			val repository = mockk<MerchantUserRepository>()
			every { repository.findById(REQUESTER_ID) } returns activeSubAccount(REQUESTER_ID, MerchantUserRole.ADMIN)
			every { repository.findById(TARGET_ID) } returns activeOwner(TARGET_ID)

			shouldThrow<MerchantUserNotManageableException> {
				ChangeMerchantUserRoleUseCase(repository, CLOCK).execute(command(MerchantUserRole.VIEWER))
			}
		}

		test("changing your own role is rejected") {
			val repository = mockk<MerchantUserRepository>()
			every { repository.findById(REQUESTER_ID) } returns activeOwner(REQUESTER_ID)

			shouldThrow<MerchantUserNotManageableException> {
				ChangeMerchantUserRoleUseCase(repository, CLOCK).execute(
					command(MerchantUserRole.VIEWER).copy(targetMerchantUserId = REQUESTER_ID),
				)
			}
		}
	})
