package paytech.practice.pay.batch.job

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import paytech.practice.pay.application.port.outbound.PaymentRepository
import paytech.practice.pay.application.sweep.ExpireCheckoutCommand
import paytech.practice.pay.application.sweep.ExpireCheckoutUseCase
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.Payment
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.TokenAmount
import paytech.practice.pay.domain.shared.WalletAddress
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private val NOW: Instant = Instant.parse("2026-07-19T00:00:00Z")
private val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
private val RECEIVING_WALLET = WalletAddress("0x" + "a".repeat(40))

private fun payment(id: String): Payment =
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
		expiresAt = NOW.minusSeconds(60),
		createdAt = NOW.minusSeconds(3_600),
	)

class ExpireExpiredCheckoutsTaskletTest :
	FunSpec({

		test("calls the use case once for every expirable payment") {
			val repository = mockk<PaymentRepository>()
			val useCase = mockk<ExpireCheckoutUseCase>(relaxed = true)
			val expirable = listOf(payment("pay1"), payment("pay2"))
			every { repository.findExpirable(NOW) } returns expirable

			val result =
				ExpireExpiredCheckoutsTasklet(repository, useCase, CLOCK)
					.execute(mockk<StepContribution>(), mockk<ChunkContext>())

			result shouldBe RepeatStatus.FINISHED
			expirable.forEach { verify(exactly = 1) { useCase.execute(ExpireCheckoutCommand(it.id)) } }
		}

		test("a failure for one payment does not stop the rest") {
			val repository = mockk<PaymentRepository>()
			val useCase = mockk<ExpireCheckoutUseCase>()
			val failing = payment("pay-failing")
			val succeeding = payment("pay-succeeding")
			every { repository.findExpirable(NOW) } returns listOf(failing, succeeding)
			every { useCase.execute(ExpireCheckoutCommand(failing.id)) } throws IllegalStateException("boom")
			every { useCase.execute(ExpireCheckoutCommand(succeeding.id)) } returns Unit

			val result =
				ExpireExpiredCheckoutsTasklet(repository, useCase, CLOCK)
					.execute(mockk<StepContribution>(), mockk<ChunkContext>())

			result shouldBe RepeatStatus.FINISHED
			verify(exactly = 1) { useCase.execute(ExpireCheckoutCommand(succeeding.id)) }
		}

		test("an empty list is a no-op") {
			val repository = mockk<PaymentRepository>()
			val useCase = mockk<ExpireCheckoutUseCase>()
			every { repository.findExpirable(NOW) } returns emptyList()

			val result =
				ExpireExpiredCheckoutsTasklet(repository, useCase, CLOCK)
					.execute(mockk<StepContribution>(), mockk<ChunkContext>())

			result shouldBe RepeatStatus.FINISHED
			verify(exactly = 0) { useCase.execute(any()) }
		}
	})
