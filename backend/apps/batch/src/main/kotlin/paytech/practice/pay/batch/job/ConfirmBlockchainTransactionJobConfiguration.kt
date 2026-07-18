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
 * BlockchainTransaction 감지·Confirm 폴링 Job/Step 정의다. Step 하나, Tasklet 하나
 * (`ConfirmPendingBlockchainTransactionsTasklet`)로 이뤄진 가장 단순한 모양이다 —
 * 읽은 항목을 그대로 다시 쓰는 Chunk 지향 처리가 아니라("읽기 → 처리 → 쓰기"를
 * 나눌 이유가 없다, Use Case 호출 자체가 이미 저장까지 끝낸다), 대상 목록을 뽑아
 * 하나씩 Use Case를 호출하는 단일 명령형 동작이라 Tasklet이 더 맞는 모양이다.
 *
 * **`ResourcelessTransactionManager`를 쓴다** — 실제 DB 쓰기는 Tasklet 안에서
 * `ConfirmBlockchainTransactionUseCase`가 자기 트랜잭션으로 이미 처리하므로, Step
 * 레벨에서 Spring이 관리하는 트랜잭션으로 또 감쌀 필요가(오히려 감싸면 안 되는
 * 이유가) 있다 — `ConfirmPendingBlockchainTransactionsTasklet`의 KDoc 참고.
 * `JobRepository` 자체의 실행 기록(BATCH_* 테이블)은 이 트랜잭션 매니저와 무관하게
 * `DefaultBatchConfiguration`이 별도로 관리한다.
 *
 * 이 Job을 실제로 언제, 얼마나 자주 실행할지는 `BlockchainTransactionConfirmScheduler`의
 * 책임이다 — 이 파일은 Job이 "무엇을 하는지"만 정의한다.
 */
@Configuration
class ConfirmBlockchainTransactionJobConfiguration {
	@Bean
	fun confirmBlockchainTransactionStep(
		jobRepository: JobRepository,
		confirmPendingBlockchainTransactionsTasklet: ConfirmPendingBlockchainTransactionsTasklet,
	): Step =
		StepBuilder("confirmBlockchainTransactionStep", jobRepository)
			.tasklet(confirmPendingBlockchainTransactionsTasklet, ResourcelessTransactionManager())
			.build()

	@Bean
	fun confirmBlockchainTransactionJob(
		jobRepository: JobRepository,
		confirmBlockchainTransactionStep: Step,
	): Job =
		JobBuilder("confirmBlockchainTransactionJob", jobRepository)
			.start(confirmBlockchainTransactionStep)
			.build()
}
