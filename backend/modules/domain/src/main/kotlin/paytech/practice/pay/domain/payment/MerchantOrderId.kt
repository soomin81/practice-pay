package paytech.practice.pay.domain.payment

/**
 * 가맹점이 자신의 시스템에서 부여한 주문 식별자를 표현하는 Value Object다.
 *
 * DB의 `payment.merchant_order_id` 컬럼(`VARCHAR(100)`)과 대응하며,
 * `(merchant_seq, merchant_order_id)` 조합의 Unique 제약으로 결제 생성의 멱등성을
 * 보장하는 데 쓰인다(`docs/database/database-design.md`의 "주요 Unique" 참고).
 *
 * @property value 가맹점 주문 식별자 문자열. 공백일 수 없고 [MAX_LENGTH]자를 넘을 수 없다.
 */
@JvmInline
value class MerchantOrderId(
	val value: String,
) {
	init {
		require(value.isNotBlank()) { "MerchantOrderId는 공백일 수 없습니다." }
		require(value.length <= MAX_LENGTH) { "MerchantOrderId는 ${MAX_LENGTH}자를 초과할 수 없습니다: $value" }
	}

	companion object {
		/** `payment.merchant_order_id` 컬럼의 최대 길이(`VARCHAR(100)`)와 동일하게 맞춘 값이다. */
		private const val MAX_LENGTH = 100
	}
}
