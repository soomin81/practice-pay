package paytech.practice.pay.application.identity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import paytech.practice.pay.application.port.outbound.AccountInvitationRepository
import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.InvitationTokenHasher
import paytech.practice.pay.application.port.outbound.MerchantUserRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.domain.identity.AccountInvitation
import paytech.practice.pay.domain.identity.AccountInvitationId
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
private val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
private val MERCHANT_ID = MerchantId("mrc_test_001")
private val REQUESTER_ID = MerchantUserId("mu_requester")
private val TARGET_ID = MerchantUserId("mu_target")

private fun activeOwner(): MerchantUser =
	MerchantUser
		.inviteInitialOwner(
			id = REQUESTER_ID,
			merchantId = MERCHANT_ID,
			loginId = LoginId("owner01"),
			email = Email("owner@example.com"),
			userName = "오너",
			invitedByInternalUserId = InternalUserId("iu_1"),
			createdAt = NOW.minusSeconds(3_600),
		).apply { activate("hashed-password", NOW.minusSeconds(1_800)) }

private fun invitedSubAccount(): MerchantUser =
	MerchantUser.inviteSubAccount(
		id = TARGET_ID,
		merchantId = MERCHANT_ID,
		loginId = LoginId("new-admin"),
		email = Email("new-admin@example.com"),
		userName = "하위 계정",
		role = MerchantUserRole.ADMIN,
		invitedByMerchantUserId = REQUESTER_ID,
		createdAt = NOW.minusSeconds(3_600),
	)

private fun pendingInvitation(): AccountInvitation =
	AccountInvitation.forMerchantUser(
		id = AccountInvitationId("ai_old"),
		merchantUserId = TARGET_ID,
		tokenHash = "old-hash",
		expiresAt = NOW.plusSeconds(3_600),
		createdAt = NOW.minusSeconds(3_600),
	)

/** `runInTransaction`을 그대로 실행하는 가짜 — 블록 안의 쓰기 순서를 검증하기 위해서다. */
private fun directTransactionManager(): TransactionManager =
	object : TransactionManager {
		override fun <T> runInTransaction(block: () -> T): T = block()
	}

private fun resendUseCase(
	merchantUserRepository: MerchantUserRepository,
	accountInvitationRepository: AccountInvitationRepository,
): ResendMerchantUserInvitationUseCase {
	val idGenerator = mockk<IdGenerator>()
	every { idGenerator.newId() } returnsMany listOf("raw-token", "new-invitation")
	val hasher = mockk<InvitationTokenHasher>()
	every { hasher.hash(any()) } answers { "hashed-" + firstArg<String>() }
	return ResendMerchantUserInvitationUseCase(
		merchantUserRepository = merchantUserRepository,
		accountInvitationRepository = accountInvitationRepository,
		invitationTokenHasher = hasher,
		idGenerator = idGenerator,
		transactionManager = directTransactionManager(),
		clock = CLOCK,
	)
}

private fun resendCommand() =
	ResendMerchantUserInvitationCommand(targetMerchantUserId = TARGET_ID, requestedByMerchantUserId = REQUESTER_ID)

private fun revokeCommand() =
	RevokeMerchantUserInvitationCommand(targetMerchantUserId = TARGET_ID, requestedByMerchantUserId = REQUESTER_ID)

class MerchantUserInvitationUseCasesTest :
	FunSpec({

		test("resend revokes the existing invitation and issues a new token") {
			val existing = pendingInvitation()
			val users = mockk<MerchantUserRepository>()
			val invitations = mockk<AccountInvitationRepository>()
			every { users.findById(REQUESTER_ID) } returns activeOwner()
			every { users.findById(TARGET_ID) } returns invitedSubAccount()
			every { invitations.findPendingByMerchantUserId(TARGET_ID) } returns existing
			val saved = mutableListOf<AccountInvitation>()
			every { invitations.save(capture(saved)) } returns Unit

			val result = resendUseCase(users, invitations).execute(resendCommand())

			// 이전 초대가 무효화돼야 옛 링크가 죽는다 — 이 슬라이스의 핵심 규칙이다.
			existing.status shouldBe AccountInvitationStatus.REVOKED
			result.invitationToken shouldBe "raw-token"
			result.invitationExpiresAt shouldBe NOW.plusSeconds(604_800)
			saved.map { it.status } shouldBe listOf(AccountInvitationStatus.REVOKED, AccountInvitationStatus.PENDING)
		}

		test("resend works even when there is no existing invitation (it was revoked earlier)") {
			val users = mockk<MerchantUserRepository>()
			val invitations = mockk<AccountInvitationRepository>()
			every { users.findById(REQUESTER_ID) } returns activeOwner()
			every { users.findById(TARGET_ID) } returns invitedSubAccount()
			every { invitations.findPendingByMerchantUserId(TARGET_ID) } returns null
			val saved = slot<AccountInvitation>()
			every { invitations.save(capture(saved)) } returns Unit

			resendUseCase(users, invitations).execute(resendCommand())

			saved.captured.status shouldBe AccountInvitationStatus.PENDING
		}

		test("resend is rejected when the target is not INVITED") {
			val users = mockk<MerchantUserRepository>()
			every { users.findById(REQUESTER_ID) } returns activeOwner()
			every { users.findById(TARGET_ID) } returns
				invitedSubAccount().apply { activate("hashed-password", NOW.minusSeconds(60)) }

			shouldThrow<InvitationNotManageableException> {
				resendUseCase(users, mockk()).execute(resendCommand())
			}
		}

		test("revoke marks the pending invitation REVOKED and leaves the account INVITED") {
			val existing = pendingInvitation()
			val target = invitedSubAccount()
			val users = mockk<MerchantUserRepository>()
			val invitations = mockk<AccountInvitationRepository>()
			every { users.findById(REQUESTER_ID) } returns activeOwner()
			every { users.findById(TARGET_ID) } returns target
			every { invitations.findPendingByMerchantUserId(TARGET_ID) } returns existing
			justRun { invitations.save(any()) }

			RevokeMerchantUserInvitationUseCase(users, invitations, CLOCK).execute(revokeCommand())

			existing.status shouldBe AccountInvitationStatus.REVOKED
			// 취소는 계정을 건드리지 않는다(종료와 분리한 판단).
			target.status shouldBe paytech.practice.pay.domain.identity.AccountStatus.INVITED
			verify(exactly = 0) { users.save(any()) }
		}

		test("revoke is rejected when there is no pending invitation") {
			val users = mockk<MerchantUserRepository>()
			val invitations = mockk<AccountInvitationRepository>()
			every { users.findById(REQUESTER_ID) } returns activeOwner()
			every { users.findById(TARGET_ID) } returns invitedSubAccount()
			every { invitations.findPendingByMerchantUserId(TARGET_ID) } returns null

			shouldThrow<InvitationNotManageableException> {
				RevokeMerchantUserInvitationUseCase(users, invitations, CLOCK).execute(revokeCommand())
			}
		}

		test("both use cases reuse the management guard (self-targeting is rejected)") {
			val users = mockk<MerchantUserRepository>()
			every { users.findById(REQUESTER_ID) } returns activeOwner()

			shouldThrow<MerchantUserNotManageableException> {
				resendUseCase(users, mockk()).execute(resendCommand().copy(targetMerchantUserId = REQUESTER_ID))
			}
			shouldThrow<MerchantUserNotManageableException> {
				RevokeMerchantUserInvitationUseCase(users, mockk(), CLOCK)
					.execute(revokeCommand().copy(targetMerchantUserId = REQUESTER_ID))
			}
		}
	})
