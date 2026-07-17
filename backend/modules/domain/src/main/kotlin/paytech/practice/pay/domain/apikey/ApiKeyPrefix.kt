package paytech.practice.pay.domain.apikey

/**
 * API Key 후보를 빠르게 조회하고 관리자 화면에서 Key를 식별하기 위한 공개
 * 부분을 표현하는 Value Object다(예: `sk_test_ab12cd34`).
 *
 * DB의 `merchant_api_key.key_prefix` 컬럼(`VARCHAR(50)`, `UNIQUE`, 전체
 * 시스템에서 유일)과 대응한다. 전체 API Key(Secret 포함) 원문은 이 타입도,
 * 도메인 계층 어디에서도 다루지 않는다 — 저장은 이 Prefix와
 * [MerchantApiKey.secretHash]로만 한다(`docs/architecture/identity-access-api-key.md`
 * 의 "저장 정책" 참고).
 *
 * @property value API Key Prefix 문자열. 공백일 수 없고 [MAX_LENGTH]자를 넘을 수 없다.
 */
@JvmInline
value class ApiKeyPrefix(val value: String) {

	init {
		require(value.isNotBlank()) { "ApiKeyPrefix는 공백일 수 없습니다." }
		require(value.length <= MAX_LENGTH) { "ApiKeyPrefix는 ${MAX_LENGTH}자를 초과할 수 없습니다: $value" }
	}

	companion object {
		/** `merchant_api_key.key_prefix` 컬럼의 최대 길이(`VARCHAR(50)`)와 동일하게 맞춘 값이다. */
		private const val MAX_LENGTH = 50
	}
}
