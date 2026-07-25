package paytech.practice.pay.api.admin.web

import java.time.Instant

/** `POST /admin/internal-users/{id}/role`의 응답 본문이다. */
data class ChangeInternalUserRoleResponse(
	val internalUserId: String,
	val role: String,
	val changedAt: Instant,
)
