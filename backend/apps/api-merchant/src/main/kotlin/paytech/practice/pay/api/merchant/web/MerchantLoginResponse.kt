package paytech.practice.pay.api.merchant.web

/** `POST /merchant/login`의 응답 본문이다. 세션은 응답 헤더의 `Set-Cookie`로 전달되고, 본문에는 신원 정보만 담는다. */
data class MerchantLoginResponse(
	val merchantUserId: String,
	val merchantId: String,
	val loginId: String,
	val userName: String,
	val role: String,
)
