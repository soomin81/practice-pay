package paytech.practice.pay.application.identity

import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.InternalUserRole

/**
 * [ChangeInternalUserRoleUseCase]의 입력이다. [ChangeInternalUserStatusCommand]와 같은
 * 이유로 [requestedByInternalUserId]는 권한이 아니라 **자기 자신 차단**에 쓴다(권한은
 * `SecurityConfig`가 정적으로 판단한다 — [InternalUserManagementGuard]의 KDoc 참고).
 */
data class ChangeInternalUserRoleCommand(
	val targetInternalUserId: InternalUserId,
	val newRole: InternalUserRole,
	val requestedByInternalUserId: InternalUserId,
)
