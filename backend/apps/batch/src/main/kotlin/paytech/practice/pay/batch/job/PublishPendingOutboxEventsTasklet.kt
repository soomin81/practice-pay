package paytech.practice.pay.batch.job

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.stereotype.Component
import paytech.practice.pay.application.outbox.PublishOutboxEventCommand
import paytech.practice.pay.application.outbox.PublishOutboxEventUseCase
import paytech.practice.pay.application.port.outbound.OutboxEventRepository
import java.time.Clock

private val logger = KotlinLogging.logger {}

/**
 * OutboxEvent 발행 폴링 한 회차다 — `OutboxEventRepository.findPendingPublication()`으로
 * 대상 목록을 뽑아 `PublishOutboxEventUseCase`를 하나씩 호출한다
 * (`docs/database/database-design.md`의 "Outbox 발행" 인덱스가 암시하는 그
 * Worker, `OutboxEvent`의 KDoc이 범위 밖으로 남겨뒀던 "별도 발행 Worker").
 *
 * `ConfirmPendingBlockchainTransactionsTasklet`과 같은 이유로 하나가 실패해도
 * 나머지를 계속 처리하고(개별 `try/catch`), Step 트랜잭션에 기대지 않는다 — 각
 * `PublishOutboxEventUseCase.execute()` 호출이 이미 자기 트랜잭션으로 저장까지
 * 끝낸다.
 */
@Component
class PublishPendingOutboxEventsTasklet(
	private val outboxEventRepository: OutboxEventRepository,
	private val publishOutboxEventUseCase: PublishOutboxEventUseCase,
	private val clock: Clock,
) : Tasklet {
	override fun execute(
		contribution: StepContribution,
		chunkContext: ChunkContext,
	): RepeatStatus {
		val pending = outboxEventRepository.findPendingPublication(clock.instant())
		logger.info { "Outbox 발행 대상 ${pending.size}건" }

		for (outboxEvent in pending) {
			try {
				publishOutboxEventUseCase.execute(PublishOutboxEventCommand(outboxEvent.eventId))
			} catch (ex: RuntimeException) {
				logger.warn(ex) { "OutboxEvent(${outboxEvent.eventId.value}) 발행 실패 — 다음 폴링에서 재시도한다." }
			}
		}

		return RepeatStatus.FINISHED
	}
}
