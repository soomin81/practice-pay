package paytech.practice.pay.application.identity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import paytech.practice.pay.application.port.outbound.MerchantUserRepository
import paytech.practice.pay.domain.identity.AccountStatus
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
private val OTHER_MERCHANT_ID = MerchantId("mrc_other")
private val TARGET_ID = MerchantUserId("mu_target")

private fun owner(
	id: MerchantUserId,
	merchantId: MerchantId = MERCHANT_ID,
): MerchantUser =
	MerchantUser
		.inviteInitialOwner(
			id = id,
			merchantId = merchantId,
			loginId = LoginId("owner-${id.value}"),
			email = Email("${id.value}@example.com"),
			userName = "오너",
			invitedByInternalUserId = InternalUserId("iu_1"),
			createdAt = NOW.minusSeconds(3_600),
		).apply { activate("hashed-password", NOW.minusSeconds(1_800)) }

private fun subAccount(
	id: MerchantUserId,
	role: MerchantUserRole,
	merchantId: MerchantId = MERCHANT_ID,
): MerchantUser =
	MerchantUser
		.inviteSubAccount(
			id = id,
			merchantId = merchantId,
			loginId = LoginId("sub-${id.value}"),
			email = Email("${id.value}@example.com"),
			userName = "하위 계정",
			role = role,
			invitedByMerchantUserId = MerchantUserId("mu_owner"),
			createdAt = NOW.minusSeconds(3_600),
		).apply { activate("hashed-password", NOW.minusSeconds(1_800)) }

private fun command(action: MerchantUserStatusAction) =
	AdminChangeMerchantUserStatusCommand(
		merchantId = MERCHANT_ID,
		targetMerchantUserId = TARGET_ID,
		action = action,
	)

class AdminChangeMerchantUserStatusUseCaseTest :
	FunSpec({

		test("an internal operator suspends an ACTIVE merchant sub-account") {
			val target = subAccount(TARGET_ID, MerchantUserRole.ADMIN)
			val repository = mockk<MerchantUserRepository>()
			every { repository.findById(TARGET_ID) } returns target
			justRun { repository.save(any()) }

			val result = AdminChangeMerchantUserStatusUseCase(repository, CLOCK).execute(command(MerchantUserStatusAction.SUSPEND))

			result.status shouldBe AccountStatus.SUSPENDED
			result.changedAt shouldBe NOW
			verify { repository.save(target) }
		}

		test("REACTIVATE and TERMINATE call the matching domain transition") {
			val suspended = subAccount(TARGET_ID, MerchantUserRole.ADMIN).apply { suspend(NOW.minusSeconds(60)) }
			val repository = mockk<MerchantUserRepository>()
			every { repository.findById(TARGET_ID) } returns suspended
			every { repository.countActiveOwners(any()) } returns 1
			justRun { repository.save(any()) }
			val useCase = AdminChangeMerchantUserStatusUseCase(repository, CLOCK)

			useCase.execute(command(MerchantUserStatusAction.REACTIVATE)).status shouldBe AccountStatus.ACTIVE
			useCase.execute(command(MerchantUserStatusAction.TERMINATE)).status shouldBe AccountStatus.TERMINATED
		}

		test("the last ACTIVE OWNER cannot be suspended — reachable via this HTTP path for the first time") {
			// merchant-side에서는 요청자 자기 자신 차단·ADMIN 제한 때문에 도달할 수 없던 방어선이다.
			val repository = mockk<MerchantUserRepository>()
			every { repository.findById(TARGET_ID) } returns owner(TARGET_ID)
			every { repository.countActiveOwners(MERCHANT_ID) } returns 1

			shouldThrow<LastActiveOwnerException> {
				AdminChangeMerchantUserStatusUseCase(repository, CLOCK).execute(command(MerchantUserStatusAction.SUSPEND))
			}
		}

		test("an OWNER can be suspended when another ACTIVE OWNER remains") {
			val repository = mockk<MerchantUserRepository>()
			every { repository.findById(TARGET_ID) } returns owner(TARGET_ID)
			every { repository.countActiveOwners(MERCHANT_ID) } returns 2
			justRun { repository.save(any()) }

			val result = AdminChangeMerchantUserStatusUseCase(repository, CLOCK).execute(command(MerchantUserStatusAction.SUSPEND))

			result.status shouldBe AccountStatus.SUSPENDED
		}

		test("REACTIVATE never consults the last-owner invariant") {
			val repository = mockk<MerchantUserRepository>()
			every { repository.findById(TARGET_ID) } returns owner(TARGET_ID).apply { suspend(NOW.minusSeconds(60)) }
			justRun { repository.save(any()) }

			AdminChangeMerchantUserStatusUseCase(repository, CLOCK).execute(command(MerchantUserStatusAction.REACTIVATE))

			verify(exactly = 0) { repository.countActiveOwners(any()) }
		}

		test("a target in another merchant is reported as not found, never as forbidden") {
			// 경로가 지정한 가맹점 소속이 아니면 존재 여부를 숨긴다.
			val repository = mockk<MerchantUserRepository>()
			every { repository.findById(TARGET_ID) } returns
				subAccount(TARGET_ID, MerchantUserRole.ADMIN, merchantId = OTHER_MERCHANT_ID)

			shouldThrow<MerchantUserNotFoundException> {
				AdminChangeMerchantUserStatusUseCase(repository, CLOCK).execute(command(MerchantUserStatusAction.SUSPEND))
			}
		}

		test("an unknown target is reported as not found") {
			val repository = mockk<MerchantUserRepository>()
			every { repository.findById(TARGET_ID) } returns null

			shouldThrow<MerchantUserNotFoundException> {
				AdminChangeMerchantUserStatusUseCase(repository, CLOCK).execute(command(MerchantUserStatusAction.SUSPEND))
			}
		}

		test("an invalid transition becomes InvalidMerchantUserTransitionException") {
			val terminated = subAccount(TARGET_ID, MerchantUserRole.ADMIN).apply { terminate(NOW.minusSeconds(60)) }
			val repository = mockk<MerchantUserRepository>()
			every { repository.findById(TARGET_ID) } returns terminated

			shouldThrow<InvalidMerchantUserTransitionException> {
				AdminChangeMerchantUserStatusUseCase(repository, CLOCK).execute(command(MerchantUserStatusAction.REACTIVATE))
			}
		}
	})
