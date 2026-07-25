package paytech.practice.pay.application.identity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import paytech.practice.pay.application.port.outbound.InternalUserRepository
import paytech.practice.pay.domain.identity.AccountStatus
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalUser
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.InternalUserRole
import paytech.practice.pay.domain.identity.LoginId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private val NOW: Instant = Instant.parse("2026-07-19T00:00:00Z")
private val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
private val REQUESTER_ID = InternalUserId("iu_requester")
private val TARGET_ID = InternalUserId("iu_target")

private fun superAdmin(id: InternalUserId): InternalUser =
	InternalUser.bootstrap(
		id = id,
		loginId = LoginId("super-${id.value}"),
		email = Email("${id.value}@example.com"),
		userName = "슈퍼 관리자",
		passwordHash = "hashed-password",
		createdAt = NOW.minusSeconds(3_600),
	)

private fun operator(
	id: InternalUserId,
	activated: Boolean = true,
): InternalUser =
	InternalUser
		.invite(
			id = id,
			loginId = LoginId("op-${id.value}"),
			email = Email("${id.value}@example.com"),
			userName = "운영자",
			role = InternalUserRole.OPERATOR,
			createdByInternalUserId = InternalUserId("iu_1"),
			createdAt = NOW.minusSeconds(3_600),
		).apply { if (activated) activate("hashed-password", NOW.minusSeconds(1_800)) }

private fun command(action: InternalUserStatusAction) =
	ChangeInternalUserStatusCommand(
		targetInternalUserId = TARGET_ID,
		action = action,
		requestedByInternalUserId = REQUESTER_ID,
	)

class ChangeInternalUserStatusUseCaseTest :
	FunSpec({

		test("suspends an ACTIVE internal user") {
			val target = operator(TARGET_ID)
			val repository = mockk<InternalUserRepository>()
			every { repository.findById(TARGET_ID) } returns target
			justRun { repository.save(any()) }

			val result = ChangeInternalUserStatusUseCase(repository, CLOCK).execute(command(InternalUserStatusAction.SUSPEND))

			result.status shouldBe AccountStatus.SUSPENDED
			result.changedAt shouldBe NOW
			verify { repository.save(target) }
		}

		test("REACTIVATE and TERMINATE call the matching domain transition") {
			val suspended = operator(TARGET_ID).apply { suspend(NOW.minusSeconds(60)) }
			val repository = mockk<InternalUserRepository>()
			every { repository.findById(TARGET_ID) } returns suspended
			justRun { repository.save(any()) }
			val useCase = ChangeInternalUserStatusUseCase(repository, CLOCK)

			useCase.execute(command(InternalUserStatusAction.REACTIVATE)).status shouldBe AccountStatus.ACTIVE
			useCase.execute(command(InternalUserStatusAction.TERMINATE)).status shouldBe AccountStatus.TERMINATED
		}

		test("the last ACTIVE SUPER_ADMIN cannot be suspended") {
			// docs/architecture/identity-access-api-key.md "3.3": 최소 하나의 활성 SUPER_ADMIN을 유지한다.
			val repository = mockk<InternalUserRepository>()
			every { repository.findById(TARGET_ID) } returns superAdmin(TARGET_ID)
			every { repository.countActiveSuperAdmins() } returns 1

			shouldThrow<LastActiveSuperAdminException> {
				ChangeInternalUserStatusUseCase(repository, CLOCK).execute(command(InternalUserStatusAction.SUSPEND))
			}
		}

		test("a SUPER_ADMIN can be suspended when another ACTIVE SUPER_ADMIN remains") {
			val repository = mockk<InternalUserRepository>()
			every { repository.findById(TARGET_ID) } returns superAdmin(TARGET_ID)
			every { repository.countActiveSuperAdmins() } returns 2
			justRun { repository.save(any()) }

			val result = ChangeInternalUserStatusUseCase(repository, CLOCK).execute(command(InternalUserStatusAction.SUSPEND))

			result.status shouldBe AccountStatus.SUSPENDED
		}

		test("REACTIVATE never consults the last-super-admin invariant (it does not reduce active SUPER_ADMINs)") {
			val repository = mockk<InternalUserRepository>()
			every { repository.findById(TARGET_ID) } returns superAdmin(TARGET_ID).apply { suspend(NOW.minusSeconds(60)) }
			justRun { repository.save(any()) }

			ChangeInternalUserStatusUseCase(repository, CLOCK).execute(command(InternalUserStatusAction.REACTIVATE))

			verify(exactly = 0) { repository.countActiveSuperAdmins() }
		}

		test("targeting yourself is rejected") {
			val repository = mockk<InternalUserRepository>()

			shouldThrow<InternalUserNotManageableException> {
				ChangeInternalUserStatusUseCase(repository, CLOCK).execute(
					command(InternalUserStatusAction.SUSPEND).copy(targetInternalUserId = REQUESTER_ID),
				)
			}
		}

		test("an unknown target is reported as not found") {
			val repository = mockk<InternalUserRepository>()
			every { repository.findById(TARGET_ID) } returns null

			shouldThrow<InternalUserNotFoundException> {
				ChangeInternalUserStatusUseCase(repository, CLOCK).execute(command(InternalUserStatusAction.SUSPEND))
			}
		}

		test("an invalid transition becomes InvalidInternalUserTransitionException, not a raw IllegalStateException") {
			// 이미 종료된 계정을 재개하려는 시도 — 애그리게이트가 막고, Use Case가 전용 예외로 바꾼다
			// (그래야 inbound Adapter가 checkNotNull 류의 500까지 409로 가리지 않는다).
			val terminated = operator(TARGET_ID).apply { terminate(NOW.minusSeconds(60)) }
			val repository = mockk<InternalUserRepository>()
			every { repository.findById(TARGET_ID) } returns terminated

			shouldThrow<InvalidInternalUserTransitionException> {
				ChangeInternalUserStatusUseCase(repository, CLOCK).execute(command(InternalUserStatusAction.REACTIVATE))
			}
		}
	})
