package paytech.practice.pay.api.merchant.web

/** `GET /merchant/merchant-users`의 응답 본문이다. */
data class ListMerchantUsersResponse(
	val merchantUsers: List<MerchantUserSummaryResponse>,
)
