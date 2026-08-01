package paytech.practice.pay.domain.blockchain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.TokenAmount
import paytech.practice.pay.domain.shared.WalletAddress
import java.time.Instant

private val SUBMITTED_AT: Instant = Instant.parse("2026-07-17T00:00:00Z")
private val CUSTOMER_WALLET = WalletAddress("0x" + "a".repeat(40))
private val RECEIVING_WALLET = WalletAddress("0x" + "b".repeat(40))
private val CONTRACT = ContractAddress("0x" + "c".repeat(40))
private val HASH = TransactionHash("0x" + "d".repeat(64))

private fun newTransaction(): BlockchainTransaction =
	BlockchainTransaction.create(
		id = BlockchainTransactionId("btx_test_001"),
		paymentId = PaymentId("pay_test_001"),
		transactionType = TransactionType.PAYMENT,
		network = BlockchainNetwork.BASE_SEPOLIA,
		chainId = ChainId(84_532),
		transactionHash = HASH,
		fromAddress = CUSTOMER_WALLET,
		toAddress = RECEIVING_WALLET,
		tokenContractAddress = CONTRACT,
		tokenAsset = Asset.USDC,
		amountMinor = TokenAmount(72_992_701),
		requiredConfirmationCount = 12,
		submittedAt = SUBMITTED_AT,
	)

