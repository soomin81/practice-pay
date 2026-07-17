package paytech.practice.pay.application.identity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import paytech.practice.pay.application.port.outbound.MerchantRepository
import paytech.practice.pay.application.port.outbound.MerchantUserRepository
import paytech.practice.pay.application.port.outbound.PasswordEncoder
import paytech.practice.pay.domain.identity.AccountStatus
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.MerchantUser
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.identity.MerchantUserRole
import paytech.practice.pay.domain.merchant.Merchant
import paytech.practice.pay.domain.merchant.MerchantCode
import paytech.practice.pay.domain.merchant.MerchantId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private val NOW: Instant = Instant.parse("2026-07-17T00:00:00Z")
private val FIXED_CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
private val MERCHANT_CODE = MerchantCode("test-merchant")
private val MERCHANT_ID = MerchantId("mrc_test_001")
private val LOGIN_ID = LoginId("owner01")
private const val CORRECT_PASSWORD = "correct-horse-battery-staple"
private const val WRONG_PASSWORD = "wrong-password"
private const val STORED_HASH = "hashed:correct-horse-battery-staple"

private fun testMerchant(): Merchant =
	Merchant.create(
		id = MERCHANT_ID,
		code = MERCHANT_CODE,
		name = "테스트 가맹점",
		webhookUrl = null,
		createdAt = NOW.minusSeconds(7_200),
	)

private fun activeUser(): MerchantUser =
	MerchantUser
		.inviteInitialOwner(
			id = MerchantUserId("mu_test_001"),
			merchantId = MERCHANT_ID,
			loginId = LOGIN_ID,
			email = Email("owner@example.com"),
			userName = "테스트 오너",
			invitedByInternalUserId = InternalUserId("iu_test_001"),
			createdAt = NOW.minusSeconds(3_600),
		).apply { activate(STORED_HASH, NOW.minusSeconds(3_600)) }

private fun newUseCase(
	merchantRepository: MerchantRepository,
	merchantUserRepository: MerchantUserRepository,
	passwordEncoder: PasswordEncoder = mockk { every { matches(CORRECT_PASSWORD, STORED_HASH) } returns true },
): AuthenticateMerchantUserUseCase =
	AuthenticateMerchantUserUseCase(
		merchantRepository = merchantRepository,
		merchantUserRepository = merchantUserRepository,
		passwordEncoder = passwordEncoder,
		clock = FIXED_CLOCK,
	)

private fun command(password: String = CORRECT_PASSWORD): AuthenticateMerchantUserCommand =
	AuthenticateMerchantUserCommand(MERCHANT_CODE, LOGIN_ID, password)

class AuthenticateMerchantUserUseCaseTest :
	FunSpec({

		test("correct credentials return the authenticated identity and record success") {
			val merchantRepository = mockk<MerchantRepository> { every { findByCode(MERCHANT_CODE) } returns testMerchant() }
			val merchantUserRepository = mockk<MerchantUserRepository>(relaxed = true)
			every { merchantUserRepository.findByMerchantIdAndLoginId(MERCHANT_ID, LOGIN_ID) } returns activeUser()

			val result = newUseCase(merchantRepository, merchantUserRepository).execute(command())

			result.loginId shouldBe LOGIN_ID
			result.role shouldBe MerchantUserRole.OWNER
			verify(exactly = 1) { merchantUserRepository.save(any()) }
		}

		test("unknown merchantCode throws InvalidCredentialsException without touching MerchantUserRepository") {
			val merchantRepository = mockk<MerchantRepository> { every { findByCode(any()) } returns null }
			val merchantUserRepository = mockk<MerchantUserRepository>()

			shouldThrow<InvalidCredentialsException> {
				newUseCase(merchantRepository, merchantUserRepository).execute(command())
			}

			verify(exactly = 0) { merchantUserRepository.findByMerchantIdAndLoginId(any(), any()) }
		}

		test("unknown loginId within a known merchant throws InvalidCredentialsException") {
			val merchantRepository = mockk<MerchantRepository> { every { findByCode(MERCHANT_CODE) } returns testMerchant() }
			val merchantUserRepository = mockk<MerchantUserRepository> { every { findByMerchantIdAndLoginId(any(), any()) } returns null }

			shouldThrow<InvalidCredentialsException> {
				newUseCase(merchantRepository, merchantUserRepository).execute(command())
			}
		}

		test("wrong password throws InvalidCredentialsException and records the failure") {
			val merchantRepository = mockk<MerchantRepository> { every { findByCode(MERCHANT_CODE) } returns testMerchant() }
			val merchantUserRepository = mockk<MerchantUserRepository>(relaxed = true)
			val user = activeUser()
			every { merchantUserRepository.findByMerchantIdAndLoginId(MERCHANT_ID, LOGIN_ID) } returns user
			val passwordEncoder = mockk<PasswordEncoder> { every { matches(any(), any()) } returns false }

			shouldThrow<InvalidCredentialsException> {
				newUseCase(merchantRepository, merchantUserRepository, passwordEncoder).execute(command(WRONG_PASSWORD))
			}

			user.failedLoginCount shouldBe 1
			verify(exactly = 1) { merchantUserRepository.save(user) }
		}

		test("the 5th consecutive wrong password locks the account") {
			val merchantRepository = mockk<MerchantRepository> { every { findByCode(MERCHANT_CODE) } returns testMerchant() }
			val merchantUserRepository = mockk<MerchantUserRepository>(relaxed = true)
			val user = activeUser()
			repeat(4) { user.recordFailedLogin(NOW.minusSeconds(60)) }
			every { merchantUserRepository.findByMerchantIdAndLoginId(MERCHANT_ID, LOGIN_ID) } returns user
			val passwordEncoder = mockk<PasswordEncoder> { every { matches(any(), any()) } returns false }

			shouldThrow<InvalidCredentialsException> {
				newUseCase(merchantRepository, merchantUserRepository, passwordEncoder).execute(command(WRONG_PASSWORD))
			}

			user.status shouldBe AccountStatus.LOCKED
			user.failedLoginCount shouldBe 5
		}

		test("a still-locked account throws AccountLockedException without checking the password") {
			val merchantRepository = mockk<MerchantRepository> { every { findByCode(MERCHANT_CODE) } returns testMerchant() }
			val merchantUserRepository = mockk<MerchantUserRepository>(relaxed = true)
			val user = activeUser()
			repeat(5) { user.recordFailedLogin(NOW.minusSeconds(120)) }
			user.lock(NOW.plusSeconds(600), NOW.minusSeconds(60))
			every { merchantUserRepository.findByMerchantIdAndLoginId(MERCHANT_ID, LOGIN_ID) } returns user
			val passwordEncoder = mockk<PasswordEncoder>()

			shouldThrow<AccountLockedException> {
				newUseCase(merchantRepository, merchantUserRepository, passwordEncoder).execute(command())
			}

			verify(exactly = 0) { passwordEncoder.matches(any(), any()) }
		}
	})
