package paytech.practice.pay.batch.job

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import paytech.practice.pay.application.port.outbound.AccountInvitationRepository
import paytech.practice.pay.application.sweep.ExpireAccountInvitationCommand
import paytech.practice.pay.application.sweep.ExpireAccountInvitationUseCase
import paytech.practice.pay.domain.identity.AccountInvitation
import paytech.practice.pay.domain.identity.AccountInvitationId
import paytech.practice.pay.domain.identity.InternalUserId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private val NOW: Instant = Instant.parse("2026-07-19T00:00:00Z")
private val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)

private fun invitation(id: String): AccountInvitation =
	AccountInvitation.forInternalUser(
		id = AccountInvitationId(id),
		internalUserId = InternalUserId("iu_1"),
		tokenHash = "hash_$id",
		expiresAt = NOW.minusSeconds(3_600),
		createdAt = NOW.minusSeconds(7_200),
	)

class ExpirePendingInvitationsTaskletTest :
	FunSpec({

		test("calls the use case once for every expirable invitation") {
			val repository = mockk<AccountInvitationRepository>()
			val useCase = mockk<ExpireAccountInvitationUseCase>(relaxed = true)
			val expirable = listOf(invitation("ai1"), invitation("ai2"), invitation("ai3"))
			every { repository.findExpirablePending(NOW) } returns expirable

			val result =
				ExpirePendingInvitationsTasklet(repository, useCase, CLOCK)
					.execute(mockk<StepContribution>(), mockk<ChunkContext>())

			result shouldBe RepeatStatus.FINISHED
			expirable.forEach { verify(exactly = 1) { useCase.execute(ExpireAccountInvitationCommand(it.id)) } }
		}

		test("a failure for one invitation does not stop the rest") {
			val repository = mockk<AccountInvitationRepository>()
			val useCase = mockk<ExpireAccountInvitationUseCase>()
			val failing = invitation("ai-failing")
			val succeeding = invitation("ai-succeeding")
			every { repository.findExpirablePending(NOW) } returns listOf(failing, succeeding)
			every { useCase.execute(ExpireAccountInvitationCommand(failing.id)) } throws IllegalStateException("boom")
			every { useCase.execute(ExpireAccountInvitationCommand(succeeding.id)) } returns Unit

			val result =
				ExpirePendingInvitationsTasklet(repository, useCase, CLOCK)
					.execute(mockk<StepContribution>(), mockk<ChunkContext>())

			result shouldBe RepeatStatus.FINISHED
			verify(exactly = 1) { useCase.execute(ExpireAccountInvitationCommand(succeeding.id)) }
		}

		test("an empty list is a no-op") {
			val repository = mockk<AccountInvitationRepository>()
			val useCase = mockk<ExpireAccountInvitationUseCase>()
			every { repository.findExpirablePending(NOW) } returns emptyList()

			val result =
				ExpirePendingInvitationsTasklet(repository, useCase, CLOCK)
					.execute(mockk<StepContribution>(), mockk<ChunkContext>())

			result shouldBe RepeatStatus.FINISHED
			verify(exactly = 0) { useCase.execute(any()) }
		}
	})
