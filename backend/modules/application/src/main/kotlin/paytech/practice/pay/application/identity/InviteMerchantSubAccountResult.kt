package paytech.practice.pay.application.identity

import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.identity.MerchantUserRole
import java.time.Instant

/**
 * [InviteMerchantSubAccountUseCase]의 결과다.
 *
 * @property invitationToken 초대 Token **원문**이다 — DB에는 이 값의 Hash만 저장돼
 * 있고, 이 결과가 반환된 뒤에는 다시 얻을 방법이 없다([IssueInternalUserResult]/
 * [RegisterMerchantResult]와 같은 규칙). 호출부는 이 값을 즉시 하위 계정
 * 대상자에게 전달하고 저장하지 않아야 한다.
 */
data class InviteMerchantSubAccountResult(
	val merchantUserId: MerchantUserId,
	val loginId: LoginId,
	val email: Email,
	val userName: String,
	val role: MerchantUserRole,
	val invitationToken: String,
	val invitationExpiresAt: Instant,
)
