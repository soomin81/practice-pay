package paytech.practice.pay.application.identity

import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.identity.MerchantUserRole

/**
 * [ChangeMerchantUserRoleUseCase]의 입력이다. [ChangeMerchantUserStatusCommand]와 같은
 * 이유로 대상 가맹점을 받지 않는다.
 */
data class ChangeMerchantUserRoleCommand(
	val targetMerchantUserId: MerchantUserId,
	val newRole: MerchantUserRole,
	val requestedByMerchantUserId: MerchantUserId,
)
