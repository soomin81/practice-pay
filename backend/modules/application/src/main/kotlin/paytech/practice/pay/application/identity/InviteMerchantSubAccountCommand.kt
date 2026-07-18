package paytech.practice.pay.application.identity

import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.identity.MerchantUserRole

/**
 * [InviteMerchantSubAccountUseCase]의 입력이다.
 *
 * @property invitedByMerchantUserId 발급을 요청한 `OWNER`/`ADMIN`의 ID. 이 값
 * 하나로 발급 권한 확인([MerchantUser.canInviteSubAccounts])과 발급 대상 가맹점
 * (하위 계정은 항상 이 사용자와 같은 가맹점에 만들어진다)을 모두 결정한다 —
 * `merchantId`를 이 Command가 별도로 받지 않는 이유다(요청 본문으로 받으면
 * 호출자가 다른 가맹점의 `merchantId`를 실어 보내 남의 가맹점에 계정을 만들 수
 * 있다).
 */
data class InviteMerchantSubAccountCommand(
	val loginId: LoginId,
	val email: Email,
	val userName: String,
	val role: MerchantUserRole,
	val invitedByMerchantUserId: MerchantUserId,
)
