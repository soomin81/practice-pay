package paytech.practice.pay.batch.job

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobOperator
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * `sellToFakeExchangeJob`을 주기적으로 실행한다 — `BlockchainTransactionConfirmScheduler`/
 * `OutboxEventPublishScheduler`와 같은 이유로 같은 모양이다(`JobOperator.start` +
 * 매번 새 `JobParameters`로 `JobInstance` 재실행 거부를 피한다).
 *
 * [POLL_INTERVAL_MILLIS]도 `docs/`에 값이 없어 고정한 MVP 상수다 — 다른 두 Worker와
 * 같은 10초로 맞췄다(Fake Exchange 매도가 Confirm/Webhook보다 더 급할 이유가 없다).
 */
@Component
class SellToFakeExchangeScheduler(
	private val jobOperator: JobOperator,
	private val sellToFakeExchangeJob: Job,
) {
	@Scheduled(fixedDelay = POLL_INTERVAL_MILLIS)
	fun poll() {
		val jobParameters =
			JobParametersBuilder()
				.addLong("run.timestamp", System.currentTimeMillis())
				.toJobParameters()
		try {
			jobOperator.start(sellToFakeExchangeJob, jobParameters)
		} catch (ex: RuntimeException) {
			logger.warn(ex) { "sellToFakeExchangeJob 실행에 실패했다 — 다음 폴링에서 다시 시도한다." }
		}
	}

	companion object {
		private const val POLL_INTERVAL_MILLIS = 10_000L
	}
}
