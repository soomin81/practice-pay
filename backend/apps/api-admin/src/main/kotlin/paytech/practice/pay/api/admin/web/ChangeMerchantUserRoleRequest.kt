package paytech.practice.pay.api.admin.web

import jakarta.validation.constraints.NotBlank

/**
 * `POST /admin/merchants/{merchantId}/users/{id}/role`의 요청 본문이다. [role]은
 * [MerchantUserRole][paytech.practice.pay.domain.identity.MerchantUserRole]의 이름
 * (`ADMIN`/`VIEWER`)이어야 한다 — `OWNER`를 보내면 도메인
 * [changeRole][paytech.practice.pay.domain.identity.MerchantUser.changeRole]의 `require`가
 * 400으로 처리된다.
 */
data class ChangeMerchantUserRoleRequest(
	@field:NotBlank
	val role: String,
)
