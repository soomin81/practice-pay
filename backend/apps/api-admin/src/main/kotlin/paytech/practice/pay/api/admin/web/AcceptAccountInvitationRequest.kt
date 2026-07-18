package paytech.practice.pay.api.admin.web

import jakarta.validation.constraints.NotBlank

/**
 * `POST /admin/account-invitations/accept`의 요청 본문이다. [invitationToken]을
 * URL 경로가 아니라 요청 본문으로 받는다 — 접근 로그에 민감한 Token 원문이
 * 남지 않게 하려는 의도적 선택이다(`docs/architecture/identity-access-api-key.md`의
 * "6.4 저장 정책"이 API Key 원문 노출을 최소화하는 것과 같은 정신).
 */
data class AcceptAccountInvitationRequest(
	@field:NotBlank
	val invitationToken: String,
	@field:NotBlank
	val newPassword: String,
)
