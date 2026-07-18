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
 * Fake Exchange 매도 폴링 Job/Step 정의다 — `ConfirmBlockchainTransactionJobConfiguration`/
 * `PublishOutboxEventJobConfiguration`과 완전히 같은 이유로 같은 모양이다(Step 하나,
 * Tasklet 하나 `SellPendingPaymentsToFakeExchangeTasklet`, Chunk 지향이 아니라 Tasklet).
 *
 * `ResourcelessTransactionManager`를 쓰는 이유도 동일하다 — 실제 DB 쓰기는
 * `SellToFakeExchangeUseCase`가 자기 트랜잭션으로 이미 처리한다.
 */
@Configuration
class SellToFakeExchangeJobConfiguration {
	@Bean
	fun sellToFakeExchangeStep(
		jobRepository: JobRepository,
		sellPendingPaymentsToFakeExchangeTasklet: SellPendingPaymentsToFakeExchangeTasklet,
	): Step =
		StepBuilder("sellToFakeExchangeStep", jobRepository)
			.tasklet(sellPendingPaymentsToFakeExchangeTasklet, ResourcelessTransactionManager())
			.build()

	@Bean
	fun sellToFakeExchangeJob(
		jobRepository: JobRepository,
		sellToFakeExchangeStep: Step,
	): Job =
		JobBuilder("sellToFakeExchangeJob", jobRepository)
			.start(sellToFakeExchangeStep)
			.build()
}
