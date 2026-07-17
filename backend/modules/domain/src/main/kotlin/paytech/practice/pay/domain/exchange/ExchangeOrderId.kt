package paytech.practice.pay.domain.exchange

/**
 * 거래소 주문을 식별하는 공개(외부 노출용) ID를 표현하는 Value Object다.
 *
 * DB의 `exchange_order.exchange_order_id` 컬럼(`VARCHAR(50)`, `UNIQUE`)과
 * 대응하며, 내부 전용 PK인 `exchange_order_seq`(`BIGINT AUTO_INCREMENT`)와는
 * 별개의 값이다. 결제 생성 멱등성에 쓰이는 [ClientOrderId]와는 다른 개념이다.
 *
 * @property value 거래소 주문 공개 ID 문자열. 공백일 수 없고 [MAX_LENGTH]자를 넘을 수 없다.
 */
@JvmInline
value class ExchangeOrderId(
	val value: String,
) {
	init {
		require(value.isNotBlank()) { "ExchangeOrderId는 공백일 수 없습니다." }
		require(value.length <= MAX_LENGTH) { "ExchangeOrderId는 ${MAX_LENGTH}자를 초과할 수 없습니다: $value" }
	}

	companion object {
		/** `exchange_order.exchange_order_id` 컬럼의 최대 길이(`VARCHAR(50)`)와 동일하게 맞춘 값이다. */
		private const val MAX_LENGTH = 50
	}
}