class BlockchainTransactionTest :
	FunSpec({

		test("create starts in SUBMITTED with zero confirmations") {
			val tx = newTransaction()

			tx.status shouldBe BlockchainTransactionStatus.SUBMITTED
			tx.confirmationCount shouldBe 0
			tx.blockNumber.shouldBeNull()
			tx.confirmedAt.shouldBeNull()
			tx.updatedAt shouldBe SUBMITTED_AT
		}

		test("create rejects a non-positive requiredConfirmationCount") {
			shouldThrow<IllegalArgumentException> {
				BlockchainTransaction.create(
					id = BlockchainTransactionId("btx_test_002"),
					paymentId = PaymentId("pay_test_001"),
					transactionType = TransactionType.PAYMENT,
					network = BlockchainNetwork.BASE_SEPOLIA,
					chainId = ChainId(84_532),
					transactionHash = HASH,
					fromAddress = null,
					toAddress = null,
					tokenContractAddress = null,
					tokenAsset = Asset.USDC,
					amountMinor = null,
					requiredConfirmationCount = 0,
					submittedAt = SUBMITTED_AT,
				)
			}
		}

		test("create rejects a zero amountMinor") {
			shouldThrow<IllegalArgumentException> {
				BlockchainTransaction.create(
					id = BlockchainTransactionId("btx_test_003"),
					paymentId = PaymentId("pay_test_001"),
					transactionType = TransactionType.PAYMENT,
					network = BlockchainNetwork.BASE_SEPOLIA,
					chainId = ChainId(84_532),
					transactionHash = HASH,
					fromAddress = null,
					toAddress = null,
					tokenContractAddress = null,
					tokenAsset = Asset.USDC,
					amountMinor = TokenAmount.ZERO,
					requiredConfirmationCount = 12,
					submittedAt = SUBMITTED_AT,
				)
			}
		}

		test("detect moves SUBMITTED to DETECTED and records the block number") {
			val tx = newTransaction()
			val detectedAt = SUBMITTED_AT.plusSeconds(10)

			tx.detect(blockNumber = 1_000L, detectedAt = detectedAt)

			tx.status shouldBe BlockchainTransactionStatus.DETECTED
			tx.blockNumber shouldBe 1_000L
			tx.detectedAt shouldBe detectedAt
		}

		test("detect fails when not SUBMITTED") {
			val tx = newTransaction()
			tx.detect(1_000L, SUBMITTED_AT.plusSeconds(10))

			shouldThrow<IllegalStateException> { tx.detect(1_001L, SUBMITTED_AT.plusSeconds(20)) }
		}

		test("startConfirming moves DETECTED to CONFIRMING") {
			val tx = newTransaction()
			tx.detect(1_000L, SUBMITTED_AT.plusSeconds(10))
			val changedAt = SUBMITTED_AT.plusSeconds(20)

			tx.startConfirming(changedAt)

			tx.status shouldBe BlockchainTransactionStatus.CONFIRMING
			tx.updatedAt shouldBe changedAt
		}

		test("recordConfirmation updates the count while CONFIRMING") {
			val tx = newTransaction()
			tx.detect(1_000L, SUBMITTED_AT.plusSeconds(10))
			tx.startConfirming(SUBMITTED_AT.plusSeconds(20))

			tx.recordConfirmation(3, SUBMITTED_AT.plusSeconds(30))

			tx.confirmationCount shouldBe 3
		}

		test("recordConfirmation fails when not CONFIRMING") {
			val tx = newTransaction()

			shouldThrow<IllegalStateException> { tx.recordConfirmation(1, SUBMITTED_AT.plusSeconds(10)) }
		}

		test("confirm moves CONFIRMING to CONFIRMED") {
			val tx = newTransaction()
			tx.detect(1_000L, SUBMITTED_AT.plusSeconds(10))
			tx.startConfirming(SUBMITTED_AT.plusSeconds(20))
			tx.recordConfirmation(12, SUBMITTED_AT.plusSeconds(30))
			val confirmedAt = SUBMITTED_AT.plusSeconds(40)

			tx.confirm(confirmedAt)

			tx.status shouldBe BlockchainTransactionStatus.CONFIRMED
			tx.confirmedAt shouldBe confirmedAt
		}

		test("confirm fails when not CONFIRMING") {
			val tx = newTransaction()

			shouldThrow<IllegalStateException> { tx.confirm(SUBMITTED_AT.plusSeconds(10)) }
		}

		test("fail moves SUBMITTED, DETECTED or CONFIRMING to FAILED") {
			val fromSubmitted = newTransaction()
			fromSubmitted.fail("RECEIPT_FAILED", "reverted", SUBMITTED_AT.plusSeconds(1))
			fromSubmitted.status shouldBe BlockchainTransactionStatus.FAILED
			fromSubmitted.failureCode shouldBe "RECEIPT_FAILED"

			val fromDetected = newTransaction()
			fromDetected.detect(1_000L, SUBMITTED_AT.plusSeconds(1))
			fromDetected.fail("DROPPED", null, SUBMITTED_AT.plusSeconds(2))
			fromDetected.status shouldBe BlockchainTransactionStatus.FAILED

			val fromConfirming = newTransaction()
			fromConfirming.detect(1_000L, SUBMITTED_AT.plusSeconds(1))
			fromConfirming.startConfirming(SUBMITTED_AT.plusSeconds(2))
			fromConfirming.fail(null, null, SUBMITTED_AT.plusSeconds(3))
			fromConfirming.status shouldBe BlockchainTransactionStatus.FAILED
		}

		test("fail fails once CONFIRMED") {
			val tx = newTransaction()
			tx.detect(1_000L, SUBMITTED_AT.plusSeconds(10))
			tx.startConfirming(SUBMITTED_AT.plusSeconds(20))
			tx.confirm(SUBMITTED_AT.plusSeconds(30))

			shouldThrow<IllegalStateException> { tx.fail(null, null, SUBMITTED_AT.plusSeconds(40)) }
		}

		test("markReorged moves DETECTED or CONFIRMING to REORGED") {
			val fromDetected = newTransaction()
			fromDetected.detect(1_000L, SUBMITTED_AT.plusSeconds(1))
			val reorgedAt = SUBMITTED_AT.plusSeconds(2)
			fromDetected.markReorged(reorgedAt)
			fromDetected.status shouldBe BlockchainTransactionStatus.REORGED
			fromDetected.updatedAt shouldBe reorgedAt

			val fromConfirming = newTransaction()
			fromConfirming.detect(1_000L, SUBMITTED_AT.plusSeconds(1))
			fromConfirming.startConfirming(SUBMITTED_AT.plusSeconds(2))
			fromConfirming.markReorged(SUBMITTED_AT.plusSeconds(3))
			fromConfirming.status shouldBe BlockchainTransactionStatus.REORGED
		}

		// 한 번도 블록에서 본 적이 없으면 "사라졌다"가 성립하지 않는다 — 그냥 미채굴이다.
		test("markReorged fails from SUBMITTED") {
			val tx = newTransaction()

			shouldThrow<IllegalStateException> { tx.markReorged(SUBMITTED_AT.plusSeconds(10)) }
		}

		// CONFIRMED 이후의 reorg는 환전·정산까지 되돌려야 해서 MVP 범위 밖이다(ADR-007).
		test("markReorged fails once CONFIRMED") {
			val tx = newTransaction()
			tx.detect(1_000L, SUBMITTED_AT.plusSeconds(10))
			tx.startConfirming(SUBMITTED_AT.plusSeconds(20))
			tx.confirm(SUBMITTED_AT.plusSeconds(30))

			shouldThrow<IllegalStateException> { tx.markReorged(SUBMITTED_AT.plusSeconds(40)) }
		}

		test("reconstitute rejects CONFIRMED without confirmedAt") {
			shouldThrow<IllegalArgumentException> {
				BlockchainTransaction.reconstitute(
					id = BlockchainTransactionId("btx_test_004"),
					paymentId = PaymentId("pay_test_001"),
					transactionType = TransactionType.PAYMENT,
					network = BlockchainNetwork.BASE_SEPOLIA,
					chainId = ChainId(84_532),
					transactionHash = HASH,
					fromAddress = CUSTOMER_WALLET,
					toAddress = RECEIVING_WALLET,
					tokenContractAddress = CONTRACT,
					tokenAsset = Asset.USDC,
					amountMinor = TokenAmount(72_992_701),
					requiredConfirmationCount = 12,
					submittedAt = SUBMITTED_AT,
					status = BlockchainTransactionStatus.CONFIRMED,
					blockNumber = 1_000L,
					confirmationCount = 12,
					failureCode = null,
					failureMessage = null,
					detectedAt = SUBMITTED_AT.plusSeconds(10),
					confirmedAt = null,
					updatedAt = SUBMITTED_AT.plusSeconds(10),
				)
			}
		}

		test("reconstitute restores a CONFIRMED transaction faithfully") {
			val confirmedAt = SUBMITTED_AT.plusSeconds(40)

			val tx =
				BlockchainTransaction.reconstitute(
					id = BlockchainTransactionId("btx_test_005"),
					paymentId = PaymentId("pay_test_001"),
					transactionType = TransactionType.PAYMENT,
					network = BlockchainNetwork.BASE_SEPOLIA,
					chainId = ChainId(84_532),
					transactionHash = HASH,
					fromAddress = CUSTOMER_WALLET,
					toAddress = RECEIVING_WALLET,
					tokenContractAddress = CONTRACT,
					tokenAsset = Asset.USDC,
					amountMinor = TokenAmount(72_992_701),
					requiredConfirmationCount = 12,
					submittedAt = SUBMITTED_AT,
					status = BlockchainTransactionStatus.CONFIRMED,
					blockNumber = 1_000L,
					confirmationCount = 12,
					failureCode = null,
					failureMessage = null,
					detectedAt = SUBMITTED_AT.plusSeconds(10),
					confirmedAt = confirmedAt,
					updatedAt = confirmedAt,
				)

			tx.status shouldBe BlockchainTransactionStatus.CONFIRMED
			tx.confirmedAt shouldBe confirmedAt
			tx.confirmationCount shouldBe 12
		}
	})
