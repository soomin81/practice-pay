package paytech.practice.pay.application.exchange

import paytech.practice.pay.domain.payment.PaymentId

/** 존재하지 않는 [PaymentId]로 Fake Exchange 매도를 시도했을 때 던진다. */
class PaymentNotFoundException(
	paymentId: PaymentId,
) : RuntimeException("Payment를 찾을 수 없습니다: ${paymentId.value}")
