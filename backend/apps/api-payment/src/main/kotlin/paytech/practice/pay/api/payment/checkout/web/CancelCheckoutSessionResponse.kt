package paytech.practice.pay.api.payment.checkout.web

/** `POST /checkout/sessions/{checkoutSessionId}/cancel`의 응답 본문이다. */
data class CancelCheckoutSessionResponse(
	val checkoutSessionId: String,
	val checkoutSessionStatus: String,
	val redirectUrl: String?,
)
