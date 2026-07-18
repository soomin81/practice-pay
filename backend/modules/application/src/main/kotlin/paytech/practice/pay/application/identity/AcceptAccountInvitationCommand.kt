package paytech.practice.pay.application.identity

import paytech.practice.pay.domain.identity.InvitationAccountType

/**
 * [AcceptAccountInvitationUseCase]의 입력이다.
 *
 * @property invitationToken 초대 발급 응답에서 한 번만 보였던 원문 Token이다.
 * @property newPassword 대상 계정에 설정할 비밀번호 원문이다.
 * @property expectedAccountType 호출한 앱이 기대하는 계정 유형이다 — `api-admin`은
 * [InvitationAccountType.INTERNAL_USER], `api-merchant`는
 * [InvitationAccountType.MERCHANT_USER]로 고정해서 호출한다. 실제
 * `AccountInvitation.accountType`과 다르면 다른 앱 경계의 초대를 잘못 제출한 것으로
 * 보고 거부한다.
 */
data class AcceptAccountInvitationCommand(
	val invitationToken: String,
	val newPassword: String,
	val expectedAccountType: InvitationAccountType,
)
