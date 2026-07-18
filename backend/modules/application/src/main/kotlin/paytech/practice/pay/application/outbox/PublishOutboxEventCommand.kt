package paytech.practice.pay.application.outbox

import paytech.practice.pay.domain.shared.EventId

/**
 * [PublishOutboxEventUseCase]의 입력이다.
 *
 * 이미 존재하는 `OutboxEvent`(`PENDING`/`RETRY_WAITING` 중 하나) 하나를 대상으로 한
 * 발행 시도 한 번이다 — `OutboxEvent`를 처음 만드는 것은 이 Use Case의 책임이
 * 아니다(`CreatePaymentUseCase`/`ConfirmBlockchainTransactionUseCase`가 자기
 * 트랜잭션 안에서 만든다).
 */
data class PublishOutboxEventCommand(
	val eventId: EventId,
)
