package paytech.practice.pay.api.merchant.web

import java.time.Instant

/**
 * `POST /merchant/api-keys`의 응답 본문이다.
 *
 * [rawApiKey]는 이 응답에서만 원문으로 보인다 — DB에는 Hash만 저장되고 다시
 * 조회할 방법이 없다(`docs/`의 "6.4 저장 정책": "API Key 원문은 생성 시 최초
 * 한 번만 표시한다"). 호출한 `OWNER`/`ADMIN`이 이 값을 즉시 가맹점 서버에
 * 전달해야 한다.
 */
data class IssueMerchantApiKeyResponse(
	val merchantApiKeyId: String,
	val keyName: String,
	val environment: String,
	val keyPrefix: String,
	val scopes: List<String>,
	val rawApiKey: String,
	val createdAt: Instant,
)
