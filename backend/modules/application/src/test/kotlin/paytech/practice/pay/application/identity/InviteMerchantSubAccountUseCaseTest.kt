package paytech.practice.pay.application.identity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import paytech.practice.pay.application.port.outbound.AccountInvitationRepository
import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.InvitationTokenHasher
import paytech.practice.pay.application.port.outbound.MerchantUserRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.domain.identity.AccountInvitation
import paytech.practice.pay.domain.identity.AccountInvitationStatus
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
private val FIXED_CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
private val MERCHANT_ID = MerchantId("mrc_test_001")
private val INVITER_ID = MerchantUserId("mu_inviter")
private val NEW_LOGIN_ID = LoginId("new-admin")
private val NEW_EMAIL = Email("new-admin@example.com")

private fun newCommand(role: MerchantUserRole = MerchantUserRole.ADMIN): InviteMerchantSubAccountCommand =
	InviteMerchantSubAccountCommand(
		loginId = NEW_LOGIN_ID,
		email = NEW_EMAIL,
		userName = "새 하위 계정",
		role = role,
		invitedByMerchantUserId = INVITER_ID,
	)

private fun activeOwner(): MerchantUser =
	MerchantUser
		.inviteInitialOwner(
			id = INVITER_ID,
			merchantId = MERCHANT_ID,
			loginId = LoginId("owner-login"),
			email = Email("owner@example.com"),
			userName = "가맹점 대표",
			invitedByInternalUserId = InternalUserId("iu_registrar"),
			createdAt = NOW.minusSeconds(3_600),
		).apply { activate("hashed-password", NOW.minusSeconds(1_800)) }

private class InviteImmediateTransactionManager : TransactionManager {
	override fun <T> runInTransaction(block: () -> T): T = block()
}

private class InviteFakeIdGenerator : IdGenerator {
	private var counter = 0

	override fun newId(): String {
		counter += 1
		return "id$counter"
	}
}

private fun newUseCase(
	merchantUserRepository: MerchantUserRepository,
	accountInvitationRepository: AccountInvitationRepository,
	invitationTokenHasher: InvitationTokenHasher = mockk { every { hash(any()) } answers { "hashed:${firstArg<String>()}" } },
): InviteMerchantSubAccountUseCase =
	InviteMerchantSubAccountUseCase(
		merchantUserRepository = merchantUserRepository,
		accountInvitationRepository = accountInvitationRepository,
		invitationTokenHasher = invitationTokenHasher,
		idGenerator = InviteFakeIdGenerator(),
		transactionManager = InviteImmediateTransactionManager(),
		clock = FIXED_CLOCK,
	)

