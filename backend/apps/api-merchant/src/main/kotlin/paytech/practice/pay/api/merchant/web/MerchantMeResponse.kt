package paytech.practice.pay.api.merchant.web

/**
 * `GET /merchant/me`의 응답 본문이다 — 프론트엔드(가맹점 콘솔)가 새로고침 후 현재
 * 세션의 로그인 사용자를 복원할 때 쓴다.
 *
 * 필드는 지금 [paytech.practice.pay.api.merchant.security.MerchantUserPrincipal]이
 * 들고 있는 값 그대로다. `userName`/`merchantCode`는 principal에 없어 이 슬라이스에서는
 * 뺐다(콘솔 헤더는 `loginId`+`role`로 표시). 필요해지면 로그인 흐름에서 principal을
 * 확장하는데, 그 단순화는 `MerchantMeController`의 KDoc에 표시해 뒀다.
 */
data class MerchantMeResponse(
	val merchantUserId: String,
	val merchantId: String,
	val loginId: String,
	val role: String,
)
