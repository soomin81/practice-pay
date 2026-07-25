package paytech.practice.pay.api.admin.web

import java.time.Instant

/**
 * `GET /admin/internal-users` 응답의 항목 하나다.
 * [InternalUserSummary][paytech.practice.pay.application.port.outbound.InternalUserSummary]를
 * 그대로 옮기되, `passwordHash`는 애초에 그 읽기 모델에도 없다(Projection 단계에서 제외).
 */
data class InternalUserSummaryResponse(
	val internalUserId: String,
	val loginId: String,
	val email: String,
	val userName: String,
	val role: String,
	val status: String,
	val lastLoginAt: Instant?,
	val createdAt: Instant,
)
