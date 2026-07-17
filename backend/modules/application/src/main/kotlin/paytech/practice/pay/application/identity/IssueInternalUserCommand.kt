package paytech.practice.pay.application.identity

import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.InternalUserRole
import paytech.practice.pay.domain.identity.LoginId

/**
 * [IssueInternalUserUseCase]의 입력이다.
 *
 * @property issuedByInternalUserId 발급을 요청한 SUPER_ADMIN의 ID. 발급 권한 자체는
 * (`InternalUser`가 아니라) 호출부인 inbound Adapter가 인증된 세션의 역할을 보고
 * 확인한다 — 이 Use Case는 그 확인이 끝났다고 전제하고, 감사 정보로만 이 값을 쓴다.
 */
data class IssueInternalUserCommand(
	val loginId: LoginId,
	val email: Email,
	val userName: String,
	val role: InternalUserRole,
	val issuedByInternalUserId: InternalUserId,
)
