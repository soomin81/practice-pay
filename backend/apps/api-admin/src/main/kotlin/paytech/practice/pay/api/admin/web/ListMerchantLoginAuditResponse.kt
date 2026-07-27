package paytech.practice.pay.api.admin.web

/** `GET /admin/merchant-login-audit`의 응답 본문이다(최근 가맹점 로그인 감사 기록, 최신순). */
data class ListMerchantLoginAuditResponse(
	val entries: List<MerchantLoginAuditEntryResponse>,
)
