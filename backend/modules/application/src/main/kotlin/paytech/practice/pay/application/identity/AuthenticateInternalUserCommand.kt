package paytech.practice.pay.application.identity

import paytech.practice.pay.domain.identity.LoginId

/**
 * [AuthenticateInternalUserUseCase]의 입력이다.
 *
 * @property loginId 로그인 아이디.
 * @property password 원문 비밀번호. 이 계층을 넘어가면 안 되고, [AuthenticateInternalUserUseCase]가
 * [paytech.practice.pay.application.port.outbound.PasswordEncoder]로 검증한 뒤 버린다 — 저장되거나 로그에 남지 않는다.
 * @property clientIp 요청의 원격 주소. 감사 로그에만 남기고 인증 판단에는 쓰지 않는다.
 * inbound Adapter가 채우며(`HttpServletRequest.remoteAddr`), 없으면 `null`이다 —
 * 기본값이 있어 IP를 넘기지 않는 호출부(테스트 등)도 그대로 동작한다.
 */
data class AuthenticateInternalUserCommand(
	val loginId: LoginId,
	val password: String,
	val clientIp: String? = null,
)
