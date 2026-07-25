package paytech.practice.pay.application.identity

import paytech.practice.pay.domain.identity.MerchantUserId

/** [RevokeMerchantUserInvitationUseCase]의 입력이다. 대상 가맹점은 요청자 소속으로 고정된다. */
data class RevokeMerchantUserInvitationCommand(
	val targetMerchantUserId: MerchantUserId,
	val requestedByMerchantUserId: MerchantUserId,
)
