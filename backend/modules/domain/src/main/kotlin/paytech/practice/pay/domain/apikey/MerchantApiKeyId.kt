package paytech.practice.pay.domain.apikey

/**
 * 가맹점 API Key를 식별하는 공개(외부 노출용) ID를 표현하는 Value Object다.
 *
 * DB의 `merchant_api_key.merchant_api_key_id` 컬럼(`VARCHAR(50)`, `UNIQUE`)과
 * 대응하며, 내부 전용 PK인 `merchant_api_key_seq`(`BIGINT AUTO_INCREMENT`)와는
 * 별개의 값이다. 화면에 노출되는 식별자는 이 ID가 아니라 [ApiKeyPrefix]다.
 *
 * @property value API Key 공개 ID 문자열. 공백일 수 없고 [MAX_LENGTH]자를 넘을 수 없다.
 */
@JvmInline
value class MerchantApiKeyId(
	val value: String,
) {
	init {
		require(value.isNotBlank()) { "MerchantApiKeyId는 공백일 수 없습니다." }
		require(value.length <= MAX_LENGTH) {
			"MerchantApiKeyId는 ${MAX_LENGTH}자를 초과할 수 없습니다: $value"
		}
	}

	companion object {
		/** `merchant_api_key.merchant_api_key_id` 컬럼의 최대 길이(`VARCHAR(50)`)와 동일하게 맞춘 값이다. */
		private const val MAX_LENGTH = 50
	}
}
