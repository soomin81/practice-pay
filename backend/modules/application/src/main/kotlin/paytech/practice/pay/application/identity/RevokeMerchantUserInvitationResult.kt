package paytech.practice.pay.application.identity

import paytech.practice.pay.domain.identity.MerchantUserId
import java.time.Instant

/** [RevokeMerchantUserInvitationUseCase]의 출력이다. */
data class RevokeMerchantUserInvitationResult(
	val merchantUserId: MerchantUserId,
	val revokedAt: Instant,
)
