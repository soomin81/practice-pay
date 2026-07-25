package paytech.practice.pay.api.admin.web

import java.time.Instant

/** `POST /admin/internal-users/{id}/suspend|reactivate|terminate`의 공통 응답 본문이다. */
data class ChangeInternalUserStatusResponse(
	val internalUserId: String,
	val status: String,
	val changedAt: Instant,
)
