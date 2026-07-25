package paytech.practice.pay.api.admin.web

/**
 * `GET /admin/me`의 응답 본문이다 — 프론트엔드(내부 운영자 콘솔)가 새로고침 후 현재
 * 세션의 로그인 사용자를 복원할 때 쓴다.
 *
 * 필드는 [paytech.practice.pay.api.admin.security.InternalUserPrincipal]이 들고 있는 값
 * 그대로다. `userName`은 principal에 없어 이 슬라이스에서는 뺐다(콘솔 헤더는
 * `loginId`+`role`로 표시한다) — `apps:api-merchant`의 `MerchantMeResponse`와 같은 단순화다.
 */
data class AdminMeResponse(
	val internalUserId: String,
	val loginId: String,
	val role: String,
)
