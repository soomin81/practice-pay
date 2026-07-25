package paytech.practice.pay.application.identity

import paytech.practice.pay.application.port.outbound.InternalUserSummary

/** [ListInternalUsersUseCase]의 출력이다. */
data class ListInternalUsersResult(
	val internalUsers: List<InternalUserSummary>,
)
