package paytech.practice.pay.application.payment

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import paytech.practice.pay.application.port.outbound.OnChainTokenTransfer
import paytech.practice.pay.application.port.outbound.OnChainTransaction
import paytech.practice.pay.domain.blockchain.BlockchainTransaction
import paytech.practice.pay.domain.blockchain.BlockchainTransactionId
import paytech.practice.pay.domain.blockchain.ChainId
import paytech.practice.pay.domain.blockchain.ContractAddress
import paytech.practice.pay.domain.blockchain.TransactionHash
import paytech.practice.pay.domain.blockchain.TransactionType
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.Payment
import paytech.practice.pay.domain.payment.PaymentFailureReason
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.TokenAmount
import paytech.practice.pay.domain.shared.WalletAddress
import java.time.Instant

private val NOW: Instant = Instant.parse("2026-07-17T00:00:00Z")
private val RECEIVING_WALLET = WalletAddress("0x" + "a".repeat(40))
private val OTHER_WALLET = WalletAddress("0x" + "f".repeat(40))
private val EXPECTED_CONTRACT = ContractAddress("0x" + "c".repeat(40))
private val OTHER_CONTRACT = ContractAddress("0x" + "e".repeat(40))
private val HASH = TransactionHash("0x" + "d".repeat(64))
private val EXPECTED_CHAIN_ID = ChainId(84_532)

private fun newPayment(): Payment =
	Payment.create(
		id = PaymentId("pay_test_001"),
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
		createdAt = NOW,
	)

private fun newBlockchainTransaction(): BlockchainTransaction =
	BlockchainTransaction.create(
		id = BlockchainTransactionId("btx_test_001"),
		paymentId = PaymentId("pay_test_001"),
		transactionType = TransactionType.PAYMENT,
		network = BlockchainNetwork.BASE_SEPOLIA,
		chainId = EXPECTED_CHAIN_ID,
		transactionHash = HASH,
		fromAddress = null,
		toAddress = null,
		tokenContractAddress = null,
		tokenAsset = Asset.USDC,
		amountMinor = null,
		requiredConfirmationCount = 12,
		submittedAt = NOW,
	)

private fun onChainTransaction(
	chainId: ChainId = EXPECTED_CHAIN_ID,
	receiptSucceeded: Boolean = true,
	transfers: List<OnChainTokenTransfer> =
		listOf(OnChainTokenTransfer(EXPECTED_CONTRACT, OTHER_WALLET, RECEIVING_WALLET, TokenAmount(6_666_667))),
): OnChainTransaction =
	OnChainTransaction(
		transactionHash = HASH,
		chainId = chainId,
		blockNumber = 1_000L,
		receiptSucceeded = receiptSucceeded,
		confirmationCount = 12,
		tokenTransfers = transfers,
	)

class PaymentTransactionValidatorTest :
	FunSpec({

		test("a matching transfer on the expected contract/wallet/amount is Valid") {
			val result =
				PaymentTransactionValidator.validate(newPayment(), newBlockchainTransaction(), onChainTransaction(), EXPECTED_CONTRACT)

			result shouldBe PaymentTransactionValidationResult.Valid
		}

		test("a reverted receipt is Invalid(TRANSACTION_RECEIPT_FAILED)") {
			val result =
				PaymentTransactionValidator.validate(
					newPayment(),
					newBlockchainTransaction(),
					onChainTransaction(receiptSucceeded = false),
					EXPECTED_CONTRACT,
				)

			result shouldBe PaymentTransactionValidationResult.Invalid(PaymentFailureReason.TRANSACTION_RECEIPT_FAILED)
		}

		test("a mismatched chain id is Invalid(NETWORK_MISMATCH)") {
			val result =
				PaymentTransactionValidator.validate(
					newPayment(),
					newBlockchainTransaction(),
					onChainTransaction(chainId = ChainId(1)),
					EXPECTED_CONTRACT,
				)

			result shouldBe PaymentTransactionValidationResult.Invalid(PaymentFailureReason.NETWORK_MISMATCH)
		}

		test("no transfer on the expected contract is Invalid(TOKEN_CONTRACT_NOT_ALLOWED)") {
			val transfers = listOf(OnChainTokenTransfer(OTHER_CONTRACT, OTHER_WALLET, RECEIVING_WALLET, TokenAmount(6_666_667)))

			val result =
				PaymentTransactionValidator.validate(
					newPayment(),
					newBlockchainTransaction(),
					onChainTransaction(transfers = transfers),
					EXPECTED_CONTRACT,
				)

			result shouldBe PaymentTransactionValidationResult.Invalid(PaymentFailureReason.TOKEN_CONTRACT_NOT_ALLOWED)
		}

		test("a transfer to the wrong wallet is Invalid(RECEIVING_WALLET_MISMATCH)") {
			val transfers = listOf(OnChainTokenTransfer(EXPECTED_CONTRACT, OTHER_WALLET, OTHER_WALLET, TokenAmount(6_666_667)))

			val result =
				PaymentTransactionValidator.validate(
					newPayment(),
					newBlockchainTransaction(),
					onChainTransaction(transfers = transfers),
					EXPECTED_CONTRACT,
				)

			result shouldBe PaymentTransactionValidationResult.Invalid(PaymentFailureReason.RECEIVING_WALLET_MISMATCH)
		}

		test("an insufficient amount is Invalid(AMOUNT_INSUFFICIENT)") {
			val transfers = listOf(OnChainTokenTransfer(EXPECTED_CONTRACT, OTHER_WALLET, RECEIVING_WALLET, TokenAmount(1)))

			val result =
				PaymentTransactionValidator.validate(
					newPayment(),
					newBlockchainTransaction(),
					onChainTransaction(transfers = transfers),
					EXPECTED_CONTRACT,
				)

			result shouldBe PaymentTransactionValidationResult.Invalid(PaymentFailureReason.AMOUNT_INSUFFICIENT)
		}

		test("wallet address comparison is case-insensitive") {
			val transfers =
				listOf(
					OnChainTokenTransfer(
						ContractAddress(EXPECTED_CONTRACT.value.uppercase().replace("0X", "0x")),
						OTHER_WALLET,
						WalletAddress(RECEIVING_WALLET.value.uppercase().replace("0X", "0x")),
						TokenAmount(6_666_667),
					),
				)

			val result =
				PaymentTransactionValidator.validate(
					newPayment(),
					newBlockchainTransaction(),
					onChainTransaction(transfers = transfers),
					EXPECTED_CONTRACT,
				)

			result shouldBe PaymentTransactionValidationResult.Valid
		}

		test("an amount greater than required is still Valid") {
			val transfers = listOf(OnChainTokenTransfer(EXPECTED_CONTRACT, OTHER_WALLET, RECEIVING_WALLET, TokenAmount(9_999_999)))

			val result =
				PaymentTransactionValidator.validate(
					newPayment(),
					newBlockchainTransaction(),
					onChainTransaction(transfers = transfers),
					EXPECTED_CONTRACT,
				)

			result shouldBe PaymentTransactionValidationResult.Valid
		}
	})
