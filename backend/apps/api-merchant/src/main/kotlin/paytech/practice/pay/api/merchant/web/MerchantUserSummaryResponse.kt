package paytech.practice.pay.api.merchant.web

import java.time.Instant

/**
 * `GET /merchant/merchant-users` 응답의 항목 하나다.
 * [MerchantUserSummary][paytech.practice.pay.application.port.outbound.MerchantUserSummary]를
 * 그대로 옮기되, `passwordHash`는 애초에 그 읽기 모델에도 없다 — Projection 단계에서부터
 * 제외했다(`MerchantApiKeySummaryResponse`가 Secret 관련 필드를 갖지 않는 것과 같은 이유).
 */
data class MerchantUserSummaryResponse(
	val merchantUserId: String,
	val loginId: String,
	val email: String,
	val userName: String,
	val role: String,
	val status: String,
	val lastLoginAt: Instant?,
	val createdAt: Instant,
)
