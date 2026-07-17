package paytech.practice.pay.application.identity

import paytech.practice.pay.domain.identity.LoginId

/**
 * [AuthenticateInternalUserUseCase]의 입력이다.
 *
 * @property loginId 로그인 아이디.
 * @property password 원문 비밀번호. 이 계층을 넘어가면 안 되고, [AuthenticateInternalUserUseCase]가
 * [paytech.practice.pay.application.port.outbound.PasswordEncoder]로 검증한 뒤 버린다 — 저장되거나 로그에 남지 않는다.
 */
data class AuthenticateInternalUserCommand(
	val loginId: LoginId,
	val password: String,
)
