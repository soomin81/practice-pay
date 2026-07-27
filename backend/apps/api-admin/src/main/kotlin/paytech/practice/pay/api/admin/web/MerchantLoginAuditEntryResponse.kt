package paytech.practice.pay.api.admin.web

import java.time.Instant

/**
 * `GET /admin/merchant-login-audit` 응답의 항목 하나다
 * ([MerchantLoginAuditEntry][paytech.practice.pay.application.port.outbound.MerchantLoginAuditEntry]를 옮긴다).
 *
 * `merchantId`/`merchantName`은 **없는 merchantCode로의 시도면 `null`**이고, `userName`은
 * **없는 loginId로의 시도면 `null`**이다 — 그 경우 `attemptedMerchantCode`/`attemptedLoginId`만 남는다.
 */
data class MerchantLoginAuditEntryResponse(
	val auditId: String,
	val merchantId: String?,
	val merchantName: String?,
	val attemptedMerchantCode: String,
	val attemptedLoginId: String,
	val userName: String?,
	val outcome: String,
	val clientIp: String?,
	val occurredAt: Instant,
)
