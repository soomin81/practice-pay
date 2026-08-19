package paytech.practice.pay.domain.customer

/**
 * 구매자 이름을 표현하는 Value Object다.
 *
 * DB의 `payment_customer.customer_name_encrypted`(암호문)와 `customer_name_masked`(마스킹)에
 * 대응한다 — **이 타입이 들고 있는 것은 언제나 평문이다.** 암복호는 어댑터 경계에서만
 * 일어난다(도메인은 인프라를 모른다).
 *
 * **[masked]가 도메인에 있는 이유**: 마스킹 규칙은 표현 형식이 아니라 업무 규칙이다. 두
 * 콘솔과 엑셀이 각자 마스킹하면 규칙이 갈리고, 갈린 것을 나중에 발견하기 어렵다(ADR-008).
 *
 * @property value 이름 평문. 공백일 수 없고 [MAX_LENGTH]자를 넘을 수 없다.
 */
@JvmInline
value class CustomerName(
	val value: String,
) {
	init {
		require(value.isNotBlank()) { "구매자 이름은 공백일 수 없습니다." }
		require(value.length <= MAX_LENGTH) { "구매자 이름은 ${MAX_LENGTH}자를 초과할 수 없습니다." }
	}

	/**
	 * 가운데를 가린 이름(`홍길동` → `홍*동`, `홍길순희` → `홍**희`).
	 *
	 * **두 글자는 가운데가 없어 뒷글자를 가린다**(`홍길` → `홍*`). 한 글자는 가릴 것이 없어
	 * 그대로 둔다 — 한 글자를 통째로 `*`로 만들면 이름이 있었다는 사실까지 사라져서, 값이
	 * 비어 있는 것과 구분되지 않는다.
	 */
	val masked: String
		get() =
			when (value.length) {
				1 -> value
				2 -> value.first() + "*"
				else -> value.first() + "*".repeat(value.length - 2) + value.last()
			}

	companion object {
		/** `payment_customer.customer_name_masked` 컬럼의 최대 길이(`VARCHAR(100)`)를 넘지 않게 잡았다. */
		private const val MAX_LENGTH = 100
	}
}
