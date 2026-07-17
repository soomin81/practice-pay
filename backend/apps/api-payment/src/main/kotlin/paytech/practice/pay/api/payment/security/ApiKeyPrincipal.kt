package paytech.practice.pay.api.payment.security

import paytech.practice.pay.domain.apikey.MerchantApiKeyId
import paytech.practice.pay.domain.merchant.MerchantId

/**
 * `ApiKeyAuthenticationFilter`가 인증에 성공하면 `Authentication.principal`로
 * 심는 값이다. 컨트롤러는 `@AuthenticationPrincipal ApiKeyPrincipal`로 바로
 * 받는다 — `merchantId`를 요청 본문 대신 여기서 가져온다(`PaymentController`의
 * KDoc, `backend/CLAUDE.md`의 "알려진 gap" 해소 참고).
 */
data class ApiKeyPrincipal(
	val merchantId: MerchantId,
	val merchantApiKeyId: MerchantApiKeyId,
)
