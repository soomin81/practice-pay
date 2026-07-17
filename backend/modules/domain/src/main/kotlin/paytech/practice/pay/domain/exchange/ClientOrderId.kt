package paytech.practice.pay.domain.exchange

/**
 * 거래소에 주문을 요청할 때 우리 시스템이 부여하는 멱등성 키를 표현하는
 * Value Object다.
 *
 * DB의 `exchange_order.client_order_id` 컬럼(`VARCHAR(100)`, `UNIQUE`)과
 * 대응하며, 거래소 주문 요청의 멱등성을 보장하는 데 쓰인다
 * (`docs/database/database-design.md`의 "주요 Unique" 참고).
 *
 * @property value 클라이언트 주문 ID 문자열. 공백일 수 없고 [MAX_LENGTH]자를 넘을 수 없다.
 */
@JvmInline
value class ClientOrderId(
	val value: String,
) {
	init {
		require(value.isNotBlank()) { "ClientOrderId는 공백일 수 없습니다." }
		require(value.length <= MAX_LENGTH) { "ClientOrderId는 ${MAX_LENGTH}자를 초과할 수 없습니다: $value" }
	}

	companion object {
		/** `exchange_order.client_order_id` 컬럼의 최대 길이(`VARCHAR(100)`)와 동일하게 맞춘 값이다. */
		private const val MAX_LENGTH = 100
	}
}
