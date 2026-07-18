package paytech.practice.pay.application.identity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import paytech.practice.pay.application.port.outbound.AccountInvitationRepository
import paytech.practice.pay.application.port.outbound.InternalUserRepository
import paytech.practice.pay.application.port.outbound.InvitationTokenHasher
import paytech.practice.pay.application.port.outbound.MerchantUserRepository
import paytech.practice.pay.application.port.outbound.PasswordEncoder
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.domain.identity.AccountInvitation
import paytech.practice.pay.domain.identity.AccountInvitationId
import paytech.practice.pay.domain.identity.AccountInvitationStatus
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalUser
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.InternalUserRole
import paytech.practice.pay.domain.identity.InvitationAccountType
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.MerchantUser
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.merchant.MerchantId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private val NOW: Instant = Instant.parse("2026-07-17T00:00:00Z")
private val FIXED_CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
private const val RAW_TOKEN = "raw-invitation-token"
private const val TOKEN_HASH = "hashed:$RAW_TOKEN"

private fun newInternalUserInvitation(): AccountInvitation =
	AccountInvitation.forInternalUser(
		id = AccountInvitationId("ai_test_001"),
		internalUserId = InternalUserId("iu_test_001"),
		tokenHash = TOKEN_HASH,
		expiresAt = NOW.plusSeconds(3_600),
		createdAt = NOW.minusSeconds(60),
	)

private fun newMerchantUserInvitation(): AccountInvitation =
	AccountInvitation.forMerchantUser(
		id = AccountInvitationId("ai_test_002"),
		merchantUserId = MerchantUserId("mu_test_001"),
		tokenHash = TOKEN_HASH,
		expiresAt = NOW.plusSeconds(3_600),
		createdAt = NOW.minusSeconds(60),
	)

private fun newInvitedInternalUser(): InternalUser =
	InternalUser.invite(
		id = InternalUserId("iu_test_001"),
		loginId = LoginId("new-operator"),
		email = Email("new-operator@example.com"),
		userName = "새 운영자",
		role = InternalUserRole.OPERATOR,
		createdByInternalUserId = InternalUserId("iu_super_admin"),
		createdAt = NOW.minusSeconds(60),
	)

private fun newInvitedMerchantUser(): MerchantUser =
	MerchantUser.inviteInitialOwner(
		id = MerchantUserId("mu_test_001"),
		merchantId = MerchantId("mrc_test_001"),
		loginId = LoginId("new-owner"),
		email = Email("new-owner@example.com"),
		userName = "새 오너",
		invitedByInternalUserId = InternalUserId("iu_super_admin"),
		createdAt = NOW.minusSeconds(60),
	)

private class AcceptInvitationTransactionManager : TransactionManager {
	override fun <T> runInTransaction(block: () -> T): T = block()
}

private fun newUseCase(
	accountInvitationRepository: AccountInvitationRepository,
	internalUserRepository: InternalUserRepository = mockk(relaxed = true),
	merchantUserRepository: MerchantUserRepository = mockk(relaxed = true),
	passwordEncoder: PasswordEncoder = mockk { every { encode(any()) } returns "encoded-password" },
	invitationTokenHasher: InvitationTokenHasher = mockk { every { hash(RAW_TOKEN) } returns TOKEN_HASH },
): AcceptAccountInvitationUseCase =
	AcceptAccountInvitationUseCase(
		accountInvitationRepository = accountInvitationRepository,
		internalUserRepository = internalUserRepository,
		merchantUserRepository = merchantUserRepository,
		invitationTokenHasher = invitationTokenHasher,
		passwordEncoder = passwordEncoder,
		transactionManager = AcceptInvitationTransactionManager(),
		clock = FIXED_CLOCK,
	)

private fun newCommand(expectedAccountType: InvitationAccountType) =
	AcceptAccountInvitationCommand(
		invitationToken = RAW_TOKEN,
		newPassword = "new-password-123",
		expectedAccountType = expectedAccountType,
	)

