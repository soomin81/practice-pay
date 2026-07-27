package paytech.practice.pay.api.admin.web

/** `GET /admin/merchants/{merchantId}/users`의 응답 본문이다. */
data class AdminListMerchantUsersResponse(
	val merchantUsers: List<AdminMerchantUserSummaryResponse>,
)
