package paytech.practice.pay.api.admin.web

import java.time.Instant

/**
 * `GET /admin/merchants/{merchantId}/users` 응답의 항목 하나다.
 * [MerchantUserSummary][paytech.practice.pay.application.port.outbound.MerchantUserSummary]를
 * 그대로 옮기되, `passwordHash`는 애초에 그 읽기 모델에도 없다(Projection 단계에서 제외).
 *
 * `pendingInvitationExpiresAt`은 `INVITED` 사용자가 왜 아직 활성화되지 않았는지를 명부에서
 * 바로 알기 위한 값이다(가맹점 콘솔의 같은 필드와 같은 의미) — 만료는 화면이 현재와 비교해
 * 판단한다.
 */
data class AdminMerchantUserSummaryResponse(
	val merchantUserId: String,
	val loginId: String,
	val email: String,
	val userName: String,
	val role: String,
	val status: String,
	val lastLoginAt: Instant?,
	val createdAt: Instant,
	val pendingInvitationExpiresAt: Instant?,
)
