package paytech.practice.pay.batch.job

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import paytech.practice.pay.application.payment.ConfirmBlockchainTransactionCommand
import paytech.practice.pay.application.payment.ConfirmBlockchainTransactionUseCase
import paytech.practice.pay.application.port.outbound.BlockchainTransactionRepository
import paytech.practice.pay.domain.blockchain.BlockchainTransaction
import paytech.practice.pay.domain.blockchain.BlockchainTransactionId
import paytech.practice.pay.domain.blockchain.ChainId
import paytech.practice.pay.domain.blockchain.TransactionHash
import paytech.practice.pay.domain.blockchain.TransactionType
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.TokenAmount
import java.time.Instant

private val NOW: Instant = Instant.parse("2026-07-17T00:00:00Z")

private fun newTransaction(id: String): BlockchainTransaction =
	BlockchainTransaction.create(
		id = BlockchainTransactionId(id),
		paymentId = PaymentId("pay_$id"),
		transactionType = TransactionType.PAYMENT,
		network = BlockchainNetwork.BASE_SEPOLIA,
		chainId = ChainId(84_532),
		transactionHash = TransactionHash("0x" + Integer.toHexString(id.hashCode()).padStart(64, '0')),
		fromAddress = null,
		toAddress = null,
		tokenContractAddress = null,
		tokenAsset = Asset.USDC,
		amountMinor = TokenAmount(1),
		requiredConfirmationCount = 12,
		submittedAt = NOW,
	)

class ConfirmPendingBlockchainTransactionsTaskletTest :
	FunSpec({

		test("calls the use case once for every pending BlockchainTransaction") {
			val blockchainTransactionRepository = mockk<BlockchainTransactionRepository>()
			val confirmBlockchainTransactionUseCase = mockk<ConfirmBlockchainTransactionUseCase>(relaxed = true)
			val pending = listOf(newTransaction("btx1"), newTransaction("btx2"), newTransaction("btx3"))
			every { blockchainTransactionRepository.findPendingConfirmation() } returns pending

			val result =
				ConfirmPendingBlockchainTransactionsTasklet(blockchainTransactionRepository, confirmBlockchainTransactionUseCase)
					.execute(mockk<StepContribution>(), mockk<ChunkContext>())

			result shouldBe RepeatStatus.FINISHED
			pending.forEach { tx ->
				verify(exactly = 1) { confirmBlockchainTransactionUseCase.execute(ConfirmBlockchainTransactionCommand(tx.id)) }
			}
		}

		test("a failure for one transaction does not stop the rest from being processed") {
			val blockchainTransactionRepository = mockk<BlockchainTransactionRepository>()
			val confirmBlockchainTransactionUseCase = mockk<ConfirmBlockchainTransactionUseCase>()
			val failing = newTransaction("btx-failing")
			val succeeding = newTransaction("btx-succeeding")
			every { blockchainTransactionRepository.findPendingConfirmation() } returns listOf(failing, succeeding)
			every { confirmBlockchainTransactionUseCase.execute(ConfirmBlockchainTransactionCommand(failing.id)) } throws
				IllegalStateException("boom")
			every { confirmBlockchainTransactionUseCase.execute(ConfirmBlockchainTransactionCommand(succeeding.id)) } returns mockk()

			val result =
				ConfirmPendingBlockchainTransactionsTasklet(blockchainTransactionRepository, confirmBlockchainTransactionUseCase)
					.execute(mockk<StepContribution>(), mockk<ChunkContext>())

			result shouldBe RepeatStatus.FINISHED
			verify(exactly = 1) { confirmBlockchainTransactionUseCase.execute(ConfirmBlockchainTransactionCommand(succeeding.id)) }
		}

		test("an empty pending list is a no-op") {
			val blockchainTransactionRepository = mockk<BlockchainTransactionRepository>()
			val confirmBlockchainTransactionUseCase = mockk<ConfirmBlockchainTransactionUseCase>()
			every { blockchainTransactionRepository.findPendingConfirmation() } returns emptyList()

			val result =
				ConfirmPendingBlockchainTransactionsTasklet(blockchainTransactionRepository, confirmBlockchainTransactionUseCase)
					.execute(mockk<StepContribution>(), mockk<ChunkContext>())

			result shouldBe RepeatStatus.FINISHED
			verify(exactly = 0) { confirmBlockchainTransactionUseCase.execute(any()) }
		}
	})
