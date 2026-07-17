package paytech.practice.pay.application.identity

import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.InternalUserRole
import paytech.practice.pay.domain.identity.LoginId

/**
 * [AuthenticateInternalUserUseCase]의 결과다.
 *
 * 세션/토큰 발급은 이 Use Case의 책임이 아니다 — 로그인한 신원만 돌려주고, 그
 * 신원으로 실제 세션을 어떻게 유지할지(Spring Security 세션 쿠키 등)는 inbound
 * Adapter가 정한다(`apps/api-admin`의 `AdminLoginController` 참고).
 */
data class AuthenticateInternalUserResult(
	val internalUserId: InternalUserId,
	val loginId: LoginId,
	val userName: String,
	val role: InternalUserRole,
)
