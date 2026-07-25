package paytech.practice.pay.application.identity

import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.InternalUserRole
import java.time.Instant

/** [ChangeInternalUserRoleUseCase]의 출력이다. */
data class ChangeInternalUserRoleResult(
	val internalUserId: InternalUserId,
	val role: InternalUserRole,
	val changedAt: Instant,
)
