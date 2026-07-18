package paytech.practice.pay.application.payment

import paytech.practice.pay.domain.blockchain.BlockchainTransactionId
import paytech.practice.pay.domain.checkout.CheckoutSessionId
import paytech.practice.pay.domain.checkout.CheckoutSessionStatus
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus

/** [SubmitPaymentTransactionUseCase]의 결과다. */
data class SubmitPaymentTransactionResult(
	val blockchainTransactionId: BlockchainTransactionId,
	val checkoutSessionId: CheckoutSessionId,
	val checkoutSessionStatus: CheckoutSessionStatus,
	val paymentId: PaymentId,
	val paymentStatus: PaymentStatus,
)
