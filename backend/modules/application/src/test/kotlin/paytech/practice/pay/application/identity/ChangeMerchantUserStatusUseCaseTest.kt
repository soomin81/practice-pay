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
private val REQUESTER_ID = MerchantUserId("mu_requester")
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
	activated: Boolean = true,
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
		).apply { if (activated) activate("hashed-password", NOW.minusSeconds(1_800)) }

private fun command(action: MerchantUserStatusAction) =
	ChangeMerchantUserStatusCommand(
		targetMerchantUserId = TARGET_ID,
		action = action,
		requestedByMerchantUserId = REQUESTER_ID,
	)

class ChangeMerchantUserStatusUseCaseTest :
	FunSpec({

		test("an OWNER suspends an ACTIVE sub-account") {
			val target = subAccount(TARGET_ID, MerchantUserRole.ADMIN)
			val repository = mockk<MerchantUserRepository>()
			every { repository.findById(REQUESTER_ID) } returns owner(REQUESTER_ID)
			every { repository.findById(TARGET_ID) } returns target
			justRun { repository.save(any()) }

			val result = ChangeMerchantUserStatusUseCase(repository, CLOCK).execute(command(MerchantUserStatusAction.SUSPEND))

			result.status shouldBe AccountStatus.SUSPENDED
			result.changedAt shouldBe NOW
			verify { repository.save(target) }
		}

		test("REACTIVATE and TERMINATE call the matching domain transition") {
			val suspended = subAccount(TARGET_ID, MerchantUserRole.ADMIN).apply { suspend(NOW.minusSeconds(60)) }
			val repository = mockk<MerchantUserRepository>()
			every { repository.findById(REQUESTER_ID) } returns owner(REQUESTER_ID)
			every { repository.findById(TARGET_ID) } returns suspended
			every { repository.countActiveOwners(any()) } returns 1
			justRun { repository.save(any()) }
			val useCase = ChangeMerchantUserStatusUseCase(repository, CLOCK)

			useCase.execute(command(MerchantUserStatusAction.REACTIVATE)).status shouldBe AccountStatus.ACTIVE
			useCase.execute(command(MerchantUserStatusAction.TERMINATE)).status shouldBe AccountStatus.TERMINATED
		}

		test("the last ACTIVE OWNER cannot be suspended") {
			// docs/domain/domain-model.md: "최소 하나의 활성 OWNER를 유지한다"
			val repository = mockk<MerchantUserRepository>()
			every { repository.findById(REQUESTER_ID) } returns owner(REQUESTER_ID)
			every { repository.findById(TARGET_ID) } returns owner(TARGET_ID)
			every { repository.countActiveOwners(MERCHANT_ID) } returns 1

			shouldThrow<LastActiveOwnerException> {
				ChangeMerchantUserStatusUseCase(repository, CLOCK).execute(command(MerchantUserStatusAction.SUSPEND))
			}
		}

		test("an OWNER can be suspended when another ACTIVE OWNER remains") {
			val repository = mockk<MerchantUserRepository>()
			every { repository.findById(REQUESTER_ID) } returns owner(REQUESTER_ID)
			every { repository.findById(TARGET_ID) } returns owner(TARGET_ID)
			every { repository.countActiveOwners(MERCHANT_ID) } returns 2
			justRun { repository.save(any()) }

			val result = ChangeMerchantUserStatusUseCase(repository, CLOCK).execute(command(MerchantUserStatusAction.SUSPEND))

			result.status shouldBe AccountStatus.SUSPENDED
		}

		test("REACTIVATE never consults the last-owner invariant (it does not reduce active owners)") {
			val repository = mockk<MerchantUserRepository>()
			every { repository.findById(REQUESTER_ID) } returns owner(REQUESTER_ID)
			every { repository.findById(TARGET_ID) } returns owner(TARGET_ID).apply { suspend(NOW.minusSeconds(60)) }
			justRun { repository.save(any()) }

			ChangeMerchantUserStatusUseCase(repository, CLOCK).execute(command(MerchantUserStatusAction.REACTIVATE))

			verify(exactly = 0) { repository.countActiveOwners(any()) }
		}

		test("targeting yourself is rejected") {
			val repository = mockk<MerchantUserRepository>()
			every { repository.findById(REQUESTER_ID) } returns owner(REQUESTER_ID)

			shouldThrow<MerchantUserNotManageableException> {
				ChangeMerchantUserStatusUseCase(repository, CLOCK).execute(
					command(MerchantUserStatusAction.SUSPEND).copy(targetMerchantUserId = REQUESTER_ID),
				)
			}
		}

		test("an ADMIN cannot manage an OWNER") {
			val repository = mockk<MerchantUserRepository>()
			every { repository.findById(REQUESTER_ID) } returns subAccount(REQUESTER_ID, MerchantUserRole.ADMIN)
			every { repository.findById(TARGET_ID) } returns owner(TARGET_ID)

			shouldThrow<MerchantUserNotManageableException> {
				ChangeMerchantUserStatusUseCase(repository, CLOCK).execute(command(MerchantUserStatusAction.SUSPEND))
			}
		}

		test("a target in another merchant is reported as not found, never as forbidden") {
			// 남의 가맹점 사용자의 존재 여부를 응답으로 알려주지 않는다.
			val repository = mockk<MerchantUserRepository>()
			every { repository.findById(REQUESTER_ID) } returns owner(REQUESTER_ID)
			every { repository.findById(TARGET_ID) } returns
				subAccount(TARGET_ID, MerchantUserRole.ADMIN, merchantId = OTHER_MERCHANT_ID)

			shouldThrow<MerchantUserNotFoundException> {
				ChangeMerchantUserStatusUseCase(repository, CLOCK).execute(command(MerchantUserStatusAction.SUSPEND))
			}
		}

		test("a VIEWER requester cannot manage anyone") {
			val repository = mockk<MerchantUserRepository>()
			every { repository.findById(REQUESTER_ID) } returns subAccount(REQUESTER_ID, MerchantUserRole.VIEWER)

			shouldThrow<MerchantUserCannotInviteSubAccountsException> {
				ChangeMerchantUserStatusUseCase(repository, CLOCK).execute(command(MerchantUserStatusAction.SUSPEND))
			}
		}

		test("an invalid transition becomes InvalidMerchantUserTransitionException, not a raw IllegalStateException") {
			// 이미 종료된 계정을 재개하려는 시도 — 애그리게이트가 막고, Use Case가 전용 예외로 바꾼다
			// (그래야 inbound Adapter가 checkNotNull 류의 500까지 409로 가리지 않는다).
			val terminated = subAccount(TARGET_ID, MerchantUserRole.ADMIN).apply { terminate(NOW.minusSeconds(60)) }
			val repository = mockk<MerchantUserRepository>()
			every { repository.findById(REQUESTER_ID) } returns owner(REQUESTER_ID)
			every { repository.findById(TARGET_ID) } returns terminated

			shouldThrow<InvalidMerchantUserTransitionException> {
				ChangeMerchantUserStatusUseCase(repository, CLOCK).execute(command(MerchantUserStatusAction.REACTIVATE))
			}
		}
	})
