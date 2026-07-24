package paytech.practice.pay.application.identity

import paytech.practice.pay.application.port.outbound.MerchantUserSummary

/** [ListMerchantUsersUseCase]의 출력이다. */
data class ListMerchantUsersResult(
	val merchantUsers: List<MerchantUserSummary>,
)
