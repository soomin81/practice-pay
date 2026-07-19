package paytech.practice.pay.application.checkout

import paytech.practice.pay.domain.checkout.CheckoutSessionId

/**
 * 고객이 체크아웃을 취소하는 Command다.
 *
 * 고객은 계정이 없고 `checkoutSessionId`를 아는 것 자체가 권한이므로
 * (`docs/architecture/checkout-api.md`의 3절) 요청자를 식별하는 필드가 없다.
 */
data class CancelCheckoutSessionCommand(
	val checkoutSessionId: CheckoutSessionId,
)