class AcceptAccountInvitationUseCaseTest :
	FunSpec({

		test("activates an INVITED InternalUser and accepts the invitation") {
			val accountInvitationRepository = mockk<AccountInvitationRepository>(relaxed = true)
			val internalUserRepository = mockk<InternalUserRepository>(relaxed = true)
			every { accountInvitationRepository.findByTokenHash(TOKEN_HASH) } returns newInternalUserInvitation()
			every { internalUserRepository.findById(InternalUserId("iu_test_001")) } returns newInvitedInternalUser()
			val savedUsers = mutableListOf<InternalUser>()
			val savedInvitations = mutableListOf<AccountInvitation>()
			every { internalUserRepository.save(capture(savedUsers)) } returns Unit
			every { accountInvitationRepository.save(capture(savedInvitations)) } returns Unit

			val result =
				newUseCase(accountInvitationRepository, internalUserRepository = internalUserRepository)
					.execute(newCommand(InvitationAccountType.INTERNAL_USER))

			result.loginId shouldBe LoginId("new-operator")
			result.activatedAt shouldBe NOW
			savedUsers.single().status.name shouldBe "ACTIVE"
			savedUsers.single().passwordHash shouldBe "encoded-password"
			savedInvitations.single().status shouldBe AccountInvitationStatus.ACCEPTED
		}

		test("activates an INVITED MerchantUser and accepts the invitation") {
			val accountInvitationRepository = mockk<AccountInvitationRepository>(relaxed = true)
			val merchantUserRepository = mockk<MerchantUserRepository>(relaxed = true)
			every { accountInvitationRepository.findByTokenHash(TOKEN_HASH) } returns newMerchantUserInvitation()
			every { merchantUserRepository.findById(MerchantUserId("mu_test_001")) } returns newInvitedMerchantUser()
			val savedUsers = mutableListOf<MerchantUser>()
			every { merchantUserRepository.save(capture(savedUsers)) } returns Unit

			val result =
				newUseCase(accountInvitationRepository, merchantUserRepository = merchantUserRepository)
					.execute(newCommand(InvitationAccountType.MERCHANT_USER))

			result.loginId shouldBe LoginId("new-owner")
			savedUsers.single().status.name shouldBe "ACTIVE"
			verify(exactly = 1) { accountInvitationRepository.save(any()) }
		}

		test("throws InvalidInvitationException when the token does not match any invitation") {
			val accountInvitationRepository = mockk<AccountInvitationRepository>()
			every { accountInvitationRepository.findByTokenHash(TOKEN_HASH) } returns null

			shouldThrow<InvalidInvitationException> {
				newUseCase(accountInvitationRepository).execute(newCommand(InvitationAccountType.INTERNAL_USER))
			}
		}

		test("throws InvalidInvitationException when the accountType does not match what the caller expected") {
			val accountInvitationRepository = mockk<AccountInvitationRepository>()
			every { accountInvitationRepository.findByTokenHash(TOKEN_HASH) } returns newInternalUserInvitation()

			shouldThrow<InvalidInvitationException> {
				newUseCase(accountInvitationRepository).execute(newCommand(InvitationAccountType.MERCHANT_USER))
			}
		}

		test("throws InvalidInvitationException when the invitation is already ACCEPTED") {
			val accountInvitationRepository = mockk<AccountInvitationRepository>()
			val acceptedInvitation = newInternalUserInvitation().apply { accept(NOW.minusSeconds(10)) }
			every { accountInvitationRepository.findByTokenHash(TOKEN_HASH) } returns acceptedInvitation

			shouldThrow<InvalidInvitationException> {
				newUseCase(accountInvitationRepository).execute(newCommand(InvitationAccountType.INTERNAL_USER))
			}
		}

		test("throws InvalidInvitationException when the invitation has expired") {
			val accountInvitationRepository = mockk<AccountInvitationRepository>()
			val expiredInvitation =
				AccountInvitation.forInternalUser(
					id = AccountInvitationId("ai_test_003"),
					internalUserId = InternalUserId("iu_test_001"),
					tokenHash = TOKEN_HASH,
					expiresAt = NOW.minusSeconds(1),
					createdAt = NOW.minusSeconds(3_600),
				)
			every { accountInvitationRepository.findByTokenHash(TOKEN_HASH) } returns expiredInvitation

			shouldThrow<InvalidInvitationException> {
				newUseCase(accountInvitationRepository).execute(newCommand(InvitationAccountType.INTERNAL_USER))
			}
		}
	})
