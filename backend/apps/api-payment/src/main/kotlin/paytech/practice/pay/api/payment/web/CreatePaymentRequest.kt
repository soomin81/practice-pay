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
 * **수취 지갑은 요청 필드가 아니다** — PG가 수탁하는 지갑이라 서버 설정
 * (`app.payment.receiving-wallets`)에서 [network]에 맞는 값을 꺼낸다. 가맹점이 지정할 수
 * 있으면 USDC를 직접 받으면서 정산 채권까지 받게 되기 때문이다
 * (`docs/architecture/mvp-scope.md`의 "수취 지갑 귀속").
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
	val successUrl: String,
	val cancelUrl: String?,
)
