package paytech.practice.pay.api.admin.security

import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.InternalUserRole
import paytech.practice.pay.domain.identity.LoginId

/**
 * `AdminLoginController`가 로그인에 성공하면 `Authentication.principal`로 심는
 * 값이다. 컨트롤러는 `@AuthenticationPrincipal InternalUserPrincipal`로 바로
 * 받는다 — `internalUserId`를 다시 조회하지 않고 세션에서 바로 가져온다
 * (`apps:api-payment`의 `ApiKeyPrincipal`과 같은 이유·같은 모양).
 */
data class InternalUserPrincipal(
	val internalUserId: InternalUserId,
	val loginId: LoginId,
	val role: InternalUserRole,
)
