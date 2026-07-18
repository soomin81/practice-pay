package paytech.practice.pay.batch.job

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import paytech.practice.pay.application.exchange.SellToFakeExchangeCommand
import paytech.practice.pay.application.exchange.SellToFakeExchangeUseCase
import paytech.practice.pay.application.port.outbound.PaymentRepository
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.Payment
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.TokenAmount
import paytech.practice.pay.domain.shared.WalletAddress
import java.time.Instant

private val NOW: Instant = Instant.parse("2026-07-17T00:00:00Z")
private val RECEIVING_WALLET = WalletAddress("0x" + "a".repeat(40))

private fun newPayment(id: String): Payment =
	Payment.create(
		id = PaymentId(id),
		merchantId = MerchantId("mrc_test_001"),
		merchantOrderId = MerchantOrderId("order_$id"),
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

class SellPendingPaymentsToFakeExchangeTaskletTest :
	FunSpec({

		test("calls the use case once for every pending Payment") {
			val paymentRepository = mockk<PaymentRepository>()
			val sellToFakeExchangeUseCase = mockk<SellToFakeExchangeUseCase>(relaxed = true)
			val pending = listOf(newPayment("pay1"), newPayment("pay2"), newPayment("pay3"))
			every { paymentRepository.findPendingExchangeSettlement() } returns pending

			val result =
				SellPendingPaymentsToFakeExchangeTasklet(paymentRepository, sellToFakeExchangeUseCase)
					.execute(mockk<StepContribution>(), mockk<ChunkContext>())

			result shouldBe RepeatStatus.FINISHED
			pending.forEach { payment ->
				verify(exactly = 1) { sellToFakeExchangeUseCase.execute(SellToFakeExchangeCommand(payment.id)) }
			}
		}

		test("a failure for one payment does not stop the rest from being processed") {
			val paymentRepository = mockk<PaymentRepository>()
			val sellToFakeExchangeUseCase = mockk<SellToFakeExchangeUseCase>()
			val failing = newPayment("pay-failing")
			val succeeding = newPayment("pay-succeeding")
			every { paymentRepository.findPendingExchangeSettlement() } returns listOf(failing, succeeding)
			every { sellToFakeExchangeUseCase.execute(SellToFakeExchangeCommand(failing.id)) } throws
				IllegalStateException("boom")
			every { sellToFakeExchangeUseCase.execute(SellToFakeExchangeCommand(succeeding.id)) } returns mockk()

			val result =
				SellPendingPaymentsToFakeExchangeTasklet(paymentRepository, sellToFakeExchangeUseCase)
					.execute(mockk<StepContribution>(), mockk<ChunkContext>())

			result shouldBe RepeatStatus.FINISHED
			verify(exactly = 1) { sellToFakeExchangeUseCase.execute(SellToFakeExchangeCommand(succeeding.id)) }
		}

		test("an empty pending list is a no-op") {
			val paymentRepository = mockk<PaymentRepository>()
			val sellToFakeExchangeUseCase = mockk<SellToFakeExchangeUseCase>()
			every { paymentRepository.findPendingExchangeSettlement() } returns emptyList()

			val result =
				SellPendingPaymentsToFakeExchangeTasklet(paymentRepository, sellToFakeExchangeUseCase)
					.execute(mockk<StepContribution>(), mockk<ChunkContext>())

			result shouldBe RepeatStatus.FINISHED
			verify(exactly = 0) { sellToFakeExchangeUseCase.execute(any()) }
		}
	})
