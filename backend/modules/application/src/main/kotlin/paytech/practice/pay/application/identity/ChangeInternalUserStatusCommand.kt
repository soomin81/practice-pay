package paytech.practice.pay.application.identity

import paytech.practice.pay.domain.identity.InternalUserId

/**
 * [ChangeInternalUserStatusUseCase]의 입력이다.
 *
 * [requestedByInternalUserId]는 권한 검사가 아니라 **자기 자신 차단**에 쓴다(권한은
 * `SecurityConfig`가 정적으로 판단한다 — [InternalUserManagementGuard]의 KDoc 참고).
 * [IssueInternalUserCommand]가 감사 정보로 발급자를 받는 것과 같은 방식으로 세션에서 온다.
 */
data class ChangeInternalUserStatusCommand(
	val targetInternalUserId: InternalUserId,
	val action: InternalUserStatusAction,
	val requestedByInternalUserId: InternalUserId,
)
