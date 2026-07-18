package paytech.practice.pay.api.merchant.security

import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.identity.MerchantUserRole
import paytech.practice.pay.domain.merchant.MerchantId

/**
 * `MerchantLoginController`가 로그인에 성공하면 `Authentication.principal`로 심는
 * 값이다. 컨트롤러는 `@AuthenticationPrincipal MerchantUserPrincipal`로 바로
 * 받는다 — `apps:api-admin`의 `InternalUserPrincipal`과 같은 이유·같은 모양이다.
 *
 * [merchantId]가 특히 중요하다 — `InviteMerchantSubAccountUseCase`가 하위 계정을
 * 어느 가맹점에 만들지는 항상 이 값에서만 가져온다(요청 본문으로 받지 않는다).
 * 그렇지 않으면 호출자가 다른 가맹점의 `merchantId`를 요청에 실어 보내 남의
 * 가맹점에 계정을 만들 수 있는 멀티테넌시 취약점이 생긴다.
 */
data class MerchantUserPrincipal(
	val merchantUserId: MerchantUserId,
	val merchantId: MerchantId,
	val loginId: LoginId,
	val role: MerchantUserRole,
)