class InviteMerchantSubAccountUseCaseTest :
	FunSpec({

		test("an ACTIVE OWNER invites an INVITED ADMIN sub-account under the same merchant") {
			val merchantUserRepository = mockk<MerchantUserRepository>(relaxed = true)
			val accountInvitationRepository = mockk<AccountInvitationRepository>(relaxed = true)
			every { merchantUserRepository.findById(INVITER_ID) } returns activeOwner()
			every { merchantUserRepository.findByMerchantIdAndLoginId(MERCHANT_ID, NEW_LOGIN_ID) } returns null
			every { merchantUserRepository.findByMerchantIdAndEmail(MERCHANT_ID, NEW_EMAIL) } returns null

			val savedSubAccounts = mutableListOf<MerchantUser>()
			val savedInvitations = mutableListOf<AccountInvitation>()
			every { merchantUserRepository.save(capture(savedSubAccounts)) } returns Unit
			every { accountInvitationRepository.save(capture(savedInvitations)) } returns Unit

			val result = newUseCase(merchantUserRepository, accountInvitationRepository).execute(newCommand())

			result.loginId shouldBe NEW_LOGIN_ID
			result.role shouldBe MerchantUserRole.ADMIN
			result.invitationToken shouldNotBe "hashed:${result.invitationToken}"

			verify(exactly = 1) { merchantUserRepository.save(any()) }
			verify(exactly = 1) { accountInvitationRepository.save(any()) }

			val savedSubAccount = savedSubAccounts.single()
			savedSubAccount.status.name shouldBe "INVITED"
			savedSubAccount.merchantId shouldBe MERCHANT_ID
			savedSubAccount.invitedByMerchantUserId shouldBe INVITER_ID

			val savedInvitation = savedInvitations.single()
			savedInvitation.status shouldBe AccountInvitationStatus.PENDING
			savedInvitation.merchantUserId shouldBe savedSubAccount.id
		}

		test("an ACTIVE ADMIN can also invite sub-accounts") {
			val admin =
				MerchantUser
					.inviteSubAccount(
						id = INVITER_ID,
						merchantId = MERCHANT_ID,
						loginId = LoginId("admin-login"),
						email = Email("admin@example.com"),
						userName = "관리자",
						role = MerchantUserRole.ADMIN,
						invitedByMerchantUserId = MerchantUserId("mu_owner"),
						createdAt = NOW.minusSeconds(3_600),
					).apply { activate("hashed-password", NOW.minusSeconds(1_800)) }

			val merchantUserRepository = mockk<MerchantUserRepository>(relaxed = true)
			val accountInvitationRepository = mockk<AccountInvitationRepository>(relaxed = true)
			every { merchantUserRepository.findById(INVITER_ID) } returns admin
			every { merchantUserRepository.findByMerchantIdAndLoginId(MERCHANT_ID, NEW_LOGIN_ID) } returns null
			every { merchantUserRepository.findByMerchantIdAndEmail(MERCHANT_ID, NEW_EMAIL) } returns null

			val result =
				newUseCase(merchantUserRepository, accountInvitationRepository)
					.execute(newCommand(role = MerchantUserRole.VIEWER))

			result.role shouldBe MerchantUserRole.VIEWER
		}

		test("a VIEWER inviter throws MerchantUserCannotInviteSubAccountsException") {
			val viewer =
				MerchantUser
					.inviteSubAccount(
						id = INVITER_ID,
						merchantId = MERCHANT_ID,
						loginId = LoginId("viewer-login"),
						email = Email("viewer@example.com"),
						userName = "뷰어",
						role = MerchantUserRole.VIEWER,
						invitedByMerchantUserId = MerchantUserId("mu_owner"),
						createdAt = NOW.minusSeconds(3_600),
					).apply { activate("hashed-password", NOW.minusSeconds(1_800)) }

			val merchantUserRepository = mockk<MerchantUserRepository>()
			every { merchantUserRepository.findById(INVITER_ID) } returns viewer

			shouldThrow<MerchantUserCannotInviteSubAccountsException> {
				newUseCase(merchantUserRepository, mockk()).execute(newCommand())
			}
		}

		test("a SUSPENDED OWNER cannot invite even though the role would allow it") {
			val suspendedOwner = activeOwner().apply { suspend(NOW.minusSeconds(600)) }

			val merchantUserRepository = mockk<MerchantUserRepository>()
			every { merchantUserRepository.findById(INVITER_ID) } returns suspendedOwner

			shouldThrow<MerchantUserCannotInviteSubAccountsException> {
				newUseCase(merchantUserRepository, mockk()).execute(newCommand())
			}
		}

		test("duplicate loginId within the same merchant throws DuplicateMerchantUserException") {
			val merchantUserRepository = mockk<MerchantUserRepository>()
			every { merchantUserRepository.findById(INVITER_ID) } returns activeOwner()
			every { merchantUserRepository.findByMerchantIdAndLoginId(MERCHANT_ID, NEW_LOGIN_ID) } returns
				MerchantUser.inviteInitialOwner(
					id = MerchantUserId("mu_existing"),
					merchantId = MERCHANT_ID,
					loginId = NEW_LOGIN_ID,
					email = Email("other@example.com"),
					userName = "기존 계정",
					invitedByInternalUserId = InternalUserId("iu_registrar"),
					createdAt = NOW.minusSeconds(7_200),
				)

			shouldThrow<DuplicateMerchantUserException> {
				newUseCase(merchantUserRepository, mockk()).execute(newCommand())
			}
		}

		test("duplicate email within the same merchant throws DuplicateMerchantUserException") {
			val merchantUserRepository = mockk<MerchantUserRepository>()
			every { merchantUserRepository.findById(INVITER_ID) } returns activeOwner()
			every { merchantUserRepository.findByMerchantIdAndLoginId(MERCHANT_ID, NEW_LOGIN_ID) } returns null
			every { merchantUserRepository.findByMerchantIdAndEmail(MERCHANT_ID, NEW_EMAIL) } returns
				MerchantUser.inviteInitialOwner(
					id = MerchantUserId("mu_existing"),
					merchantId = MERCHANT_ID,
					loginId = LoginId("other-login"),
					email = NEW_EMAIL,
					userName = "기존 계정",
					invitedByInternalUserId = InternalUserId("iu_registrar"),
					createdAt = NOW.minusSeconds(7_200),
				)

			shouldThrow<DuplicateMerchantUserException> {
				newUseCase(merchantUserRepository, mockk()).execute(newCommand())
			}
		}

		test("attempting to invite an OWNER throws IllegalArgumentException") {
			val merchantUserRepository = mockk<MerchantUserRepository>()
			every { merchantUserRepository.findById(INVITER_ID) } returns activeOwner()
			every { merchantUserRepository.findByMerchantIdAndLoginId(MERCHANT_ID, NEW_LOGIN_ID) } returns null
			every { merchantUserRepository.findByMerchantIdAndEmail(MERCHANT_ID, NEW_EMAIL) } returns null

			shouldThrow<IllegalArgumentException> {
				newUseCase(merchantUserRepository, mockk()).execute(newCommand(role = MerchantUserRole.OWNER))
			}
		}
	})
