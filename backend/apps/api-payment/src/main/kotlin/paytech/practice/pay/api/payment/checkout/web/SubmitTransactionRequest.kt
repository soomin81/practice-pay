package paytech.practice.pay.api.payment.checkout.web

import jakarta.validation.constraints.NotBlank

/** `POST /checkout/sessions/{checkoutSessionId}/transaction`의 요청 본문이다. */
data class SubmitTransactionRequest(
	@field:NotBlank
	val transactionHash: String,
)

/**
 * 제출 결과다. **"결제가 완료됐다"가 아니라 "제출을 접수했다"는 뜻이다** — 확정은
 * `apps:batch`의 Confirm Worker가 하므로 프론트는 곧바로 상태 폴링으로 넘어간다.
 */
data class SubmitTransactionResponse(
	val blockchainTransactionId: String,
	val checkoutSessionId: String,
	val checkoutSessionStatus: String,
	val paymentId: String,
	val paymentStatus: String,
)
