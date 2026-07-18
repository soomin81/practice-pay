package paytech.practice.pay.application.outbox

import paytech.practice.pay.domain.outbox.OutboxEventStatus
import paytech.practice.pay.domain.shared.EventId

/** [PublishOutboxEventUseCase]의 결과다. 이번 발행 시도 이후의 최종 상태를 돌려준다. */
data class PublishOutboxEventResult(
	val eventId: EventId,
	val outboxEventStatus: OutboxEventStatus,
)
