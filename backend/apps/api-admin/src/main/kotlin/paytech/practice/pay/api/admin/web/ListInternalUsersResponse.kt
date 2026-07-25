package paytech.practice.pay.api.admin.web

/** `GET /admin/internal-users`의 응답 본문이다. */
data class ListInternalUsersResponse(
	val internalUsers: List<InternalUserSummaryResponse>,
)
