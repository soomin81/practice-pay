package paytech.practice.pay.application.identity

import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.InternalUserRole
import paytech.practice.pay.domain.identity.LoginId
import java.time.Instant

/**
 * [IssueInternalUserUseCase]의 결과다.
 *
 * @property invitationToken 초대 Token **원문**이다 — DB에는 이 값의 Hash만 저장돼
 * 있고, 이 결과가 반환된 뒤에는 다시 얻을 방법이 없다(`docs/architecture/identity-access-api-key.md`의
 * "6.4 저장 정책"이 API Key 원문에 대해 정한 것과 같은 규칙을 초대 Token에도
 * 적용한다). 호출부는 이 값을 즉시 초대 대상에게 전달하고 저장하지 않아야 한다.
 */
data class IssueInternalUserResult(
	val internalUserId: InternalUserId,
	val loginId: LoginId,
	val email: Email,
	val userName: String,
	val role: InternalUserRole,
	val invitationToken: String,
	val invitationExpiresAt: Instant,
)
