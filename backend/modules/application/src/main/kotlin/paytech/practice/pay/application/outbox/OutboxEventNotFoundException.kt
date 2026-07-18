package paytech.practice.pay.application.outbox

import paytech.practice.pay.domain.shared.EventId

/** 존재하지 않는 [EventId]로 발행을 시도했을 때 던진다. */
class OutboxEventNotFoundException(
	eventId: EventId,
) : RuntimeException("OutboxEvent를 찾을 수 없습니다: ${eventId.value}")
