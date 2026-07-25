package paytech.practice.pay.application.identity

import paytech.practice.pay.domain.identity.MerchantUserId
import java.time.Instant

/**
 * [ResendMerchantUserInvitationUseCase]의 출력이다.
 *
 * [invitationToken]은 이 응답에서만 원문으로 보인다 — 최초 발급과 같은 규칙이다
 * (DB에는 Hash만 남는다). **이전 초대 링크는 이 시점에 무효가 된다.**
 */
data class ResendMerchantUserInvitationResult(
	val merchantUserId: MerchantUserId,
	val invitationToken: String,
	val invitationExpiresAt: Instant,
)
