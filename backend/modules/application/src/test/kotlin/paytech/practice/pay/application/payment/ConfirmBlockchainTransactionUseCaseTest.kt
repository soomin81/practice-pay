package paytech.practice.pay.application.payment

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import paytech.practice.pay.application.port.outbound.BlockchainClient
import paytech.practice.pay.application.port.outbound.BlockchainTransactionRepository
import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.OnChainTokenTransfer
import paytech.practice.pay.application.port.outbound.OnChainTransaction
import paytech.practice.pay.application.port.outbound.OutboxEventRepository
import paytech.practice.pay.application.port.outbound.PaymentRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.domain.blockchain.BlockchainTransaction
import paytech.practice.pay.domain.blockchain.BlockchainTransactionId
import paytech.practice.pay.domain.blockchain.BlockchainTransactionStatus
import paytech.practice.pay.domain.blockchain.ChainId
import paytech.practice.pay.domain.blockchain.ContractAddress
import paytech.practice.pay.domain.blockchain.TransactionHash
import paytech.practice.pay.domain.blockchain.TransactionType
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.Payment
import paytech.practice.pay.domain.payment.PaymentFailureReason
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.TokenAmount
import paytech.practice.pay.domain.shared.WalletAddress
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private val NOW: Instant = Instant.parse("2026-07-17T00:00:00Z")
private val FIXED_CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
private val RECEIVING_WALLET = WalletAddress("0x" + "a".repeat(40))
private val CUSTOMER_WALLET = WalletAddress("0x" + "b".repeat(40))
private val USDC_CONTRACT = ContractAddress("0x036CbD53842c5426634e7929541eC2318f3dCF7e")
private val HASH = TransactionHash("0x" + "d".repeat(64))
private val BTX_ID = BlockchainTransactionId("btx_test_001")
private val PAYMENT_ID = PaymentId("pay_test_001")

private fun newPaymentProcessing(): Payment {
	val payment =
		Payment.create(
			id = PAYMENT_ID,
			merchantId = MerchantId("mrc_test_001"),
			merchantOrderId = MerchantOrderId("order-001"),
			orderName = "테스트 주문",
			orderAmount = Money(10_000),
			paymentAsset = Asset.USDC,
			paymentAmount = TokenAmount(6_666_667),
			tokenDecimals = 6,
			network = BlockchainNetwork.BASE_SEPOLIA,
			receivingWallet = RECEIVING_WALLET,
			expiresAt = NOW.plusSeconds(1_800),
			createdAt = NOW.minusSeconds(60),
		)
	payment.ready(NOW.minusSeconds(50))
	payment.submit(CUSTOMER_WALLET, NOW.minusSeconds(40))
	return payment
}

private fun newBlockchainTransactionSubmitted(requiredConfirmationCount: Int = 12): BlockchainTransaction =
	BlockchainTransaction.create(
		id = BTX_ID,
		paymentId = PAYMENT_ID,
		transactionType = TransactionType.PAYMENT,
		network = BlockchainNetwork.BASE_SEPOLIA,
		chainId = ChainId(84_532),
		transactionHash = HASH,
		fromAddress = null,
		toAddress = null,
		tokenContractAddress = null,
		tokenAsset = Asset.USDC,
		amountMinor = null,
		requiredConfirmationCount = requiredConfirmationCount,
		submittedAt = NOW.minusSeconds(30),
	)

private fun onChainTransaction(
	confirmationCount: Int,
	receiptSucceeded: Boolean = true,
	transfers: List<OnChainTokenTransfer> =
		listOf(OnChainTokenTransfer(USDC_CONTRACT, CUSTOMER_WALLET, RECEIVING_WALLET, TokenAmount(6_666_667))),
): OnChainTransaction =
	OnChainTransaction(
		transactionHash = HASH,
		chainId = ChainId(84_532),
		blockNumber = 1_000L,
		receiptSucceeded = receiptSucceeded,
		confirmationCount = confirmationCount,
		tokenTransfers = transfers,
	)

private class ConfirmImmediateTransactionManager : TransactionManager {
	override fun <T> runInTransaction(block: () -> T): T = block()
}

private class ConfirmFakeIdGenerator : IdGenerator {
	private var counter = 0

	override fun newId(): String {
		counter += 1
		return "id$counter"
	}
}

