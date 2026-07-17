package paytech.practice.pay.domain.merchant

/**
 * 가맹점의 비즈니스 코드를 표현하는 Value Object다.
 *
 * DB의 `merchant.merchant_code` 컬럼(`VARCHAR(50)`, `UNIQUE`)과 대응한다.
 * 외부 노출용 공개 ID인 [MerchantId]와는 별개의 값으로, 내부 운영/정산 등에서
 * 쓰는 사람이 읽기 좋은 식별 코드다.
 *
 * @property value 가맹점 코드 문자열. 공백일 수 없고 [MAX_LENGTH]자를 넘을 수 없다.
 */
@JvmInline
value class MerchantCode(
	val value: String,
) {
	init {
		require(value.isNotBlank()) { "MerchantCode는 공백일 수 없습니다." }
		require(value.length <= MAX_LENGTH) { "MerchantCode는 ${MAX_LENGTH}자를 초과할 수 없습니다: $value" }
	}

	companion object {
		/** `merchant.merchant_code` 컬럼의 최대 길이(`VARCHAR(50)`)와 동일하게 맞춘 값이다. */
		private const val MAX_LENGTH = 50
	}
}
