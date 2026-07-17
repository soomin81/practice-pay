package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.blockchain.BlockchainTransaction
import paytech.practice.pay.domain.blockchain.BlockchainTransactionId

/**
 * [BlockchainTransaction] Aggregate를 저장·복원하는 Command Repository Outbound Port다.
 */
interface BlockchainTransactionRepository {
	/** BlockchainTransaction을 저장한다(신규 생성·상태 변경 모두 이 메서드로 반영한다). */
	fun save(blockchainTransaction: BlockchainTransaction)

	/** `blockchain_transaction_id`로 BlockchainTransaction을 찾는다. 없으면 `null`이다. */
	fun findById(blockchainTransactionId: BlockchainTransactionId): BlockchainTransaction?
}
