package paytech.practice.pay.domain.shared

/**
 * 도메인 이벤트를 식별하는 Value Object다.
 *
 * DB의 `outbox_event.event_id`(`VARCHAR(50)`, `UNIQUE`) 컬럼과 대응하며,
 * `webhook_delivery.event_id` 컬럼도 같은 값을 참조한다 — `OutboxEvent`가 만든
 * 이벤트 하나를 `WebhookDelivery`가 가맹점에게 전달하는 관계라 두 Aggregate가
 * 이 타입을 공유한다(`docs/domain/glossary.md`, `docs/database/database-design.md`의
 * "주요 Unique" 참고). `event_id + merchant_seq` 조합이 Webhook 전송의 멱등성 키다.
 *
 * @property value 이벤트 ID 문자열. 공백일 수 없고 [MAX_LENGTH]자를 넘을 수 없다.
 */
@JvmInline
value class EventId(
	val value: String,
) {
	init {
		require(value.isNotBlank()) { "EventId는 공백일 수 없습니다." }
		require(value.length <= MAX_LENGTH) { "EventId는 ${MAX_LENGTH}자를 초과할 수 없습니다: $value" }
	}

	companion object {
		/** `outbox_event.event_id`/`webhook_delivery.event_id` 컬럼의 최대 길이(`VARCHAR(50)`)와 동일하게 맞춘 값이다. */
		private const val MAX_LENGTH = 50
	}
}
