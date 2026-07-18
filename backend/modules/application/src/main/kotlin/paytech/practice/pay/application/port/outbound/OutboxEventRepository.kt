package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.outbox.OutboxEvent
import paytech.practice.pay.domain.shared.EventId
import java.time.Instant

/**
 * [OutboxEvent] Aggregate를 저장·복원하는 Command Repository Outbound Port다.
 */
interface OutboxEventRepository {
	/** OutboxEvent를 저장한다(신규 생성·상태 변경 모두 이 메서드로 반영한다). */
	fun save(outboxEvent: OutboxEvent)

	/** `event_id`로 OutboxEvent를 찾는다. 없으면 `null`이다. */
	fun findById(eventId: EventId): OutboxEvent?

	/**
	 * 발행을 기다리는 OutboxEvent를 전부 찾는다 — 발행 Worker(`apps:batch`)가
	 * 폴링 대상 목록을 뽑을 때 쓴다. `docs/database/database-design.md`의
	 * "Outbox 발행: `event_status + next_retry_at + created_at`" 인덱스와
	 * 대응한다: `PENDING`이거나, `RETRY_WAITING`이면서 재시도 예정 시각([now])이
	 * 이미 지난 것만 대상이다. 오래 쌓인 것부터 먼저 처리하도록 `created_at`
	 * 오름차순으로 반환한다.
	 */
	fun findPendingPublication(now: Instant): List<OutboxEvent>
}
