package paytech.practice.pay.application.sweep

import paytech.practice.pay.domain.payment.PaymentId

/**
 * [ExpireCheckoutUseCase]의 입력이다. Sweep Worker가 만료된 `Payment`의 식별자를 넘기고,
 * Use Case가 그 Payment와 (있으면) 딸린 `CheckoutSession`을 함께 만료시킨다.
 */
data class ExpireCheckoutCommand(
	val paymentId: PaymentId,
)
