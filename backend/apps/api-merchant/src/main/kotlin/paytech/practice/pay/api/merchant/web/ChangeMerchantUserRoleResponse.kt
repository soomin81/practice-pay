package paytech.practice.pay.api.merchant.web

import java.time.Instant

/** `POST /merchant/merchant-users/{id}/role`의 응답 본문이다. */
data class ChangeMerchantUserRoleResponse(
	val merchantUserId: String,
	val role: String,
	val changedAt: Instant,
)
