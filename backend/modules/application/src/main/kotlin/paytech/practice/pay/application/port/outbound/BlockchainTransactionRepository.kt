package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.blockchain.BlockchainTransaction
import paytech.practice.pay.domain.blockchain.BlockchainTransactionId
import paytech.practice.pay.domain.blockchain.TransactionHash
import paytech.practice.pay.domain.shared.BlockchainNetwork

/**
 * [BlockchainTransaction] Aggregate를 저장·복원하는 Command Repository Outbound Port다.
 */
interface BlockchainTransactionRepository {
	/** BlockchainTransaction을 저장한다(신규 생성·상태 변경 모두 이 메서드로 반영한다). */
	fun save(blockchainTransaction: BlockchainTransaction)

	/** `blockchain_transaction_id`로 BlockchainTransaction을 찾는다. 없으면 `null`이다. */
	fun findById(blockchainTransactionId: BlockchainTransactionId): BlockchainTransaction?

	/**
	 * `(network_code, transaction_hash)` 조합으로 기존 BlockchainTransaction을 찾는다.
	 *
	 * `uk_blockchain_network_hash` Unique 제약과 대응하는 조회다 — 같은 Transaction
	 * Hash가 이미 다른 Payment에 쓰이고 있는지 확인할 때 쓴다(`docs/domain/glossary.md`의
	 * Transaction Hash 정의: "`networkCode + transactionHash`로 중복을 방지한다").
	 */
	fun findByNetworkAndTransactionHash(
		network: BlockchainNetwork,
		transactionHash: TransactionHash,
	): BlockchainTransaction?

	/**
	 * 아직 종료되지 않은(`SUBMITTED`/`DETECTED`/`CONFIRMING`) BlockchainTransaction을
	 * 전부 찾는다 — Confirm Worker(`apps:batch`)가 폴링 대상 목록을 뽑을 때 쓴다.
	 * `docs/database/database-design.md`의 "Confirm Worker: `transaction_status +
	 * updated_at`" 인덱스와 대응하며, 오래 갱신되지 않은 것부터 먼저 처리하도록
	 * `updated_at` 오름차순으로 반환한다.
	 */
	fun findPendingConfirmation(): List<BlockchainTransaction>
}
