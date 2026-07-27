package paytech.practice.pay.application.sweep

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import paytech.practice.pay.application.port.outbound.AccountInvitationRepository
import paytech.practice.pay.domain.identity.AccountInvitation
import paytech.practice.pay.domain.identity.AccountInvitationId
import paytech.practice.pay.domain.identity.AccountInvitationStatus
import paytech.practice.pay.domain.identity.InternalUserId
import java.time.Instant

private val NOW: Instant = Instant.parse("2026-07-19T00:00:00Z")
private val INVITATION_ID = AccountInvitationId("ai_target")

private fun pendingInvitation(): AccountInvitation =
	AccountInvitation.forInternalUser(
		id = INVITATION_ID,
		internalUserId = InternalUserId("iu_1"),
		tokenHash = "hash",
		expiresAt = NOW.minusSeconds(3_600),
		createdAt = NOW.minusSeconds(7_200),
	)

private fun command() = ExpireAccountInvitationCommand(INVITATION_ID)

class ExpireAccountInvitationUseCaseTest :
	FunSpec({

		test("a PENDING invitation is expired and saved") {
			val invitation = pendingInvitation()
			val repository = mockk<AccountInvitationRepository>()
			every { repository.findById(INVITATION_ID) } returns invitation
			justRun { repository.save(any()) }

			ExpireAccountInvitationUseCase(repository).execute(command())

			invitation.status shouldBe AccountInvitationStatus.EXPIRED
			verify { repository.save(invitation) }
		}

		test("an already ACCEPTED invitation is left untouched (re-check guards concurrency)") {
			// 후보를 뽑은 뒤 수락됐을 수 있다 — 다시 읽어 PENDING이 아니면 조용히 지나간다.
			val accepted = pendingInvitation().apply { accept(NOW.minusSeconds(60)) }
			val repository = mockk<AccountInvitationRepository>()
			every { repository.findById(INVITATION_ID) } returns accepted

			ExpireAccountInvitationUseCase(repository).execute(command())

			accepted.status shouldBe AccountInvitationStatus.ACCEPTED
			verify(exactly = 0) { repository.save(any()) }
		}

		test("a missing invitation is a no-op") {
			val repository = mockk<AccountInvitationRepository>()
			every { repository.findById(INVITATION_ID) } returns null

			ExpireAccountInvitationUseCase(repository).execute(command())

			verify(exactly = 0) { repository.save(any()) }
		}
	})
