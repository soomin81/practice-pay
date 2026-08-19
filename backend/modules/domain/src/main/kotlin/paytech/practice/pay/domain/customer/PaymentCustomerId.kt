package paytech.practice.pay.domain.customer

/**
 * 구매자 정보를 식별하는 공개 ID를 표현하는 Value Object다.
 *
 * DB의 `payment_customer.payment_customer_id` 컬럼(`VARCHAR(50)`, `UNIQUE`)과 대응한다.
 * **실제 멱등성 키는 이 값이 아니라 `payment_seq`다**(결제 1건당 1건, `UNIQUE`).
 *
 * @property value 공개 ID 문자열. 공백일 수 없고 [MAX_LENGTH]자를 넘을 수 없다.
 */
@JvmInline
value class PaymentCustomerId(
	val value: String,
) {
	init {
		require(value.isNotBlank()) { "PaymentCustomerId는 공백일 수 없습니다." }
		require(value.length <= MAX_LENGTH) {
			"PaymentCustomerId는 ${MAX_LENGTH}자를 초과할 수 없습니다: $value"
		}
	}

	companion object {
		const val PREFIX = "pcu_"

		/** `payment_customer.payment_customer_id` 컬럼의 최대 길이(`VARCHAR(50)`)와 동일하다. */
		private const val MAX_LENGTH = 50
	}
}
