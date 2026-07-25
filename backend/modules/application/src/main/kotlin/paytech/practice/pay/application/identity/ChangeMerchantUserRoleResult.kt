package paytech.practice.pay.application.identity

import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.identity.MerchantUserRole
import java.time.Instant

/** [ChangeMerchantUserRoleUseCase]의 출력이다. */
data class ChangeMerchantUserRoleResult(
	val merchantUserId: MerchantUserId,
	val role: MerchantUserRole,
	val changedAt: Instant,
)
