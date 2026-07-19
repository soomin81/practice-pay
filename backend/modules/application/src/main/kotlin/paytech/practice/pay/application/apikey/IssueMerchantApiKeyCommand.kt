package paytech.practice.pay.application.apikey

import paytech.practice.pay.domain.apikey.ApiKeyScope
import paytech.practice.pay.domain.identity.MerchantUserId

/**
 * [IssueMerchantApiKeyUseCase]의 입력이다.
 *
 * @property scopes MVP는 `PAYMENT_CREATE`/`PAYMENT_READ`만 발급한다
 * (`docs/architecture/identity-access-api-key.md`의 "6.8 Scope") — 비어 있거나
 * 그 둘 밖의 값이 섞여 있으면 [IssueMerchantApiKeyUseCase]가 `IllegalArgumentException`을
 * 던진다.
 * @property issuedByMerchantUserId 발급을 요청한 `OWNER`/`ADMIN`의 ID. 이 값
 * 하나로 발급 권한 확인과 발급 대상 가맹점을 모두 결정한다 —
 * [paytech.practice.pay.application.identity.InviteMerchantSubAccountCommand]와
 * 같은 이유로 `merchantId`를 별도로 받지 않는다.
 */
data class IssueMerchantApiKeyCommand(
	val keyName: String,
	val scopes: Set<ApiKeyScope>,
	val issuedByMerchantUserId: MerchantUserId,
)
