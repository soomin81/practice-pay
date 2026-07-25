package paytech.practice.pay.api.merchant.web

import java.time.Instant

/**
 * `POST /merchant/merchant-users/{id}/invitation/revoke`의 응답 본문이다.
 * 계정 상태는 바뀌지 않는다(`INVITED` 그대로) — 초대 토큰만 무효화된다.
 */
data class RevokeInvitationResponse(
	val merchantUserId: String,
	val revokedAt: Instant,
)
