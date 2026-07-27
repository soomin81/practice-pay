package paytech.practice.pay.api.admin.web

import java.time.Instant

/** `POST /admin/merchants/{merchantId}/users/{id}/role`의 응답 본문이다. */
data class ChangeMerchantUserRoleResponse(
	val merchantUserId: String,
	val role: String,
	val changedAt: Instant,
)
