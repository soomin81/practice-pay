package paytech.practice.pay.infra.persistence.jooq.blockchain

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import paytech.practice.pay.application.port.outbound.BlockchainTransactionRepository
import paytech.practice.pay.dbcore.jooq.tables.BlockchainTransaction.Companion.BLOCKCHAIN_TRANSACTION
import paytech.practice.pay.dbcore.jooq.tables.records.BlockchainTransactionRecord
import paytech.practice.pay.domain.blockchain.BlockchainTransaction
import paytech.practice.pay.domain.blockchain.BlockchainTransactionId
import paytech.practice.pay.domain.blockchain.BlockchainTransactionStatus
import paytech.practice.pay.domain.blockchain.ChainId
import paytech.practice.pay.domain.blockchain.ContractAddress
import paytech.practice.pay.domain.blockchain.TransactionHash
import paytech.practice.pay.domain.blockchain.TransactionType
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.TokenAmount
import paytech.practice.pay.domain.shared.WalletAddress
import paytech.practice.pay.infra.persistence.jooq.paymentId
import paytech.practice.pay.infra.persistence.jooq.paymentSeq
import paytech.practice.pay.infra.persistence.jooq.toUtcInstant
import paytech.practice.pay.infra.persistence.jooq.toUtcLocalDateTime

/**
 * jOOQ로 [BlockchainTransactionRepository] Port를 구현한다.
 *
 * `save`의 낙관적 잠금 한계는 [paytech.practice.pay.infra.persistence.jooq.payment.PaymentRepositoryAdapter]와
 * 동일하다 — 도메인 [BlockchainTransaction]이 자신의 `version`을 모르기 때문에, DB에서
 * 방금 읽은 version을 그대로 +1 해서 쓴다.
 */
