package paytech.practice.pay.domain.outbox

/**
 * [OutboxEvent]의 상태를 표현한다.
 *
 * `docs/domain/state-transitions.md`는 `OutboxEvent`를 다루지 않는다 — 아래 상태와
 * 전이는 `outbox_event` 테이블의 `ck_outbox_event_status`/`ck_outbox_published_at`
 * 제약과 `event_status`/`retry_count`/`next_retry_at`/`published_at` 컬럼 구성에서
 * 직접 추론했다(`docs/database/database-design.md`). Transactional Outbox 패턴에서
 * 비동기로 이벤트를 발행하는 흐름이라, 같은 재시도 패턴을 이미 쓰고 있는
 * [paytech.practice.pay.domain.webhook.WebhookDelivery]의 상태 모델을 그대로
 * 옮겨왔다.
 *
 * 정상 흐름: `PENDING → PROCESSING → PUBLISHED`
 *
 * 발행 실패 시 `RETRY_WAITING`을 거쳐 재시도하고, 최대 횟수를 초과하면 `FAILED`로
 * 처리한다. "최대 횟수"의 구체적인 값은 이 Aggregate가 정하지 않는다 —
 * [OutboxEvent.retryCount]만 셀 뿐, 재시도할지 최종 실패 처리할지의 판단은
 * 호출부(발행 Worker)의 책임이다.
 *
 * `PUBLISHED`, `FAILED`는 종료 상태다.
 */
enum class OutboxEventStatus {
	PENDING,
	PROCESSING,
	PUBLISHED,
	RETRY_WAITING,
	FAILED,
}
