package paytech.practice.pay.domain.webhook

/**
 * Webhook으로 통지할 이벤트를 식별하는 Value Object다.
 *
 * DB의 `webhook_delivery.event_id` 컬럼(`VARCHAR(50)`)과 대응하며,
 * `event_id + merchant_seq` 조합이 Webhook 전송의 멱등성 키다
 * (`docs/database/database-design.md`의 "주요 Unique" 참고).
 *
 * @property value 이벤트 ID 문자열. 공백일 수 없고 [MAX_LENGTH]자를 넘을 수 없다.
 */
@JvmInline
value class EventId(val value: String) {

	init {
		require(value.isNotBlank()) { "EventId는 공백일 수 없습니다." }
		require(value.length <= MAX_LENGTH) { "EventId는 ${MAX_LENGTH}자를 초과할 수 없습니다: $value" }
	}

	companion object {
		/** `webhook_delivery.event_id` 컬럼의 최대 길이(`VARCHAR(50)`)와 동일하게 맞춘 값이다. */
		private const val MAX_LENGTH = 50
	}
}
