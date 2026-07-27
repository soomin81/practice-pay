package paytech.practice.pay.batch.job

import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 초대 만료 Sweep Job/Step 정의다 — 다른 폴링 Job과 같은 모양이다(Step 하나, Tasklet 하나,
 * Chunk 지향이 아니라 Tasklet). `ResourcelessTransactionManager`를 쓰는 이유도 동일하다 —
 * 실제 DB 쓰기는 `ExpireAccountInvitationUseCase`가 처리한다.
 */
@Configuration
class ExpireAccountInvitationsJobConfiguration {
	@Bean
	fun expireAccountInvitationsStep(
		jobRepository: JobRepository,
		expirePendingInvitationsTasklet: ExpirePendingInvitationsTasklet,
	): Step =
		StepBuilder("expireAccountInvitationsStep", jobRepository)
			.tasklet(expirePendingInvitationsTasklet, ResourcelessTransactionManager())
			.build()

	@Bean
	fun expireAccountInvitationsJob(
		jobRepository: JobRepository,
		expireAccountInvitationsStep: Step,
	): Job =
		JobBuilder("expireAccountInvitationsJob", jobRepository)
			.start(expireAccountInvitationsStep)
			.build()
}
