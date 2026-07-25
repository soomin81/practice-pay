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
	/**
	 * 유효한(`PENDING`) 초대의 만료 시각. 없으면 `null`이다 — `INVITED` 사용자가 왜 아직
	 * 활성화되지 않았는지를 화면이 판단하는 근거다(`null`=초대 없음/취소됨, 과거=만료됨).
	 */
	val pendingInvitationExpiresAt: Instant?,
)
