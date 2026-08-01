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
 * [network]/[receivingWallet]을 요청 본문으로 받는 것은 **알려진 gap이다** — 수취 지갑은
 * PG가 수탁하는 지갑이라 가맹점이 정할 값이 아니고, 지금은 허용 목록 검증도 없다. 근거와
 * 예정된 방향은 `CreatePaymentCommand`의 KDoc과 `docs/architecture/mvp-scope.md`의
 * "수취 지갑 귀속" 절에 있다.
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
