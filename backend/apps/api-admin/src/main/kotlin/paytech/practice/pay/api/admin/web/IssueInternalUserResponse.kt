package paytech.practice.pay.api.admin.web

import java.time.Instant

/**
 * `POST /admin/internal-users`의 응답 본문이다.
 *
 * [invitationToken]은 이 응답에서만 원문으로 보인다 — DB에는 Hash만 저장되고
 * 다시 조회할 방법이 없다(`docs/architecture/identity-access-api-key.md`의
 * "6.4 저장 정책"이 API Key 원문에 대해 정한 것과 같은 규칙을 초대 Token에도
 * 적용한다, [paytech.practice.pay.application.identity.IssueInternalUserResult]의
 * KDoc 참고). 호출한 SUPER_ADMIN이 이 값을 초대 대상에게 즉시 전달해야 한다.
 */
data class IssueInternalUserResponse(
	val internalUserId: String,
	val loginId: String,
	val email: String,
	val userName: String,
	val role: String,
	val invitationToken: String,
	val invitationExpiresAt: Instant,
)
