package paytech.practice.pay.batch.job

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobOperator
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * `expireCheckoutsJob`을 주기적으로 실행한다 — `ExpireAccountInvitationsScheduler`와 같은
 * 이유·같은 모양이고 폴링 주기도 같은 60초다(그 KDoc 참고: 만료 정리는 결제 흐름을
 * 진행시키는 Worker와 달리 자주 돌 이유가 없다).
 */
@Component
class ExpireCheckoutsScheduler(
	private val jobOperator: JobOperator,
	private val expireCheckoutsJob: Job,
) {
	@Scheduled(fixedDelay = POLL_INTERVAL_MILLIS)
	fun poll() {
		val jobParameters =
			JobParametersBuilder()
				.addLong("run.timestamp", System.currentTimeMillis())
				.toJobParameters()
		try {
			jobOperator.start(expireCheckoutsJob, jobParameters)
		} catch (ex: RuntimeException) {
			logger.warn(ex) { "expireCheckoutsJob 실행에 실패했다 — 다음 폴링에서 다시 시도한다." }
		}
	}

	companion object {
		private const val POLL_INTERVAL_MILLIS = 60_000L
	}
}
