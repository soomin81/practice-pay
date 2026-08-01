package paytech.practice.pay.application.payment

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import paytech.practice.pay.application.checkout.CheckoutSessionNotFoundException
import paytech.practice.pay.application.port.outbound.BlockchainTransactionRepository
import paytech.practice.pay.application.port.outbound.CheckoutSessionRepository
import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.PaymentRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.domain.blockchain.BlockchainTransaction
import paytech.practice.pay.domain.blockchain.BlockchainTransactionId
import paytech.practice.pay.domain.blockchain.BlockchainTransactionStatus
import paytech.practice.pay.domain.blockchain.ChainId
import paytech.practice.pay.domain.blockchain.ContractAddress
import paytech.practice.pay.domain.blockchain.TransactionHash
import paytech.practice.pay.domain.blockchain.TransactionType
import paytech.practice.pay.domain.checkout.CheckoutSession
import paytech.practice.pay.domain.checkout.CheckoutSessionId
import paytech.practice.pay.domain.checkout.CheckoutSessionStatus
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.Payment
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.HttpUrl
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
private val HASH = TransactionHash("0x" + "d".repeat(64))
private val CS_ID = CheckoutSessionId("cs_test_001")
private val PAYMENT_ID = PaymentId("pay_test_001")

private fun newPaymentReady(): Payment {
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
	return payment
}

private fun newCheckoutSessionWalletConnected(): CheckoutSession {
	val session =
		CheckoutSession.create(
			id = CS_ID,
			paymentId = PAYMENT_ID,
			successUrl = HttpUrl("https://merchant.example.com/success"),
			cancelUrl = null,
			expiresAt = NOW.plusSeconds(1_800),
			createdAt = NOW.minusSeconds(60),
		)
	session.open(NOW.minusSeconds(50))
	session.connectWallet(CUSTOMER_WALLET, NOW.minusSeconds(40))
	return session
}

private class SubmitImmediateTransactionManager : TransactionManager {
	override fun <T> runInTransaction(block: () -> T): T = block()
}

private class SubmitFakeIdGenerator : IdGenerator {
	private var counter = 0

	override fun newId(): String {
		counter += 1
		return "id$counter"
	}
}

private fun newUseCase(
	checkoutSessionRepository: CheckoutSessionRepository,
	paymentRepository: PaymentRepository,
	blockchainTransactionRepository: BlockchainTransactionRepository,
): SubmitPaymentTransactionUseCase =
	SubmitPaymentTransactionUseCase(
		checkoutSessionRepository = checkoutSessionRepository,
		paymentRepository = paymentRepository,
		blockchainTransactionRepository = blockchainTransactionRepository,
		idGenerator = SubmitFakeIdGenerator(),
		transactionManager = SubmitImmediateTransactionManager(),
		clock = FIXED_CLOCK,
	)

