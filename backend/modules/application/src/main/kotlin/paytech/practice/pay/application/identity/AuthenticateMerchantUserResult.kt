package paytech.practice.pay.application.identity

import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.identity.MerchantUserRole
import paytech.practice.pay.domain.merchant.MerchantId

/**
 * [AuthenticateMerchantUserUseCase]의 결과다. [AuthenticateInternalUserResult]와
 * 같은 이유로 세션/토큰은 다루지 않는다 — 인증된 신원만 돌려준다.
 */
data class AuthenticateMerchantUserResult(
	val merchantUserId: MerchantUserId,
	val merchantId: MerchantId,
	val loginId: LoginId,
	val userName: String,
	val role: MerchantUserRole,
)
