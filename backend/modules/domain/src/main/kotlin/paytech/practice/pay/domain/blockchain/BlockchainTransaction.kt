package paytech.practice.pay.domain.blockchain

import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.TokenAmount
import paytech.practice.pay.domain.shared.WalletAddress
import java.time.Instant

/**
 * 블록체인 거래(BlockchainTransaction) Aggregate Root다.
 *
 * 온체인 자산 전송이며, Transaction Hash, 온체인 상세 정보, Confirm 수, 성공과
 * 실패를 관리한다. `Payment`와 별도의 상태를 가진다(`docs/domain/glossary.md`).
 * 상태는 이 클래스의 메서드를 통해서만 변경되고, 전이 전 현재 상태를 검증하며,
 * 종료 상태(`CONFIRMED`/`FAILED`/`REORGED`)는 재사용하지 않는다. `Payment`는
 * ID로만 참조한다.
 *
 * 여기서의 검증은 스스로의 상태 전이 규칙뿐이다 — Network/Contract/수취 지갑/금액
 * 등이 실제로 기대값과 일치하는지 확인하는 것은 이 Aggregate가 아니라
 * `PaymentTransactionValidator` 같은 도메인 서비스의 책임이다.
 *
 * 인스턴스는 [create]로 새로 만들거나 [reconstitute]로 저장된 값을 복원해서 얻는다.
 *
 * @see docs/domain/domain-model.md
 * @see docs/domain/state-transitions.md
 */
