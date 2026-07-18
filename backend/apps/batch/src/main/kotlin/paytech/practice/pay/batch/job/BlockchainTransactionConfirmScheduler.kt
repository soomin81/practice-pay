package paytech.practice.pay.batch.job

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobOperator
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * `confirmBlockchainTransactionJob`을 주기적으로 실행한다.
 *
 * Spring Boot의 `JobLauncherApplicationRunner`(부팅 시 Job을 1회 자동 실행하는
 * 기본 동작)는 `application.yaml`의 `spring.batch.job.enabled: false`로 꺼뒀다 —
 * 이 Job은 "한 번 실행하고 끝"이 아니라 계속 폴링해야 해서, 실행 시점을 전부 이
 * 스케줄러가 정한다.
 *
 * 실행마다 `JobParameters`에 현재 시각을 넣는다 — Spring Batch는 같은
 * `JobParameters`로 이미 `COMPLETED`된 `JobInstance`를 다시 실행하지 못하게
 * 막는데(`docs/`에 없는, Spring Batch 자체의 동작), 매번 새 시각을 넣어 그때마다
 * 새 `JobInstance`로 인식되게 한다.
 *
 * [POLL_INTERVAL_MILLIS]는 `docs/`에 값이 없어 고정한 MVP 상수다 — Base의 블록
 * 생성 주기(~2초)를 감안해 놓친 Confirm이 없도록 충분히 자주 돌되, RPC 호출을
 * 과도하게 만들지 않는 선으로 잡았다.
 */
@Component
class BlockchainTransactionConfirmScheduler(
	private val jobOperator: JobOperator,
	private val confirmBlockchainTransactionJob: Job,
) {
	@Scheduled(fixedDelay = POLL_INTERVAL_MILLIS)
	fun poll() {
		val jobParameters =
			JobParametersBuilder()
				.addLong("run.timestamp", System.currentTimeMillis())
				.toJobParameters()
		try {
			jobOperator.start(confirmBlockchainTransactionJob, jobParameters)
		} catch (ex: RuntimeException) {
			logger.warn(ex) { "confirmBlockchainTransactionJob 실행에 실패했다 — 다음 폴링에서 다시 시도한다." }
		}
	}

	companion object {
		private const val POLL_INTERVAL_MILLIS = 10_000L
	}
}