class SubmitPaymentTransactionUseCaseTest :
	FunSpec({

		test("creates a SUBMITTED BlockchainTransaction and submits both CheckoutSession and Payment") {
			val checkoutSessionRepository = mockk<CheckoutSessionRepository>(relaxed = true)
			val paymentRepository = mockk<PaymentRepository>(relaxed = true)
			val blockchainTransactionRepository = mockk<BlockchainTransactionRepository>(relaxed = true)
			// Use Case는 paymentId를 얻기 위해 잠그지 않고 한 번, 그다음 잠금으로 한 번 읽는다.
			every { checkoutSessionRepository.findById(CS_ID) } returns newCheckoutSessionWalletConnected()
			every { checkoutSessionRepository.findByIdForUpdate(CS_ID) } returns newCheckoutSessionWalletConnected()
			every { paymentRepository.findByIdForUpdate(PAYMENT_ID) } returns newPaymentReady()
			every { blockchainTransactionRepository.findByNetworkAndTransactionHash(any(), HASH) } returns null

			val result =
				newUseCase(checkoutSessionRepository, paymentRepository, blockchainTransactionRepository).execute(
					SubmitPaymentTransactionCommand(CS_ID, HASH),
				)

			result.checkoutSessionStatus shouldBe CheckoutSessionStatus.PAYMENT_SUBMITTED
			result.paymentStatus shouldBe PaymentStatus.PROCESSING
			verify(exactly = 1) { blockchainTransactionRepository.save(any()) }
			verify(exactly = 1) { checkoutSessionRepository.save(any()) }
			verify(exactly = 1) { paymentRepository.save(any()) }
		}

		test("the created BlockchainTransaction records the customer wallet, receiving wallet and payment amount") {
			val checkoutSessionRepository = mockk<CheckoutSessionRepository>(relaxed = true)
			val paymentRepository = mockk<PaymentRepository>(relaxed = true)
			val blockchainTransactionRepository = mockk<BlockchainTransactionRepository>(relaxed = true)
			val savedTransactions = mutableListOf<BlockchainTransaction>()
			// Use Case는 paymentId를 얻기 위해 잠그지 않고 한 번, 그다음 잠금으로 한 번 읽는다.
			every { checkoutSessionRepository.findById(CS_ID) } returns newCheckoutSessionWalletConnected()
			every { checkoutSessionRepository.findByIdForUpdate(CS_ID) } returns newCheckoutSessionWalletConnected()
			every { paymentRepository.findByIdForUpdate(PAYMENT_ID) } returns newPaymentReady()
			every { blockchainTransactionRepository.findByNetworkAndTransactionHash(any(), HASH) } returns null
			every { blockchainTransactionRepository.save(capture(savedTransactions)) } returns Unit

			newUseCase(checkoutSessionRepository, paymentRepository, blockchainTransactionRepository).execute(
				SubmitPaymentTransactionCommand(CS_ID, HASH),
			)

			val saved = savedTransactions.single()
			saved.status shouldBe BlockchainTransactionStatus.SUBMITTED
			saved.fromAddress shouldBe CUSTOMER_WALLET
			saved.toAddress shouldBe RECEIVING_WALLET
			saved.amountMinor shouldBe TokenAmount(6_666_667)
			saved.chainId shouldBe ChainId(84_532)
			saved.tokenContractAddress shouldBe ContractAddress("0x036CbD53842c5426634e7929541eC2318f3dCF7e")
		}

		test("resubmitting the same hash for the same payment is idempotent") {
			val checkoutSessionRepository = mockk<CheckoutSessionRepository>(relaxed = true)
			val paymentRepository = mockk<PaymentRepository>(relaxed = true)
			val blockchainTransactionRepository = mockk<BlockchainTransactionRepository>()
			val existing =
				BlockchainTransaction.create(
					id = BlockchainTransactionId("btx_existing"),
					paymentId = PAYMENT_ID,
					transactionType = TransactionType.PAYMENT,
					network = BlockchainNetwork.BASE_SEPOLIA,
					chainId = ChainId(84_532),
					transactionHash = HASH,
					fromAddress = CUSTOMER_WALLET,
					toAddress = RECEIVING_WALLET,
					tokenContractAddress = ContractAddress("0x036CbD53842c5426634e7929541eC2318f3dCF7e"),
					tokenAsset = Asset.USDC,
					amountMinor = TokenAmount(6_666_667),
					requiredConfirmationCount = 12,
					submittedAt = NOW.minusSeconds(10),
				)
			// Use Case는 paymentId를 얻기 위해 잠그지 않고 한 번, 그다음 잠금으로 한 번 읽는다.
			every { checkoutSessionRepository.findById(CS_ID) } returns newCheckoutSessionWalletConnected()
			every { checkoutSessionRepository.findByIdForUpdate(CS_ID) } returns newCheckoutSessionWalletConnected()
			every { paymentRepository.findByIdForUpdate(PAYMENT_ID) } returns newPaymentReady()
			every { blockchainTransactionRepository.findByNetworkAndTransactionHash(any(), HASH) } returns existing

			val result =
				newUseCase(checkoutSessionRepository, paymentRepository, blockchainTransactionRepository).execute(
					SubmitPaymentTransactionCommand(CS_ID, HASH),
				)

			result.blockchainTransactionId shouldBe existing.id
			verify(exactly = 0) { blockchainTransactionRepository.save(any()) }
		}

		test("the same hash already used by a different payment throws DuplicateTransactionHashException") {
			val checkoutSessionRepository = mockk<CheckoutSessionRepository>(relaxed = true)
			val paymentRepository = mockk<PaymentRepository>(relaxed = true)
			val blockchainTransactionRepository = mockk<BlockchainTransactionRepository>()
			val existingForOtherPayment =
				BlockchainTransaction.create(
					id = BlockchainTransactionId("btx_other"),
					paymentId = PaymentId("pay_other"),
					transactionType = TransactionType.PAYMENT,
					network = BlockchainNetwork.BASE_SEPOLIA,
					chainId = ChainId(84_532),
					transactionHash = HASH,
					fromAddress = null,
					toAddress = null,
					tokenContractAddress = null,
					tokenAsset = Asset.USDC,
					amountMinor = null,
					requiredConfirmationCount = 12,
					submittedAt = NOW.minusSeconds(10),
				)
			// Use Case는 paymentId를 얻기 위해 잠그지 않고 한 번, 그다음 잠금으로 한 번 읽는다.
			every { checkoutSessionRepository.findById(CS_ID) } returns newCheckoutSessionWalletConnected()
			every { checkoutSessionRepository.findByIdForUpdate(CS_ID) } returns newCheckoutSessionWalletConnected()
			every { paymentRepository.findByIdForUpdate(PAYMENT_ID) } returns newPaymentReady()
			every { blockchainTransactionRepository.findByNetworkAndTransactionHash(any(), HASH) } returns existingForOtherPayment

			shouldThrow<DuplicateTransactionHashException> {
				newUseCase(checkoutSessionRepository, paymentRepository, blockchainTransactionRepository).execute(
					SubmitPaymentTransactionCommand(CS_ID, HASH),
				)
			}
		}

		test("throws CheckoutSessionNotFoundException when the id does not exist") {
			val checkoutSessionRepository = mockk<CheckoutSessionRepository>()
			// Use Case는 paymentId를 얻기 위해 잠그지 않고 한 번, 그다음 잠금으로 한 번 읽는다.
			every { checkoutSessionRepository.findById(CS_ID) } returns null
			every { checkoutSessionRepository.findByIdForUpdate(CS_ID) } returns null

			shouldThrow<CheckoutSessionNotFoundException> {
				newUseCase(checkoutSessionRepository, mockk(), mockk()).execute(SubmitPaymentTransactionCommand(CS_ID, HASH))
			}
		}

		test("throws when the CheckoutSession is not WALLET_CONNECTED") {
			val checkoutSessionRepository = mockk<CheckoutSessionRepository>()
			val paymentRepository = mockk<PaymentRepository>(relaxed = true)
			val blockchainTransactionRepository = mockk<BlockchainTransactionRepository>()
			val session =
				CheckoutSession.create(
					id = CS_ID,
					paymentId = PAYMENT_ID,
					successUrl = HttpUrl("https://merchant.example.com/success"),
					cancelUrl = null,
					expiresAt = NOW.plusSeconds(1_800),
					createdAt = NOW.minusSeconds(60),
				)
			// Use Case는 paymentId를 얻기 위해 잠그지 않고 한 번, 그다음 잠금으로 한 번 읽는다.
			every { checkoutSessionRepository.findById(CS_ID) } returns session
			every { checkoutSessionRepository.findByIdForUpdate(CS_ID) } returns session
			every { paymentRepository.findByIdForUpdate(PAYMENT_ID) } returns newPaymentReady()
			every { blockchainTransactionRepository.findByNetworkAndTransactionHash(any(), HASH) } returns null

			shouldThrow<IllegalStateException> {
				newUseCase(checkoutSessionRepository, paymentRepository, blockchainTransactionRepository).execute(
					SubmitPaymentTransactionCommand(CS_ID, HASH),
				)
			}
		}
	})
