package paytech.practice.pay.application.identity

import paytech.practice.pay.domain.identity.AccountStatus
import paytech.practice.pay.domain.identity.InternalUserId
import java.time.Instant

/** [ChangeInternalUserStatusUseCase]의 출력이다. */
data class ChangeInternalUserStatusResult(
	val internalUserId: InternalUserId,
	val status: AccountStatus,
	val changedAt: Instant,
)
