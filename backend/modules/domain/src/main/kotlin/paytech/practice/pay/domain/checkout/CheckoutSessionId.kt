package paytech.practice.pay.domain.checkout

/**
 * 체크아웃 세션을 식별하는 공개(외부 노출용) ID를 표현하는 Value Object다.
 *
 * DB의 `checkout_session.checkout_session_id` 컬럼(`VARCHAR(50)`, `UNIQUE`)과
 * 대응하며, 내부 전용 PK인 `checkout_session_seq`(`BIGINT AUTO_INCREMENT`)와는
 * 별개의 값이다.
 *
 * @property value 체크아웃 세션 공개 ID 문자열. 공백일 수 없고 [MAX_LENGTH]자를 넘을 수 없다.
 */
@JvmInline
value class CheckoutSessionId(
	val value: String,
) {
	init {
		require(value.isNotBlank()) { "CheckoutSessionId는 공백일 수 없습니다." }
		require(value.length <= MAX_LENGTH) { "CheckoutSessionId는 ${MAX_LENGTH}자를 초과할 수 없습니다: $value" }
	}

	companion object {
		/** `checkout_session.checkout_session_id` 컬럼의 최대 길이(`VARCHAR(50)`)와 동일하게 맞춘 값이다. */
		private const val MAX_LENGTH = 50
	}
}
