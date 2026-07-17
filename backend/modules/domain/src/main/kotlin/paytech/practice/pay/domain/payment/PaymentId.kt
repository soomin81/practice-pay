package paytech.practice.pay.domain.payment

/**
 * 결제를 식별하는 공개(외부 노출용) ID를 표현하는 Value Object다.
 *
 * DB의 `payment.payment_id` 컬럼(`VARCHAR(40)`, `UNIQUE`)과 1:1로 대응하며,
 * 내부 전용 PK인 `payment_seq`(`BIGINT AUTO_INCREMENT`)와는 별개의 값이다.
 *
 * @property value 결제 공개 ID 문자열. 공백일 수 없고 [MAX_LENGTH]자를 넘을 수 없다.
 */
@JvmInline
value class PaymentId(val value: String) {

	init {
		require(value.isNotBlank()) { "PaymentId는 공백일 수 없습니다." }
		require(value.length <= MAX_LENGTH) { "PaymentId는 ${MAX_LENGTH}자를 초과할 수 없습니다: $value" }
	}

	companion object {
		/** `payment.payment_id` 컬럼의 최대 길이(`VARCHAR(40)`)와 동일하게 맞춘 값이다. */
		private const val MAX_LENGTH = 40
	}
}
