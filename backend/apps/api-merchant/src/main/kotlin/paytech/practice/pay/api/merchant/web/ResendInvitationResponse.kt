package paytech.practice.pay.api.merchant.web

import java.time.Instant

/**
 * `POST /merchant/merchant-users/{id}/invitation/resend`의 응답 본문이다.
 *
 * [invitationToken]은 이 응답에서만 원문으로 보인다(최초 발급과 같은 규칙) — **이전
 * 초대 링크는 이 시점에 무효가 된다.**
 */
data class ResendInvitationResponse(
	val merchantUserId: String,
	val invitationToken: String,
	val invitationExpiresAt: Instant,
)
