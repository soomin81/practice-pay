package paytech.practice.pay.application.identity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import paytech.practice.pay.application.port.outbound.InternalLoginAuditRepository
import paytech.practice.pay.application.port.outbound.InternalUserRepository
import paytech.practice.pay.application.port.outbound.PasswordEncoder
import paytech.practice.pay.domain.identity.AccountStatus
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalLoginAudit
import paytech.practice.pay.domain.identity.InternalUser
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.InternalUserRole
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.LoginOutcome
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private val NOW: Instant = Instant.parse("2026-07-17T00:00:00Z")
private val FIXED_CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
private val LOGIN_ID = LoginId("admin01")
private const val CORRECT_PASSWORD = "correct-horse-battery-staple"
private const val WRONG_PASSWORD = "wrong-password"
private const val STORED_HASH = "hashed:correct-horse-battery-staple"

private fun activeUser(): InternalUser =
	InternalUser.bootstrap(
		id = InternalUserId("iu_test_001"),
		loginId = LOGIN_ID,
		email = Email("admin@example.com"),
		userName = "테스트 관리자",
		passwordHash = STORED_HASH,
		createdAt = NOW.minusSeconds(3_600),
	)

private fun newUseCase(
	internalUserRepository: InternalUserRepository,
	passwordEncoder: PasswordEncoder = mockk { every { matches(CORRECT_PASSWORD, STORED_HASH) } returns true },
	auditRepository: InternalLoginAuditRepository = mockk(relaxed = true),
): AuthenticateInternalUserUseCase =
	AuthenticateInternalUserUseCase(
		internalUserRepository = internalUserRepository,
		passwordEncoder = passwordEncoder,
		internalLoginAuditRepository = auditRepository,
		idGenerator = mockk { every { newId() } returns "audit-token" },
		clock = FIXED_CLOCK,
	)

