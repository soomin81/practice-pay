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
 * OutboxEvent 발행 폴링 Job/Step 정의다 — `ConfirmBlockchainTransactionJobConfiguration`과
 * 완전히 같은 이유로 같은 모양이다(Step 하나, Tasklet 하나
 * `PublishPendingOutboxEventsTasklet`, Chunk 지향이 아니라 Tasklet).
 *
 * `ResourcelessTransactionManager`를 쓰는 이유도 동일하다 — 실제 DB 쓰기는
 * `PublishOutboxEventUseCase`가 자기 트랜잭션으로 이미 처리한다.
 */
@Configuration
class PublishOutboxEventJobConfiguration {
	@Bean
	fun publishOutboxEventStep(
		jobRepository: JobRepository,
		publishPendingOutboxEventsTasklet: PublishPendingOutboxEventsTasklet,
	): Step =
		StepBuilder("publishOutboxEventStep", jobRepository)
			.tasklet(publishPendingOutboxEventsTasklet, ResourcelessTransactionManager())
			.build()

	@Bean
	fun publishOutboxEventJob(
		jobRepository: JobRepository,
		publishOutboxEventStep: Step,
	): Job =
		JobBuilder("publishOutboxEventJob", jobRepository)
			.start(publishOutboxEventStep)
			.build()
}