private fun newUseCase(
	blockchainTransactionRepository: BlockchainTransactionRepository,
	paymentRepository: PaymentRepository,
	blockchainClient: BlockchainClient,
	outboxEventRepository: OutboxEventRepository = mockk(relaxed = true),
): ConfirmBlockchainTransactionUseCase =
	ConfirmBlockchainTransactionUseCase(
		blockchainTransactionRepository = blockchainTransactionRepository,
		paymentRepository = paymentRepository,
		outboxEventRepository = outboxEventRepository,
		blockchainClient = blockchainClient,
		idGenerator = ConfirmFakeIdGenerator(),
		transactionManager = ConfirmImmediateTransactionManager(),
		clock = FIXED_CLOCK,
	)

class ConfirmBlockchainTransactionUseCaseTest :
	FunSpec({

		test("not yet found on-chain leaves the transaction and payment untouched") {
			val blockchainTransactionRepository = mockk<BlockchainTransactionRepository>(relaxed = true)
			val paymentRepository = mockk<PaymentRepository>(relaxed = true)
			val blockchainClient = mockk<BlockchainClient>()
			every { blockchainTransactionRepository.findById(BTX_ID) } returns newBlockchainTransactionSubmitted()
			every { paymentRepository.findById(PAYMENT_ID) } returns newPaymentProcessing()
			every { blockchainClient.findTransaction(BlockchainNetwork.BASE_SEPOLIA, HASH) } returns null

			val result =
				newUseCase(blockchainTransactionRepository, paymentRepository, blockchainClient).execute(
					ConfirmBlockchainTransactionCommand(BTX_ID),
				)

			result.blockchainTransactionStatus shouldBe BlockchainTransactionStatus.SUBMITTED
			result.paymentStatus shouldBe PaymentStatus.PROCESSING
			verify(exactly = 0) { blockchainTransactionRepository.save(any()) }
			verify(exactly = 0) { paymentRepository.save(any()) }
		}

		test("detected but not enough confirmations moves to CONFIRMING without succeeding") {
			val blockchainTransactionRepository = mockk<BlockchainTransactionRepository>(relaxed = true)
			val paymentRepository = mockk<PaymentRepository>(relaxed = true)
			val blockchainClient = mockk<BlockchainClient>()
			every { blockchainTransactionRepository.findById(BTX_ID) } returns newBlockchainTransactionSubmitted()
			every { paymentRepository.findById(PAYMENT_ID) } returns newPaymentProcessing()
			every { blockchainClient.findTransaction(BlockchainNetwork.BASE_SEPOLIA, HASH) } returns onChainTransaction(confirmationCount = 3)

			val result =
				newUseCase(blockchainTransactionRepository, paymentRepository, blockchainClient).execute(
					ConfirmBlockchainTransactionCommand(BTX_ID),
				)

			result.blockchainTransactionStatus shouldBe BlockchainTransactionStatus.CONFIRMING
			result.paymentStatus shouldBe PaymentStatus.CONFIRMING
			verify(exactly = 1) { blockchainTransactionRepository.save(any()) }
			verify(exactly = 1) { paymentRepository.save(any()) }
		}

		test("enough confirmations on the first poll confirms the transaction and succeeds the payment") {
			val blockchainTransactionRepository = mockk<BlockchainTransactionRepository>(relaxed = true)
			val paymentRepository = mockk<PaymentRepository>(relaxed = true)
			val blockchainClient = mockk<BlockchainClient>()
			val outboxEventRepository = mockk<OutboxEventRepository>(relaxed = true)
			every { blockchainTransactionRepository.findById(BTX_ID) } returns newBlockchainTransactionSubmitted(requiredConfirmationCount = 3)
			every { paymentRepository.findById(PAYMENT_ID) } returns newPaymentProcessing()
			every { blockchainClient.findTransaction(BlockchainNetwork.BASE_SEPOLIA, HASH) } returns onChainTransaction(confirmationCount = 5)

			val result =
				newUseCase(blockchainTransactionRepository, paymentRepository, blockchainClient, outboxEventRepository).execute(
					ConfirmBlockchainTransactionCommand(BTX_ID),
				)

			result.blockchainTransactionStatus shouldBe BlockchainTransactionStatus.CONFIRMED
			result.paymentStatus shouldBe PaymentStatus.SUCCEEDED
			verify(exactly = 1) { outboxEventRepository.save(any()) }
		}

		test("a resumed CONFIRMING poll that now has enough confirmations succeeds without re-detecting") {
			val tx = newBlockchainTransactionSubmitted(requiredConfirmationCount = 3)
			tx.detect(1_000L, NOW.minusSeconds(20))
			tx.startConfirming(NOW.minusSeconds(10))
			val payment = newPaymentProcessing()
			payment.startConfirmation(NOW.minusSeconds(10))
			val blockchainTransactionRepository = mockk<BlockchainTransactionRepository>(relaxed = true)
			val paymentRepository = mockk<PaymentRepository>(relaxed = true)
			val blockchainClient = mockk<BlockchainClient>()
			every { blockchainTransactionRepository.findById(BTX_ID) } returns tx
			every { paymentRepository.findById(PAYMENT_ID) } returns payment
			every { blockchainClient.findTransaction(BlockchainNetwork.BASE_SEPOLIA, HASH) } returns onChainTransaction(confirmationCount = 4)

			val result =
				newUseCase(blockchainTransactionRepository, paymentRepository, blockchainClient).execute(
					ConfirmBlockchainTransactionCommand(BTX_ID),
				)

			result.blockchainTransactionStatus shouldBe BlockchainTransactionStatus.CONFIRMED
			result.paymentStatus shouldBe PaymentStatus.SUCCEEDED
		}

		test("a reverted receipt fails both aggregates with TRANSACTION_RECEIPT_FAILED") {
			val blockchainTransactionRepository = mockk<BlockchainTransactionRepository>(relaxed = true)
			val paymentRepository = mockk<PaymentRepository>(relaxed = true)
			val blockchainClient = mockk<BlockchainClient>()
			every { blockchainTransactionRepository.findById(BTX_ID) } returns newBlockchainTransactionSubmitted()
			every { paymentRepository.findById(PAYMENT_ID) } returns newPaymentProcessing()
			every { blockchainClient.findTransaction(BlockchainNetwork.BASE_SEPOLIA, HASH) } returns
				onChainTransaction(confirmationCount = 12, receiptSucceeded = false)

			val result =
				newUseCase(blockchainTransactionRepository, paymentRepository, blockchainClient).execute(
					ConfirmBlockchainTransactionCommand(BTX_ID),
				)

			result.blockchainTransactionStatus shouldBe BlockchainTransactionStatus.FAILED
			result.paymentStatus shouldBe PaymentStatus.FAILED
		}

		test("an amount mismatch fails both aggregates with the validator's reason") {
			val blockchainTransactionRepository = mockk<BlockchainTransactionRepository>(relaxed = true)
			val paymentRepository = mockk<PaymentRepository>(relaxed = true)
			val blockchainClient = mockk<BlockchainClient>()
			val savedPayments = mutableListOf<Payment>()
			every { blockchainTransactionRepository.findById(BTX_ID) } returns newBlockchainTransactionSubmitted()
			every { paymentRepository.findById(PAYMENT_ID) } returns newPaymentProcessing()
			every { paymentRepository.save(capture(savedPayments)) } returns Unit
			val insufficientTransfer = OnChainTokenTransfer(USDC_CONTRACT, CUSTOMER_WALLET, RECEIVING_WALLET, TokenAmount(1))
			every { blockchainClient.findTransaction(BlockchainNetwork.BASE_SEPOLIA, HASH) } returns
				onChainTransaction(confirmationCount = 12, transfers = listOf(insufficientTransfer))

			newUseCase(blockchainTransactionRepository, paymentRepository, blockchainClient).execute(
				ConfirmBlockchainTransactionCommand(BTX_ID),
			)

			savedPayments.single().failureReason shouldBe PaymentFailureReason.AMOUNT_INSUFFICIENT
		}

		test("throws BlockchainTransactionNotFoundException when the id does not exist") {
			val blockchainTransactionRepository = mockk<BlockchainTransactionRepository>()
			every { blockchainTransactionRepository.findById(BTX_ID) } returns null

			shouldThrow<BlockchainTransactionNotFoundException> {
				newUseCase(blockchainTransactionRepository, mockk(), mockk()).execute(ConfirmBlockchainTransactionCommand(BTX_ID))
			}
		}

		test("throws when the BlockchainTransaction is already in a terminal state") {
			val tx = newBlockchainTransactionSubmitted()
			tx.fail(null, null, NOW.minusSeconds(5))
			val blockchainTransactionRepository = mockk<BlockchainTransactionRepository>()
			every { blockchainTransactionRepository.findById(BTX_ID) } returns tx

			shouldThrow<IllegalStateException> {
				newUseCase(blockchainTransactionRepository, mockk(), mockk()).execute(ConfirmBlockchainTransactionCommand(BTX_ID))
			}
		}
	})
