package paytech.practice.pay.application.payment

import paytech.practice.pay.domain.checkout.CheckoutSessionId

/** 존재하지 않는 [CheckoutSessionId]로 결제 제출을 시도했을 때 던진다. */
class CheckoutSessionNotFoundException(
	checkoutSessionId: CheckoutSessionId,
) : RuntimeException("CheckoutSession을 찾을 수 없습니다: ${checkoutSessionId.value}")
