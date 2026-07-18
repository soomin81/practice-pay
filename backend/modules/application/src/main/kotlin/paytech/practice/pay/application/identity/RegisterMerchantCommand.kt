package paytech.practice.pay.application.identity

import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.merchant.MerchantCode
import paytech.practice.pay.domain.shared.HttpUrl

/**
 * [RegisterMerchantUseCase]의 입력이다.
 *
 * @property registeredByInternalUserId 등록을 요청한 내부 운영자의 ID. 등록 권한 자체는
 * (`InternalUser`가 아니라) 호출부인 inbound Adapter가 인증된 세션의 역할을 보고
 * 확인한다 — 이 Use Case는 그 확인이 끝났다고 전제하고, 감사 정보로만 이 값을 쓴다
 * ([IssueInternalUserCommand]의 `issuedByInternalUserId`와 같은 이유).
 */
data class RegisterMerchantCommand(
	val merchantCode: MerchantCode,
	val merchantName: String,
	val webhookUrl: HttpUrl?,
	val ownerLoginId: LoginId,
	val ownerEmail: Email,
	val ownerUserName: String,
	val registeredByInternalUserId: InternalUserId,
)
