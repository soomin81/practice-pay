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
import paytech.practice.pay.application.port.outbound.InternalUserRepository
import paytech.practice.pay.application.port.outbound.InvitationTokenHasher
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.domain.identity.AccountInvitation
import paytech.practice.pay.domain.identity.AccountInvitationStatus
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalUser
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.InternalUserRole
import paytech.practice.pay.domain.identity.LoginId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private val NOW: Instant = Instant.parse("2026-07-17T00:00:00Z")
private val FIXED_CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
private val ISSUER_ID = InternalUserId("iu_super_admin")
private val LOGIN_ID = LoginId("new-operator")
private val EMAIL = Email("new-operator@example.com")

private fun newCommand(): IssueInternalUserCommand =
	IssueInternalUserCommand(
		loginId = LOGIN_ID,
		email = EMAIL,
		userName = "새 운영자",
		role = InternalUserRole.OPERATOR,
		issuedByInternalUserId = ISSUER_ID,
	)

private class ImmediateTransactionManager : TransactionManager {
	override fun <T> runInTransaction(block: () -> T): T = block()
}

private class FakeIdGenerator : IdGenerator {
	private var counter = 0

	override fun newId(): String {
		counter += 1
		return "id$counter"
	}
}

private fun newUseCase(
	internalUserRepository: InternalUserRepository,
	accountInvitationRepository: AccountInvitationRepository,
	invitationTokenHasher: InvitationTokenHasher = mockk { every { hash(any()) } answers { "hashed:${firstArg<String>()}" } },
): IssueInternalUserUseCase =
	IssueInternalUserUseCase(
		internalUserRepository = internalUserRepository,
		accountInvitationRepository = accountInvitationRepository,
		invitationTokenHasher = invitationTokenHasher,
		idGenerator = FakeIdGenerator(),
		transactionManager = ImmediateTransactionManager(),
		clock = FIXED_CLOCK,
	)

class IssueInternalUserUseCaseTest :
	FunSpec({

		test("issues an INVITED InternalUser with a PENDING AccountInvitation and returns the raw token") {
			val internalUserRepository = mockk<InternalUserRepository>(relaxed = true)
			val accountInvitationRepository = mockk<AccountInvitationRepository>(relaxed = true)
			every { internalUserRepository.findByLoginId(LOGIN_ID) } returns null
			every { internalUserRepository.findByEmail(EMAIL) } returns null

			val savedUsers = mutableListOf<InternalUser>()
			val savedInvitations = mutableListOf<AccountInvitation>()
			every { internalUserRepository.save(capture(savedUsers)) } returns Unit
			every { accountInvitationRepository.save(capture(savedInvitations)) } returns Unit

			val result = newUseCase(internalUserRepository, accountInvitationRepository).execute(newCommand())

			result.loginId shouldBe LOGIN_ID
			result.email shouldBe EMAIL
			result.role shouldBe InternalUserRole.OPERATOR
			result.invitationToken shouldNotBe "hashed:${result.invitationToken}"

			verify(exactly = 1) { internalUserRepository.save(any()) }
			verify(exactly = 1) { accountInvitationRepository.save(any()) }

			savedUsers.single().status.name shouldBe "INVITED"
			savedUsers.single().createdByInternalUserId shouldBe ISSUER_ID

			val savedInvitation = savedInvitations.single()
			savedInvitation.status shouldBe AccountInvitationStatus.PENDING
			savedInvitation.internalUserId shouldBe savedUsers.single().id
			savedInvitation.tokenHash shouldBe "hashed:${result.invitationToken}"
		}

		test("duplicate loginId throws DuplicateInternalUserException") {
			val internalUserRepository = mockk<InternalUserRepository>()
			every { internalUserRepository.findByLoginId(LOGIN_ID) } returns
				InternalUser.bootstrap(
					id = InternalUserId("iu_existing"),
					loginId = LOGIN_ID,
					email = Email("other@example.com"),
					userName = "기존 관리자",
					passwordHash = "hash",
					createdAt = NOW.minusSeconds(3_600),
				)

			shouldThrow<DuplicateInternalUserException> {
				newUseCase(internalUserRepository, mockk()).execute(newCommand())
			}
		}

		test("duplicate email throws DuplicateInternalUserException") {
			val internalUserRepository = mockk<InternalUserRepository>()
			every { internalUserRepository.findByLoginId(any()) } returns null
			every { internalUserRepository.findByEmail(EMAIL) } returns
				InternalUser.bootstrap(
					id = InternalUserId("iu_existing"),
					loginId = LoginId("other-login"),
					email = EMAIL,
					userName = "기존 관리자",
					passwordHash = "hash",
					createdAt = NOW.minusSeconds(3_600),
				)

			shouldThrow<DuplicateInternalUserException> {
				newUseCase(internalUserRepository, mockk()).execute(newCommand())
			}
		}
	})
