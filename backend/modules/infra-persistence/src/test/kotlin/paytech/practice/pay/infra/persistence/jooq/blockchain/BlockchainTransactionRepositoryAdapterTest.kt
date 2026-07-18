package paytech.practice.pay.infra.persistence.jooq.blockchain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
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
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.TokenAmount
import paytech.practice.pay.domain.shared.WalletAddress
import paytech.practice.pay.infra.persistence.jooq.PersistenceTestSupport
import paytech.practice.pay.infra.persistence.jooq.insertTestMerchant
import paytech.practice.pay.infra.persistence.jooq.payment.PaymentRepositoryAdapter
import paytech.practice.pay.infra.persistence.jooq.uniqueSuffix
import java.time.Instant

private val NOW: Instant = Instant.parse("2026-07-17T00:00:00Z")
private val RECEIVING_WALLET = WalletAddress("0x" + "a".repeat(40))
private val CUSTOMER_WALLET = WalletAddress("0x" + "b".repeat(40))
private val CONTRACT = ContractAddress("0x" + "c".repeat(40))

private fun savedPayment(): PaymentId {
	val merchantId = MerchantId(insertTestMerchant())
	val payment =
		Payment.create(
			id = PaymentId("pay_${uniqueSuffix()}"),
			merchantId = merchantId,
			merchantOrderId = MerchantOrderId("order-${uniqueSuffix()}"),
			orderName = "테스트 주문",
			orderAmount = Money(10_000),
			paymentAsset = Asset.USDC,
			paymentAmount = TokenAmount(6_666_667),
			tokenDecimals = 6,
			network = BlockchainNetwork.BASE_SEPOLIA,
			receivingWallet = RECEIVING_WALLET,
			expiresAt = NOW.plusSeconds(1_800),
			createdAt = NOW,
		)
	PaymentRepositoryAdapter(PersistenceTestSupport.dsl).save(payment)
	return payment.id
}

private fun newTransaction(paymentId: PaymentId): BlockchainTransaction =
	BlockchainTransaction.create(
		id = BlockchainTransactionId("btx_${uniqueSuffix()}"),
		paymentId = paymentId,
		transactionType = TransactionType.PAYMENT,
		network = BlockchainNetwork.BASE_SEPOLIA,
		chainId = ChainId(84_532),
		transactionHash = TransactionHash("0x" + uniqueSuffix().padEnd(64, '0')),
		fromAddress = CUSTOMER_WALLET,
		toAddress = RECEIVING_WALLET,
		tokenContractAddress = CONTRACT,
		tokenAsset = Asset.USDC,
		amountMinor = TokenAmount(6_666_667),
		requiredConfirmationCount = 12,
		submittedAt = NOW,
	)

class BlockchainTransactionRepositoryAdapterTest :
	FunSpec({
		val adapter = BlockchainTransactionRepositoryAdapter(PersistenceTestSupport.dsl)

		test("save inserts a new BlockchainTransaction and findById round-trips it") {
			val tx = newTransaction(savedPayment())

			adapter.save(tx)
			val found = adapter.findById(tx.id)

			found.shouldNotBeNull()
			found.id shouldBe tx.id
			found.paymentId shouldBe tx.paymentId
			found.status shouldBe BlockchainTransactionStatus.SUBMITTED
			found.chainId shouldBe ChainId(84_532)
			found.tokenContractAddress shouldBe CONTRACT
			found.requiredConfirmationCount shouldBe 12
		}

		test("save persists a state transition on an existing BlockchainTransaction") {
			val tx = newTransaction(savedPayment())
			adapter.save(tx)

			tx.detect(blockNumber = 1_000L, detectedAt = NOW.plusSeconds(10))
			tx.startConfirming(NOW.plusSeconds(20))
			tx.recordConfirmation(5, NOW.plusSeconds(30))
			adapter.save(tx)

			val found = adapter.findById(tx.id)
			found.shouldNotBeNull()
			found.status shouldBe BlockchainTransactionStatus.CONFIRMING
			found.blockNumber shouldBe 1_000L
			found.confirmationCount shouldBe 5
		}

		test("findById returns null when no such transaction exists") {
			adapter.findById(BlockchainTransactionId("btx_no-such-transaction")).shouldBeNull()
		}

		test("save inserts a new BlockchainTransaction and findByNetworkAndTransactionHash round-trips it") {
			val tx = newTransaction(savedPayment())

			adapter.save(tx)
			val found = adapter.findByNetworkAndTransactionHash(BlockchainNetwork.BASE_SEPOLIA, tx.transactionHash)

			found.shouldNotBeNull()
			found.id shouldBe tx.id
		}

		test("findByNetworkAndTransactionHash returns null when no such hash exists") {
			val noSuchHash = TransactionHash("0x" + uniqueSuffix().padEnd(64, '0'))

			adapter.findByNetworkAndTransactionHash(BlockchainNetwork.BASE_SEPOLIA, noSuchHash).shouldBeNull()
		}

		test("findPendingConfirmation includes SUBMITTED/DETECTED/CONFIRMING but not CONFIRMED") {
			val submitted = newTransaction(savedPayment())
			adapter.save(submitted)

			val detected = newTransaction(savedPayment())
			detected.detect(1_000L, NOW.plusSeconds(10))
			adapter.save(detected)

			val confirming = newTransaction(savedPayment())
			confirming.detect(1_000L, NOW.plusSeconds(10))
			confirming.startConfirming(NOW.plusSeconds(20))
			adapter.save(confirming)

			val confirmed = newTransaction(savedPayment())
			confirmed.detect(1_000L, NOW.plusSeconds(10))
			confirmed.startConfirming(NOW.plusSeconds(20))
			confirmed.recordConfirmation(12, NOW.plusSeconds(30))
			confirmed.confirm(NOW.plusSeconds(40))
			adapter.save(confirmed)

			val pendingIds = adapter.findPendingConfirmation().map { it.id }

			pendingIds shouldContain submitted.id
			pendingIds shouldContain detected.id
			pendingIds shouldContain confirming.id
			pendingIds shouldNotContain confirmed.id
		}
	})
