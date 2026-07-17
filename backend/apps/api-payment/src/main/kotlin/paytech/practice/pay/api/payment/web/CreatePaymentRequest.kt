package paytech.practice.pay.api.payment.web

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

/**
 * `POST /api/v1/payments`의 요청 본문이다(`docs/architecture/identity-access-api-key.md`의
 * "대표 사용 API" 참고).
 *
 * [merchantId]를 요청 본문으로 직접 받는다 — `MerchantApiKey` 인증(같은 문서의
 * "6.3 인증 방식")이 아직 구현되지 않아서, 인증된 API Key로부터 가맹점을
 * 알아내는 대신 임시로 이렇게 받는다. API Key 인증이 생기면 이 필드는
 * 제거하고 인증 컨텍스트에서 가져와야 한다.
 *
 * [network]/[receivingWallet]도 마찬가지로 `CreatePaymentCommand`의 KDoc이 설명하는
 * 것과 같은 이유(가맹점 지갑 설정을 어디서 조회하는지 아직 `docs/`에 없음)로 직접
 * 받는다.
 */
data class CreatePaymentRequest(
	@field:NotBlank
	val merchantId: String,
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
