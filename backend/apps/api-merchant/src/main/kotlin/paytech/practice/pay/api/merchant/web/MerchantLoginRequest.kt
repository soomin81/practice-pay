package paytech.practice.pay.api.merchant.web

import jakarta.validation.constraints.NotBlank

/**
 * `POST /merchant/login`의 요청 본문이다(`docs/architecture/identity-access-api-key.md`의
 * "4.5 로그인 경로").
 *
 * [merchantCode]가 필요한 이유는 `AuthenticateMerchantUserCommand`의 KDoc 참고 —
 * `login_id`가 가맹점 안에서만 유일해서 어느 가맹점인지부터 밝혀야 한다.
 */
data class MerchantLoginRequest(
	@field:NotBlank
	val merchantCode: String,
	@field:NotBlank
	val loginId: String,
	@field:NotBlank
	val password: String,
)
