package paytech.practice.pay.api.payment.checkout.web

/**
 * `GET /checkout/sessions/{checkoutSessionId}/status`의 응답 본문이다
 * (`docs/architecture/checkout-api.md`의 4.2).
 *
 * [redirectUrl]은 `paymentStatus`가 `SUCCEEDED`가 됐을 때만 채워진다 — 프론트가
 * 리다이렉트 시점을 스스로 판단하지 않고 서버 신호를 따르게 하려는 것이다.
 *
 * [failureReason]은 `PaymentFailureReason` Enum 이름 그대로다. 고객에게 이 값을
 * 그대로 보여주지 말고 프론트가 안내 문구로 번역한다.
 */
data class CheckoutStatusResponse(
	val checkoutSessionStatus: String,
	val paymentStatus: String,
	val confirmationCount: Int,
	val requiredConfirmationCount: Int,
	val transactionHash: String?,
	val failureReason: String?,
	val redirectUrl: String?,
)
