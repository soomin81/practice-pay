package paytech.practice.pay.application.apikey

import paytech.practice.pay.domain.apikey.ApiEnvironment
import paytech.practice.pay.domain.apikey.ApiKeyPrefix
import paytech.practice.pay.domain.apikey.ApiKeyScope
import paytech.practice.pay.domain.apikey.MerchantApiKeyId
import java.time.Instant

/**
 * [IssueMerchantApiKeyUseCase]의 결과다.
 *
 * @property rawApiKey 전체 API Key **원문**(`sk_test_<prefix>_<secret>`)이다 —
 * DB에는 이 값을 해시한 `secretHash`만 저장돼 있고, 이 결과가 반환된 뒤에는
 * 다시 얻을 방법이 없다(`docs/architecture/identity-access-api-key.md`의
 * "6.4 저장 정책": "API Key 원문은 생성 시 최초 한 번만 표시한다"). 호출부는 이
 * 값을 즉시 호출자에게 보여주고 저장하지 않아야 한다.
 */
data class IssueMerchantApiKeyResult(
	val merchantApiKeyId: MerchantApiKeyId,
	val keyName: String,
	val environment: ApiEnvironment,
	val keyPrefix: ApiKeyPrefix,
	val scopes: Set<ApiKeyScope>,
	val rawApiKey: String,
	val createdAt: Instant,
)
