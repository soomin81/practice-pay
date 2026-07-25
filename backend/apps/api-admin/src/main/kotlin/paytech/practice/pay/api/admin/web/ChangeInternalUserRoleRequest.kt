package paytech.practice.pay.api.admin.web

import jakarta.validation.constraints.NotBlank

/**
 * `POST /admin/internal-users/{id}/role`의 요청 본문이다. [role]은
 * [InternalUserRole][paytech.practice.pay.domain.identity.InternalUserRole]의 이름
 * (`OPERATOR`/`VIEWER`)이어야 한다 — `SUPER_ADMIN`을 보내면 도메인
 * [changeRole][paytech.practice.pay.domain.identity.InternalUser.changeRole]의 `require`가
 * 400으로 처리된다(초대 발급과 같은 제약).
 */
data class ChangeInternalUserRoleRequest(
	@field:NotBlank
	val role: String,
)
