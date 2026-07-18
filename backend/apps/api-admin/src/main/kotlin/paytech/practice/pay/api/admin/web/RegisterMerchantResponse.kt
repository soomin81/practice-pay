package paytech.practice.pay.api.admin.web

import java.time.Instant

/**
 * `POST /admin/merchants`의 응답 본문이다.
 *
 * [invitationToken]은 이 응답에서만 원문으로 보인다 — DB에는 Hash만 저장되고
 * 다시 조회할 방법이 없다(`IssueInternalUserResponse`와 같은 규칙,
 * [paytech.practice.pay.application.identity.RegisterMerchantResult]의 KDoc 참고).
 * 호출한 내부 운영자가 이 값을 OWNER에게 즉시 전달해야 한다.
 */
data class RegisterMerchantResponse(
	val merchantId: String,
	val merchantCode: String,
	val merchantName: String,
	val ownerMerchantUserId: String,
	val ownerLoginId: String,
	val ownerEmail: String,
	val invitationToken: String,
	val invitationExpiresAt: Instant,
)
