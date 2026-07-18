package paytech.practice.pay.api.merchant.web

import java.time.Instant

/** `POST /merchant/account-invitations/accept`의 응답 본문이다. */
data class AcceptAccountInvitationResponse(
	val loginId: String,
	val activatedAt: Instant,
)
