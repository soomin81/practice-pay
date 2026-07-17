package paytech.practice.pay.api.admin.web

import jakarta.validation.constraints.NotBlank

/** `POST /admin/login`의 요청 본문이다(`docs/architecture/identity-access-api-key.md`의 "3.4 로그인 경로"). */
data class AdminLoginRequest(
	@field:NotBlank
	val loginId: String,
	@field:NotBlank
	val password: String,
)
