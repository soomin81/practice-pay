package paytech.practice.pay.api.merchant.web

import jakarta.validation.constraints.NotBlank

/**
 * `POST /merchant/merchant-users/{id}/role`의 요청 본문이다. [role]은
 * [MerchantUserRole][paytech.practice.pay.domain.identity.MerchantUserRole]의 이름
 * (`ADMIN`/`VIEWER`)이어야 한다 — `OWNER`를 보내면 도메인
 * [changeRole][paytech.practice.pay.domain.identity.MerchantUser.changeRole]의 `require`가
 * 400으로 처리된다(하위 계정 발급과 같은 제약).
 */
data class ChangeMerchantUserRoleRequest(
	@field:NotBlank
	val role: String,
)
