package paytech.practice.pay.api.merchant.web

import jakarta.validation.constraints.NotBlank

/**
 * `POST /merchant/merchant-users`의 요청 본문이다(`docs/architecture/identity-access-api-key.md`의
 * "4.4 하위 계정 발급"). [role]은 [paytech.practice.pay.domain.identity.MerchantUserRole]의
 * 이름(`ADMIN`/`VIEWER`) 문자열이어야 한다 — `OWNER`를 보내면
 * `MerchantUser.inviteSubAccount`의 `require`가 던지는 `IllegalArgumentException`을
 * 통해 [MerchantApiExceptionHandler]가 400으로 변환한다. `merchantId`는 받지
 * 않는다 — 항상 호출자 자신의 소속 가맹점에 만들어진다(`MerchantUserPrincipal`의
 * KDoc 참고).
 */
data class InviteMerchantSubAccountRequest(
	@field:NotBlank
	val loginId: String,
	@field:NotBlank
	val email: String,
	@field:NotBlank
	val userName: String,
	@field:NotBlank
	val role: String,
)
