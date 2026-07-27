package paytech.practice.pay.batch.job

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.stereotype.Component
import paytech.practice.pay.application.port.outbound.AccountInvitationRepository
import paytech.practice.pay.application.sweep.ExpireAccountInvitationCommand
import paytech.practice.pay.application.sweep.ExpireAccountInvitationUseCase
import java.time.Clock

private val logger = KotlinLogging.logger {}

/**
 * 초대 만료 Sweep 한 회차다 — `AccountInvitationRepository.findExpirablePending(now)`로 대상을
 * 뽑아 `ExpireAccountInvitationUseCase`를 하나씩 호출한다. 다른 Tasklet과 같은 이유로 하나가
 * 실패해도 나머지를 계속 처리하고(개별 `try/catch`), Step 트랜잭션에 기대지 않는다.
 */
@Component
class ExpirePendingInvitationsTasklet(
	private val accountInvitationRepository: AccountInvitationRepository,
	private val expireAccountInvitationUseCase: ExpireAccountInvitationUseCase,
	private val clock: Clock,
) : Tasklet {
	override fun execute(
		contribution: StepContribution,
		chunkContext: ChunkContext,
	): RepeatStatus {
		val expirable = accountInvitationRepository.findExpirablePending(clock.instant())
		logger.info { "초대 만료 대상 ${expirable.size}건" }

		for (invitation in expirable) {
			try {
				expireAccountInvitationUseCase.execute(ExpireAccountInvitationCommand(invitation.id))
			} catch (ex: RuntimeException) {
				logger.warn(ex) { "AccountInvitation(${invitation.id.value}) 만료 실패 — 다음 폴링에서 재시도한다." }
			}
		}

		return RepeatStatus.FINISHED
	}
}
