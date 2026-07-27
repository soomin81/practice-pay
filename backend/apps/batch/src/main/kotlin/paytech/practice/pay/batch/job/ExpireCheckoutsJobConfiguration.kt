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
 * 체크아웃 만료 Sweep Job/Step 정의다 — 다른 폴링 Job과 같은 모양이다.
 * `ResourcelessTransactionManager`를 쓰는 이유도 동일하다 — 실제 DB 쓰기(Payment+세션 교차
 * 애그리게이트)는 `ExpireCheckoutUseCase`가 자기 트랜잭션으로 처리한다.
 */
@Configuration
class ExpireCheckoutsJobConfiguration {
	@Bean
	fun expireCheckoutsStep(
		jobRepository: JobRepository,
		expireExpiredCheckoutsTasklet: ExpireExpiredCheckoutsTasklet,
	): Step =
		StepBuilder("expireCheckoutsStep", jobRepository)
			.tasklet(expireExpiredCheckoutsTasklet, ResourcelessTransactionManager())
			.build()

	@Bean
	fun expireCheckoutsJob(
		jobRepository: JobRepository,
		expireCheckoutsStep: Step,
	): Job =
		JobBuilder("expireCheckoutsJob", jobRepository)
			.start(expireCheckoutsStep)
			.build()
}
