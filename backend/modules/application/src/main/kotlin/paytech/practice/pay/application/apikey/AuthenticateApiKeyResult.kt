package paytech.practice.pay.application.apikey

import paytech.practice.pay.domain.apikey.ApiKeyScope
import paytech.practice.pay.domain.apikey.MerchantApiKeyId
import paytech.practice.pay.domain.merchant.MerchantId

/**
 * [AuthenticateApiKeyUseCase]의 결과다.
 *
 * [scopes]는 이 Key로 호출할 수 있는 API 범위일 뿐이다 — 특정 엔드포인트가
 * 어떤 Scope를 요구하는지는 이 Use Case의 책임이 아니라 inbound Adapter(예:
 * `apps:api-payment`의 `SecurityConfig`)가 결정한다. 인증(이 Key가 유효한가)과
 * 인가(이 Key로 이 엔드포인트를 부를 수 있는가)를 분리하기 위해서다.
 */
data class AuthenticateApiKeyResult(
	val merchantId: MerchantId,
	val merchantApiKeyId: MerchantApiKeyId,
	val scopes: Set<ApiKeyScope>,
)
