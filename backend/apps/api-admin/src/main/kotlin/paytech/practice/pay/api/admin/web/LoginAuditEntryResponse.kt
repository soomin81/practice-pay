package paytech.practice.pay.api.admin.web

import java.time.Instant

/**
 * `GET /admin/login-audit` 응답의 항목 하나다
 * ([InternalLoginAuditEntry][paytech.practice.pay.application.port.outbound.InternalLoginAuditEntry]를 옮긴다).
 *
 * `internalUserId`/`userName`은 **없는 로그인 아이디로의 시도면 `null`이다** — 그 경우
 * `attemptedLoginId`만 남는다(존재하지 않는 계정에 대한 시도).
 */
data class LoginAuditEntryResponse(
	val auditId: String,
	val internalUserId: String?,
	val attemptedLoginId: String,
	val userName: String?,
	val outcome: String,
	val clientIp: String?,
	val occurredAt: Instant,
)