class BlockchainTransaction private constructor(
	val id: BlockchainTransactionId,
	val paymentId: PaymentId,
	val transactionType: TransactionType,
	val network: BlockchainNetwork,
	val chainId: ChainId,
	val transactionHash: TransactionHash,
	val fromAddress: WalletAddress?,
	val toAddress: WalletAddress?,
	val tokenContractAddress: ContractAddress?,
	val tokenAsset: Asset,
	val amountMinor: TokenAmount?,
	val requiredConfirmationCount: Int,
	val submittedAt: Instant,
	status: BlockchainTransactionStatus,
	blockNumber: Long?,
	confirmationCount: Int,
	failureCode: String?,
	failureMessage: String?,
	detectedAt: Instant?,
	confirmedAt: Instant?,
	updatedAt: Instant,
) {
	var status: BlockchainTransactionStatus = status
		private set

	var blockNumber: Long? = blockNumber
		private set

	/** 현재까지 누적된 블록 확인 수. [requiredConfirmationCount] 이상이 되어야 [confirm]할 수 있다. */
	var confirmationCount: Int = confirmationCount
		private set

	var failureCode: String? = failureCode
		private set

	var failureMessage: String? = failureMessage
		private set

	var detectedAt: Instant? = detectedAt
		private set

	/** 거래가 `CONFIRMED`로 확정된 시각. `CONFIRMED` 상태에서는 항상 값이 있다. */
	var confirmedAt: Instant? = confirmedAt
		private set

	var updatedAt: Instant = updatedAt
		private set

	init {
		require(requiredConfirmationCount > 0) {
			"requiredConfirmationCount는 0보다 커야 합니다: $requiredConfirmationCount"
		}
		require(confirmationCount >= 0) { "confirmationCount는 음수일 수 없습니다: $confirmationCount" }
		require(amountMinor == null || amountMinor > TokenAmount.ZERO) {
			"amountMinor는 null이 아니면 0보다 커야 합니다: $amountMinor"
		}
		require(status != BlockchainTransactionStatus.CONFIRMED || confirmedAt != null) {
			"CONFIRMED 상태는 confirmedAt이 반드시 있어야 합니다."
		}
	}

	/** `SUBMITTED` → `DETECTED`. 온체인에서 거래가 블록에 포함된 것을 감지했다. */
	fun detect(
		blockNumber: Long,
		detectedAt: Instant,
	) {
		checkTransition(status == BlockchainTransactionStatus.SUBMITTED, BlockchainTransactionStatus.DETECTED)
		this.blockNumber = blockNumber
		status = BlockchainTransactionStatus.DETECTED
		this.detectedAt = detectedAt
		updatedAt = detectedAt
	}

	/** `DETECTED` → `CONFIRMING`. 추가 블록 확인 대기를 시작한다. */
	fun startConfirming(changedAt: Instant) {
		checkTransition(status == BlockchainTransactionStatus.DETECTED, BlockchainTransactionStatus.CONFIRMING)
		status = BlockchainTransactionStatus.CONFIRMING
		updatedAt = changedAt
	}

	/** `CONFIRMING` 상태에서 누적 블록 확인 수를 갱신한다. 상태 전이는 없다. */
	fun recordConfirmation(
		confirmationCount: Int,
		changedAt: Instant,
	) {
		check(status == BlockchainTransactionStatus.CONFIRMING) {
			"CONFIRMING 상태가 아니면 confirmationCount를 갱신할 수 없습니다: 현재 상태=$status"
		}
		require(confirmationCount >= 0) { "confirmationCount는 음수일 수 없습니다: $confirmationCount" }
		this.confirmationCount = confirmationCount
		updatedAt = changedAt
	}

	/** `CONFIRMING` → `CONFIRMED`. */
	fun confirm(confirmedAt: Instant) {
		checkTransition(status == BlockchainTransactionStatus.CONFIRMING, BlockchainTransactionStatus.CONFIRMED)
		status = BlockchainTransactionStatus.CONFIRMED
		this.confirmedAt = confirmedAt
		updatedAt = confirmedAt
	}

	/** (`SUBMITTED`, `DETECTED` 또는 `CONFIRMING`) → `FAILED`. */
	fun fail(
		failureCode: String?,
		failureMessage: String?,
		failedAt: Instant,
	) {
		checkTransition(
			status == BlockchainTransactionStatus.SUBMITTED ||
				status == BlockchainTransactionStatus.DETECTED ||
				status == BlockchainTransactionStatus.CONFIRMING,
			BlockchainTransactionStatus.FAILED,
		)
		status = BlockchainTransactionStatus.FAILED
		this.failureCode = failureCode
		this.failureMessage = failureMessage
		updatedAt = failedAt
	}

	private fun checkTransition(
		allowed: Boolean,
		target: BlockchainTransactionStatus,
	) {
		check(allowed) { "BlockchainTransaction 상태를 $status 에서 $target (으)로 전이할 수 없습니다." }
	}

	companion object {
		/** 새 온체인 거래를 `SUBMITTED` 상태로 생성한다. */
		fun create(
			id: BlockchainTransactionId,
			paymentId: PaymentId,
			transactionType: TransactionType,
			network: BlockchainNetwork,
			chainId: ChainId,
			transactionHash: TransactionHash,
			fromAddress: WalletAddress?,
			toAddress: WalletAddress?,
			tokenContractAddress: ContractAddress?,
			tokenAsset: Asset,
			amountMinor: TokenAmount?,
			requiredConfirmationCount: Int,
			submittedAt: Instant,
		): BlockchainTransaction =
			BlockchainTransaction(
				id = id,
				paymentId = paymentId,
				transactionType = transactionType,
				network = network,
				chainId = chainId,
				transactionHash = transactionHash,
				fromAddress = fromAddress,
				toAddress = toAddress,
				tokenContractAddress = tokenContractAddress,
				tokenAsset = tokenAsset,
				amountMinor = amountMinor,
				requiredConfirmationCount = requiredConfirmationCount,
				submittedAt = submittedAt,
				status = BlockchainTransactionStatus.SUBMITTED,
				blockNumber = null,
				confirmationCount = 0,
				failureCode = null,
				failureMessage = null,
				detectedAt = null,
				confirmedAt = null,
				updatedAt = submittedAt,
			)

		/** 영속 계층에 저장되어 있던 값으로 Aggregate를 복원한다. */
		fun reconstitute(
			id: BlockchainTransactionId,
			paymentId: PaymentId,
			transactionType: TransactionType,
			network: BlockchainNetwork,
			chainId: ChainId,
			transactionHash: TransactionHash,
			fromAddress: WalletAddress?,
			toAddress: WalletAddress?,
			tokenContractAddress: ContractAddress?,
			tokenAsset: Asset,
			amountMinor: TokenAmount?,
			requiredConfirmationCount: Int,
			submittedAt: Instant,
			status: BlockchainTransactionStatus,
			blockNumber: Long?,
			confirmationCount: Int,
			failureCode: String?,
			failureMessage: String?,
			detectedAt: Instant?,
			confirmedAt: Instant?,
			updatedAt: Instant,
		): BlockchainTransaction =
			BlockchainTransaction(
				id = id,
				paymentId = paymentId,
				transactionType = transactionType,
				network = network,
				chainId = chainId,
				transactionHash = transactionHash,
				fromAddress = fromAddress,
				toAddress = toAddress,
				tokenContractAddress = tokenContractAddress,
				tokenAsset = tokenAsset,
				amountMinor = amountMinor,
				requiredConfirmationCount = requiredConfirmationCount,
				submittedAt = submittedAt,
				status = status,
				blockNumber = blockNumber,
				confirmationCount = confirmationCount,
				failureCode = failureCode,
				failureMessage = failureMessage,
				detectedAt = detectedAt,
				confirmedAt = confirmedAt,
				updatedAt = updatedAt,
			)
	}
}
