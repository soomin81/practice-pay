package paytech.practice.pay.application.payment

import paytech.practice.pay.domain.blockchain.BlockchainTransactionId
import paytech.practice.pay.domain.blockchain.BlockchainTransactionStatus
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus

/**
 * [ConfirmBlockchainTransactionUseCase]의 결과다. 이번 폴링 이후의 최종 상태를
 * 그대로 돌려준다 — 아직 온체인에서 못 찾았으면 상태는 폴링 전과 동일하다.
 */
data class ConfirmBlockchainTransactionResult(
	val blockchainTransactionId: BlockchainTransactionId,
	val blockchainTransactionStatus: BlockchainTransactionStatus,
	val paymentId: PaymentId,
	val paymentStatus: PaymentStatus,
)
