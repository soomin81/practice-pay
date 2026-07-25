package paytech.practice.pay.api.merchant.web

import java.time.Instant

/** `POST /merchant/merchant-users/{id}/suspend|reactivate|terminate`의 공통 응답 본문이다. */
data class ChangeMerchantUserStatusResponse(
	val merchantUserId: String,
	val status: String,
	val changedAt: Instant,
)
