package paytech.practice.pay.application.payment

import paytech.practice.pay.domain.blockchain.BlockchainTransactionId

/** 존재하지 않는 [BlockchainTransactionId]로 감지·Confirm을 시도했을 때 던진다. */
class BlockchainTransactionNotFoundException(
	blockchainTransactionId: BlockchainTransactionId,
) : RuntimeException("BlockchainTransaction을 찾을 수 없습니다: ${blockchainTransactionId.value}")
