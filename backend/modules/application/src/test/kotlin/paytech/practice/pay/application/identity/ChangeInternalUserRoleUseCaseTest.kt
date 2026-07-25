package paytech.practice.pay.application.identity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import paytech.practice.pay.application.port.outbound.InternalUserRepository
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

private fun operator(id: InternalUserId): InternalUser =
	InternalUser
		.invite(
			id = id,
			loginId = LoginId("op-${id.value}"),
			email = Email("${id.value}@example.com"),
			userName = "운영자",
			role = InternalUserRole.OPERATOR,
			createdByInternalUserId = InternalUserId("iu_1"),
			createdAt = NOW.minusSeconds(3_600),
		).apply { activate("hashed-password", NOW.minusSeconds(1_800)) }

private fun command(newRole: InternalUserRole) =
	ChangeInternalUserRoleCommand(
		targetInternalUserId = TARGET_ID,
		newRole = newRole,
		requestedByInternalUserId = REQUESTER_ID,
	)

class ChangeInternalUserRoleUseCaseTest :
	FunSpec({

		test("changes an internal user role") {
			val target = operator(TARGET_ID)
			val repository = mockk<InternalUserRepository>()
			every { repository.findById(TARGET_ID) } returns target
			justRun { repository.save(any()) }

			val result = ChangeInternalUserRoleUseCase(repository, CLOCK).execute(command(InternalUserRole.VIEWER))

			result.role shouldBe InternalUserRole.VIEWER
			result.changedAt shouldBe NOW
			verify { repository.save(target) }
		}

		test("promoting to SUPER_ADMIN is rejected by the domain (IllegalArgumentException, mapped to 400)") {
			// 초대와 같은 제약 — Use Case는 IllegalStateException만 감싸므로 이 예외는 그대로 흘러 400이 된다.
			val repository = mockk<InternalUserRepository>()
			every { repository.findById(TARGET_ID) } returns operator(TARGET_ID)
			justRun { repository.save(any()) }

			shouldThrow<IllegalArgumentException> {
				ChangeInternalUserRoleUseCase(repository, CLOCK).execute(command(InternalUserRole.SUPER_ADMIN))
			}
		}

		test("the last ACTIVE SUPER_ADMIN cannot be demoted") {
			// 강등은 활성 SUPER_ADMIN 집합에서 빼는 연산이라 정지·종료와 같은 불변식이 걸린다.
			val repository = mockk<InternalUserRepository>()
			every { repository.findById(TARGET_ID) } returns superAdmin(TARGET_ID)
			every { repository.countActiveSuperAdmins() } returns 1

			shouldThrow<LastActiveSuperAdminException> {
				ChangeInternalUserRoleUseCase(repository, CLOCK).execute(command(InternalUserRole.OPERATOR))
			}
		}

		test("a SUPER_ADMIN can be demoted when another ACTIVE SUPER_ADMIN remains") {
			val repository = mockk<InternalUserRepository>()
			every { repository.findById(TARGET_ID) } returns superAdmin(TARGET_ID)
			every { repository.countActiveSuperAdmins() } returns 2
			justRun { repository.save(any()) }

			val result = ChangeInternalUserRoleUseCase(repository, CLOCK).execute(command(InternalUserRole.OPERATOR))

			result.role shouldBe InternalUserRole.OPERATOR
		}

		test("targeting yourself is rejected") {
			val repository = mockk<InternalUserRepository>()

			shouldThrow<InternalUserNotManageableException> {
				ChangeInternalUserRoleUseCase(repository, CLOCK).execute(
					command(InternalUserRole.VIEWER).copy(targetInternalUserId = REQUESTER_ID),
				)
			}
		}

		test("an unknown target is reported as not found") {
			val repository = mockk<InternalUserRepository>()
			every { repository.findById(TARGET_ID) } returns null

			shouldThrow<InternalUserNotFoundException> {
				ChangeInternalUserRoleUseCase(repository, CLOCK).execute(command(InternalUserRole.VIEWER))
			}
		}

		test("changing the role of a TERMINATED account becomes InvalidInternalUserTransitionException") {
			val terminated = operator(TARGET_ID).apply { terminate(NOW.minusSeconds(60)) }
			val repository = mockk<InternalUserRepository>()
			every { repository.findById(TARGET_ID) } returns terminated

			shouldThrow<InvalidInternalUserTransitionException> {
				ChangeInternalUserRoleUseCase(repository, CLOCK).execute(command(InternalUserRole.VIEWER))
			}
		}
	})
