package paytech.practice.pay.application.identity

import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.merchant.MerchantCode
import paytech.practice.pay.domain.merchant.MerchantId
import java.time.Instant

/**
 * [RegisterMerchantUseCase]의 결과다.
 *
 * @property invitationToken 초대 Token **원문**이다 — DB에는 이 값의 Hash만 저장돼
 * 있고, 이 결과가 반환된 뒤에는 다시 얻을 방법이 없다([IssueInternalUserResult]와
 * 같은 규칙, `docs/architecture/identity-access-api-key.md`의 "6.4 저장 정책" 참고).
 * 호출부는 이 값을 즉시 OWNER에게 전달하고 저장하지 않아야 한다.
 */
data class RegisterMerchantResult(
	val merchantId: MerchantId,
	val merchantCode: MerchantCode,
	val merchantName: String,
	val ownerMerchantUserId: MerchantUserId,
	val ownerLoginId: LoginId,
	val ownerEmail: Email,
	val invitationToken: String,
	val invitationExpiresAt: Instant,
)