@Repository
class BlockchainTransactionRepositoryAdapter(
	private val dsl: DSLContext,
) : BlockchainTransactionRepository {
	override fun save(blockchainTransaction: BlockchainTransaction) {
		val existing =
			dsl
				.selectFrom(BLOCKCHAIN_TRANSACTION)
				.where(BLOCKCHAIN_TRANSACTION.BLOCKCHAIN_TRANSACTION_ID.eq(blockchainTransaction.id.value))
				.fetchOne()

		if (existing == null) {
			dsl
				.newRecord(BLOCKCHAIN_TRANSACTION)
				.apply {
					fillFrom(blockchainTransaction)
					version = 0L
				}.insert()
		} else {
			dsl
				.update(BLOCKCHAIN_TRANSACTION)
				.set(BLOCKCHAIN_TRANSACTION.TRANSACTION_STATUS, blockchainTransaction.status.name)
				.set(BLOCKCHAIN_TRANSACTION.BLOCK_NUMBER, blockchainTransaction.blockNumber)
				.set(BLOCKCHAIN_TRANSACTION.CONFIRMATION_COUNT, blockchainTransaction.confirmationCount)
				.set(BLOCKCHAIN_TRANSACTION.FAILURE_CODE, blockchainTransaction.failureCode)
				.set(BLOCKCHAIN_TRANSACTION.FAILURE_MESSAGE, blockchainTransaction.failureMessage)
				.set(BLOCKCHAIN_TRANSACTION.DETECTED_AT, blockchainTransaction.detectedAt?.toUtcLocalDateTime())
				.set(BLOCKCHAIN_TRANSACTION.CONFIRMED_AT, blockchainTransaction.confirmedAt?.toUtcLocalDateTime())
				.set(BLOCKCHAIN_TRANSACTION.UPDATED_AT, blockchainTransaction.updatedAt.toUtcLocalDateTime())
				.set(BLOCKCHAIN_TRANSACTION.VERSION, (existing.version ?: 0L) + 1)
				.where(BLOCKCHAIN_TRANSACTION.BLOCKCHAIN_TRANSACTION_SEQ.eq(existing.blockchainTransactionSeq))
				.and(BLOCKCHAIN_TRANSACTION.VERSION.eq(existing.version))
				.execute()
				.also { updatedRows ->
					check(updatedRows == 1) {
						"BlockchainTransaction(${blockchainTransaction.id.value}) 저장에 실패했습니다 — " +
							"동시에 변경된 것으로 보입니다(예상 version=${existing.version})."
					}
				}
		}
	}

	override fun findById(blockchainTransactionId: BlockchainTransactionId): BlockchainTransaction? =
		dsl
			.selectFrom(BLOCKCHAIN_TRANSACTION)
			.where(BLOCKCHAIN_TRANSACTION.BLOCKCHAIN_TRANSACTION_ID.eq(blockchainTransactionId.value))
			.fetchOne()
			?.toDomain()

	override fun findByNetworkAndTransactionHash(
		network: BlockchainNetwork,
		transactionHash: TransactionHash,
	): BlockchainTransaction? =
		dsl
			.selectFrom(BLOCKCHAIN_TRANSACTION)
			.where(BLOCKCHAIN_TRANSACTION.NETWORK_CODE.eq(network.code))
			.and(BLOCKCHAIN_TRANSACTION.TRANSACTION_HASH.eq(transactionHash.value))
			.fetchOne()
			?.toDomain()

	override fun findPendingConfirmation(): List<BlockchainTransaction> =
		dsl
			.selectFrom(BLOCKCHAIN_TRANSACTION)
			.where(BLOCKCHAIN_TRANSACTION.TRANSACTION_STATUS.`in`(PENDING_CONFIRMATION_STATUSES))
			.orderBy(BLOCKCHAIN_TRANSACTION.UPDATED_AT.asc())
			.fetch()
			.map { it.toDomain() }

	private fun BlockchainTransactionRecord.fillFrom(blockchainTransaction: BlockchainTransaction) {
		blockchainTransactionId = blockchainTransaction.id.value
		paymentSeq = dsl.paymentSeq(blockchainTransaction.paymentId)
		transactionType = blockchainTransaction.transactionType.name
		networkCode = blockchainTransaction.network.code
		chainId = blockchainTransaction.chainId.value
		transactionHash = blockchainTransaction.transactionHash.value
		fromAddress = blockchainTransaction.fromAddress?.value
		toAddress = blockchainTransaction.toAddress?.value
		tokenContractAddress = blockchainTransaction.tokenContractAddress?.value
		tokenAssetCode = blockchainTransaction.tokenAsset.code
		amountMinor = blockchainTransaction.amountMinor?.amountMinor
		requiredConfirmationCount = blockchainTransaction.requiredConfirmationCount
		transactionStatus = blockchainTransaction.status.name
		blockNumber = blockchainTransaction.blockNumber
		confirmationCount = blockchainTransaction.confirmationCount
		failureCode = blockchainTransaction.failureCode
		failureMessage = blockchainTransaction.failureMessage
		submittedAt = blockchainTransaction.submittedAt.toUtcLocalDateTime()
		detectedAt = blockchainTransaction.detectedAt?.toUtcLocalDateTime()
		confirmedAt = blockchainTransaction.confirmedAt?.toUtcLocalDateTime()
		createdAt = blockchainTransaction.submittedAt.toUtcLocalDateTime()
		updatedAt = blockchainTransaction.updatedAt.toUtcLocalDateTime()
	}

	private fun BlockchainTransactionRecord.toDomain(): BlockchainTransaction =
		BlockchainTransaction.reconstitute(
			id = BlockchainTransactionId(blockchainTransactionId!!),
			paymentId = dsl.paymentId(paymentSeq!!),
			transactionType = TransactionType.valueOf(transactionType!!),
			network = BlockchainNetwork(networkCode!!),
			chainId = ChainId(chainId!!),
			transactionHash = TransactionHash(transactionHash!!),
			fromAddress = fromAddress?.let { WalletAddress(it) },
			toAddress = toAddress?.let { WalletAddress(it) },
			tokenContractAddress = tokenContractAddress?.let { ContractAddress(it) },
			tokenAsset = Asset(tokenAssetCode!!),
			amountMinor = amountMinor?.let { TokenAmount(it) },
			requiredConfirmationCount = requiredConfirmationCount!!,
			submittedAt = submittedAt!!.toUtcInstant(),
			status = BlockchainTransactionStatus.valueOf(transactionStatus!!),
			blockNumber = blockNumber,
			confirmationCount = confirmationCount!!,
			failureCode = failureCode,
			failureMessage = failureMessage,
			detectedAt = detectedAt?.toUtcInstant(),
			confirmedAt = confirmedAt?.toUtcInstant(),
			updatedAt = updatedAt!!.toUtcInstant(),
		)

	companion object {
		private val PENDING_CONFIRMATION_STATUSES =
			listOf(
				BlockchainTransactionStatus.SUBMITTED.name,
				BlockchainTransactionStatus.DETECTED.name,
				BlockchainTransactionStatus.CONFIRMING.name,
			)
	}
}
