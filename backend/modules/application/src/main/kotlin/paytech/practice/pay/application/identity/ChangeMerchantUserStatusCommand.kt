package paytech.practice.pay.application.identity

import paytech.practice.pay.domain.identity.MerchantUserId

/**
 * [ChangeMerchantUserStatusUseCase]의 입력이다.
 *
 * **대상 가맹점(`merchantId`)을 받지 않는다** — 요청자 자신의 소속 가맹점으로 고정되고,
 * 대상이 그 가맹점 소속이 아니면 "없음"으로 거부한다(`ListMerchantUsersCommand`와 같은
 * 멀티테넌시 방어).
 */
data class ChangeMerchantUserStatusCommand(
	val targetMerchantUserId: MerchantUserId,
	val action: MerchantUserStatusAction,
	val requestedByMerchantUserId: MerchantUserId,
)
