package paytech.practice.pay.application.identity

import paytech.practice.pay.domain.identity.AccountStatus
import paytech.practice.pay.domain.identity.MerchantUserId
import java.time.Instant

/** [ChangeMerchantUserStatusUseCase]의 출력이다. */
data class ChangeMerchantUserStatusResult(
	val merchantUserId: MerchantUserId,
	val status: AccountStatus,
	val changedAt: Instant,
)
