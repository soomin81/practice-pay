package paytech.practice.pay.api.merchant.web

import java.time.Instant

/**
 * `POST /merchant/merchant-users`의 응답 본문이다.
 *
 * [invitationToken]은 이 응답에서만 원문으로 보인다 — DB에는 Hash만 저장되고
 * 다시 조회할 방법이 없다(`IssueInternalUserResponse`/`RegisterMerchantResponse`와
 * 같은 규칙). 호출한 `OWNER`/`ADMIN`이 이 값을 하위 계정 대상자에게 즉시
 * 전달해야 한다.
 */
data class InviteMerchantSubAccountResponse(
	val merchantUserId: String,
	val loginId: String,
	val email: String,
	val userName: String,
	val role: String,
	val invitationToken: String,
	val invitationExpiresAt: Instant,
)
