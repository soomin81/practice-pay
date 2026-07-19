package paytech.practice.pay.api.merchant.web

import java.time.Instant

/**
 * `GET /merchant/api-keys` 응답의 항목 하나다. [MerchantApiKeySummary]를 그대로
 * 옮기되, Secret 관련 필드(`secretHash`/`hashAlgorithm`)는 애초에
 * [MerchantApiKeySummary]에도 없다 — Projection 단계에서부터 제외했다
 * (`docs/`의 "6.4 저장 정책": API Key 원문은 발급 응답에서만 보인다).
 */
data class MerchantApiKeySummaryResponse(
	val merchantApiKeyId: String,
	val keyName: String,
	val environment: String,
	val keyPrefix: String,
	val scopes: List<String>,
	val status: String,
	val createdAt: Instant,
	val lastUsedAt: Instant?,
	val revokedAt: Instant?,
)
