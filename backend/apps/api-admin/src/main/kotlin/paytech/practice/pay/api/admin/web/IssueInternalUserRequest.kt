package paytech.practice.pay.api.admin.web

import jakarta.validation.constraints.NotBlank

/**
 * `POST /admin/internal-users`의 요청 본문이다(`docs/architecture/identity-access-api-key.md`의
 * "3.3 발급 정책"). [role]은 [paytech.practice.pay.domain.identity.InternalUserRole]의
 * 이름(`SUPER_ADMIN`/`OPERATOR`/`VIEWER`) 문자열이어야 한다 — 잘못된 값은
 * `InternalUserRole.valueOf`가 던지는 `IllegalArgumentException`을 통해
 * [AdminApiExceptionHandler]가 400으로 변환한다.
 */
data class IssueInternalUserRequest(
	@field:NotBlank
	val loginId: String,
	@field:NotBlank
	val email: String,
	@field:NotBlank
	val userName: String,
	@field:NotBlank
	val role: String,
)