class AuthenticateInternalUserUseCaseTest :
	FunSpec({

		test("correct credentials return the authenticated identity and record success") {
			val repository = mockk<InternalUserRepository>(relaxed = true)
			every { repository.findByLoginId(LOGIN_ID) } returns activeUser()

			val result = newUseCase(repository).execute(AuthenticateInternalUserCommand(LOGIN_ID, CORRECT_PASSWORD))

			result.loginId shouldBe LOGIN_ID
			result.role shouldBe InternalUserRole.SUPER_ADMIN
			verify(exactly = 1) { repository.save(any()) }
		}

		test("unknown loginId throws InvalidCredentialsException") {
			val repository = mockk<InternalUserRepository>()
			every { repository.findByLoginId(any()) } returns null

			shouldThrow<InvalidCredentialsException> {
				newUseCase(repository).execute(AuthenticateInternalUserCommand(LOGIN_ID, CORRECT_PASSWORD))
			}
		}

		test("wrong password throws InvalidCredentialsException and records the failure") {
			val repository = mockk<InternalUserRepository>(relaxed = true)
			val user = activeUser()
			every { repository.findByLoginId(LOGIN_ID) } returns user
			val passwordEncoder = mockk<PasswordEncoder> { every { matches(any(), any()) } returns false }

			shouldThrow<InvalidCredentialsException> {
				newUseCase(repository, passwordEncoder).execute(AuthenticateInternalUserCommand(LOGIN_ID, WRONG_PASSWORD))
			}

			user.failedLoginCount shouldBe 1
			verify(exactly = 1) { repository.save(user) }
		}

		test("the 5th consecutive wrong password locks the account") {
			val repository = mockk<InternalUserRepository>(relaxed = true)
			val user = activeUser()
			repeat(4) { user.recordFailedLogin(NOW.minusSeconds(60)) }
			every { repository.findByLoginId(LOGIN_ID) } returns user
			val passwordEncoder = mockk<PasswordEncoder> { every { matches(any(), any()) } returns false }

			shouldThrow<InvalidCredentialsException> {
				newUseCase(repository, passwordEncoder).execute(AuthenticateInternalUserCommand(LOGIN_ID, WRONG_PASSWORD))
			}

			user.status shouldBe AccountStatus.LOCKED
			user.failedLoginCount shouldBe 5
		}

		test("a still-locked account throws AccountLockedException without checking the password") {
			val repository = mockk<InternalUserRepository>(relaxed = true)
			val user = activeUser()
			repeat(5) { user.recordFailedLogin(NOW.minusSeconds(120)) }
			user.lock(NOW.plusSeconds(600), NOW.minusSeconds(60))
			every { repository.findByLoginId(LOGIN_ID) } returns user
			val passwordEncoder = mockk<PasswordEncoder>()

			shouldThrow<AccountLockedException> {
				newUseCase(repository, passwordEncoder).execute(AuthenticateInternalUserCommand(LOGIN_ID, CORRECT_PASSWORD))
			}

			verify(exactly = 0) { passwordEncoder.matches(any(), any()) }
		}

		test("an expired lock unlocks and, with the correct password, succeeds") {
			val repository = mockk<InternalUserRepository>(relaxed = true)
			val user = activeUser()
			repeat(5) { user.recordFailedLogin(NOW.minusSeconds(1_200)) }
			user.lock(NOW.minusSeconds(60), NOW.minusSeconds(600))
			every { repository.findByLoginId(LOGIN_ID) } returns user

			val result = newUseCase(repository).execute(AuthenticateInternalUserCommand(LOGIN_ID, CORRECT_PASSWORD))

			result.loginId shouldBe LOGIN_ID
			user.status shouldBe AccountStatus.ACTIVE
		}

		test("an INVITED account throws InvalidCredentialsException") {
			val repository = mockk<InternalUserRepository>()
			val invitedUser =
				InternalUser.invite(
					id = InternalUserId("iu_test_002"),
					loginId = LOGIN_ID,
					email = Email("invited@example.com"),
					userName = "초대된 관리자",
					role = InternalUserRole.OPERATOR,
					createdByInternalUserId = InternalUserId("iu_test_001"),
					createdAt = NOW,
				)
			every { repository.findByLoginId(LOGIN_ID) } returns invitedUser

			shouldThrow<InvalidCredentialsException> {
				newUseCase(repository).execute(AuthenticateInternalUserCommand(LOGIN_ID, CORRECT_PASSWORD))
			}
		}

		test("a successful login records a SUCCESS audit entry with the client IP") {
			val repository = mockk<InternalUserRepository>(relaxed = true)
			every { repository.findByLoginId(LOGIN_ID) } returns activeUser()
			val auditRepository = mockk<InternalLoginAuditRepository>(relaxed = true)
			val captured = slot<InternalLoginAudit>()

			newUseCase(repository, auditRepository = auditRepository)
				.execute(AuthenticateInternalUserCommand(LOGIN_ID, CORRECT_PASSWORD, clientIp = "203.0.113.7"))

			verify(exactly = 1) { auditRepository.append(capture(captured)) }
			captured.captured.outcome shouldBe LoginOutcome.SUCCESS
			captured.captured.internalUserId shouldBe InternalUserId("iu_test_001")
			captured.captured.clientIp shouldBe "203.0.113.7"
		}

		test("an unknown loginId records an INVALID_CREDENTIALS audit entry with a null user") {
			val repository = mockk<InternalUserRepository>()
			every { repository.findByLoginId(any()) } returns null
			val auditRepository = mockk<InternalLoginAuditRepository>(relaxed = true)
			val captured = slot<InternalLoginAudit>()

			shouldThrow<InvalidCredentialsException> {
				newUseCase(repository, auditRepository = auditRepository)
					.execute(AuthenticateInternalUserCommand(LOGIN_ID, CORRECT_PASSWORD))
			}

			verify(exactly = 1) { auditRepository.append(capture(captured)) }
			captured.captured.outcome shouldBe LoginOutcome.INVALID_CREDENTIALS
			captured.captured.internalUserId shouldBe null
			captured.captured.attemptedLoginId shouldBe LOGIN_ID
		}

		test("a still-locked account records a LOCKED audit entry") {
			val repository = mockk<InternalUserRepository>(relaxed = true)
			val user = activeUser()
			repeat(5) { user.recordFailedLogin(NOW.minusSeconds(120)) }
			user.lock(NOW.plusSeconds(600), NOW.minusSeconds(60))
			every { repository.findByLoginId(LOGIN_ID) } returns user
			val auditRepository = mockk<InternalLoginAuditRepository>(relaxed = true)
			val captured = slot<InternalLoginAudit>()

			shouldThrow<AccountLockedException> {
				newUseCase(repository, mockk(), auditRepository).execute(AuthenticateInternalUserCommand(LOGIN_ID, CORRECT_PASSWORD))
			}

			verify(exactly = 1) { auditRepository.append(capture(captured)) }
			captured.captured.outcome shouldBe LoginOutcome.LOCKED
		}
	})
