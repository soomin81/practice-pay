package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.outbox.OutboxEvent

/**
 * [OutboxEvent] Aggregate를 저장하는 Command Repository Outbound Port다.
 *
 * 이 슬라이스(결제 생성)에서는 새 이벤트 적재만 필요해 `save`만 정의한다 — 발행
 * Worker Use Case가 추가될 때 조회·상태 갱신 메서드를 함께 확장한다.
 */
interface OutboxEventRepository {
	/** 새 Outbox 이벤트를 저장한다. */
	fun save(outboxEvent: OutboxEvent)
}
