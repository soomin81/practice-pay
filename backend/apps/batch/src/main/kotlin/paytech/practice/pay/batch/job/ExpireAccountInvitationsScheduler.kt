package paytech.practice.pay.batch.job

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobOperator
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * `expireAccountInvitationsJob`을 주기적으로 실행한다 — 다른 폴링 Scheduler와 같은 모양이다
 * (`JobOperator.start` + 매번 새 `JobParameters`로 `JobInstance` 재실행 거부를 피한다).
 *
 * **[POLL_INTERVAL_MILLIS]는 다른 Worker의 10초와 다른 60초다.** 초대 만료는 며칠 단위라
 * 몇 초 지연이 무의미하고, 상태를 진실과 맞추는 정리 성격이라 자주 돌 이유가 없다
 * (Confirm/Webhook/매도가 결제 흐름을 진행시키는 것과 다른 성격) — `docs/`에 값이 없어
 * 고정한 MVP 상수다.
 */
@Component
class ExpireAccountInvitationsScheduler(
	private val jobOperator: JobOperator,
	private val expireAccountInvitationsJob: Job,
) {
	@Scheduled(fixedDelay = POLL_INTERVAL_MILLIS)
	fun poll() {
		val jobParameters =
			JobParametersBuilder()
				.addLong("run.timestamp", System.currentTimeMillis())
				.toJobParameters()
		try {
			jobOperator.start(expireAccountInvitationsJob, jobParameters)
		} catch (ex: RuntimeException) {
			logger.warn(ex) { "expireAccountInvitationsJob 실행에 실패했다 — 다음 폴링에서 다시 시도한다." }
		}
	}

	companion object {
		private const val POLL_INTERVAL_MILLIS = 60_000L
	}
}
