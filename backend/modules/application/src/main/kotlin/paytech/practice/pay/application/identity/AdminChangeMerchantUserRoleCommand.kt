package paytech.practice.pay.application.identity

import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.identity.MerchantUserRole
import paytech.practice.pay.domain.merchant.MerchantId

/**
 * [AdminChangeMerchantUserRoleUseCase]의 입력이다. [AdminChangeMerchantUserStatusCommand]와
 * 같은 이유로 대상 가맹점을 직접 받고 요청자 식별자는 받지 않는다.
 */
data class AdminChangeMerchantUserRoleCommand(
	val merchantId: MerchantId,
	val targetMerchantUserId: MerchantUserId,
	val newRole: MerchantUserRole,
)
