package paytech.practice.pay.api.payment.web

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

/**
 * `POST /api/v1/payments`의 요청 본문이다(`docs/architecture/identity-access-api-key.md`의
 * "대표 사용 API" 참고).
 *
 * `merchantId`는 여기 없다 — `PaymentController`가 `MerchantApiKey` 인증
 * (`ApiKeyAuthenticationFilter`가 심는 `ApiKeyPrincipal`)에서 가져온다. 이전에는
 * API Key 인증이 없어서 임시로 요청 본문에 받았지만, 이제는 인증된 가맹점만
 * 결제를 생성할 수 있다.
 *
 * [network]/[receivingWallet]은 여전히 `CreatePaymentCommand`의 KDoc이 설명하는
 * 이유(가맹점 지갑 설정을 어디서 조회하는지 아직 `docs/`에 없음)로 직접 받는다.
 */
data class CreatePaymentRequest(
	@field:NotBlank
	val merchantOrderId: String,
	@field:NotBlank
	val orderName: String,
	@field:Positive
	val orderAmount: Long,
	@field:NotBlank
	val network: String,
	@field:NotBlank
	val receivingWallet: String,
	@field:NotBlank
	val successUrl: String,
	val cancelUrl: String?,
)
