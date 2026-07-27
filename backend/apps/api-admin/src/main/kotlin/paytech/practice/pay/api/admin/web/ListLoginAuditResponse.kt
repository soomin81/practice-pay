package paytech.practice.pay.api.admin.web

/** `GET /admin/login-audit`의 응답 본문이다(최근 로그인 감사 기록, 최신순). */
data class ListLoginAuditResponse(
	val entries: List<LoginAuditEntryResponse>,
)
