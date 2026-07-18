package paytech.practice.pay.api.merchant.web

import jakarta.validation.constraints.NotBlank

/**
 * `POST /merchant/account-invitations/accept`의 요청 본문이다(`apps:api-admin`의
 * `AcceptAccountInvitationRequest`와 같은 이유·같은 모양 — Token을 URL 경로가
 * 아니라 요청 본문으로 받는다).
 */
data class AcceptAccountInvitationRequest(
	@field:NotBlank
	val invitationToken: String,
	@field:NotBlank
	val newPassword: String,
)
