package paytech.practice.pay.application.checkout

import paytech.practice.pay.domain.checkout.CheckoutSessionId

/** 존재하지 않는 [CheckoutSessionId]로 CheckoutSession을 다루려고 했을 때 던진다. */
class CheckoutSessionNotFoundException(
	checkoutSessionId: CheckoutSessionId,
) : RuntimeException("CheckoutSession을 찾을 수 없습니다: ${checkoutSessionId.value}")
