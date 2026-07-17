package paytech.practice.pay.api.payment.web

/** `POST /api/v1/payments`의 응답 본문이다. */
data class CreatePaymentResponse(
	val paymentId: String,
	val checkoutSessionId: String,
)
