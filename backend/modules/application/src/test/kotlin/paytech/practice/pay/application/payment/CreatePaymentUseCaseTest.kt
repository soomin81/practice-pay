package paytech.practice.pay.application.payment

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import paytech.practice.pay.application.port.outbound.CheckoutSessionRepository
import paytech.practice.pay.application.port.outbound.ExchangeRateProvider
import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.MarketRateQuote
import paytech.practice.pay.application.port.outbound.MerchantRepository
import paytech.practice.pay.application.port.outbound.OutboxEventRepository
import paytech.practice.pay.application.port.outbound.PaymentQuoteRepository
import paytech.practice.pay.application.port.outbound.PaymentRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.domain.checkout.CheckoutSession
import paytech.practice.pay.domain.checkout.CheckoutSessionId
import paytech.practice.pay.domain.merchant.Merchant
import paytech.practice.pay.domain.merchant.MerchantCode
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.Payment
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.ExchangeRate
import paytech.practice.pay.domain.shared.HttpUrl
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.TokenAmount
import paytech.practice.pay.domain.shared.WalletAddress
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private val NOW: Instant = Instant.parse("2026-07-17T00:00:00Z")
private val FIXED_CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
private val RECEIVING_WALLET = WalletAddress("0x" + "a".repeat(40))

private fun activeMerchant(): Merchant =
	Merchant.create(
		id = MerchantId("mrc_test_001"),
		code = MerchantCode("test-merchant"),
		name = "테스트 가맹점",
		webhookUrl = null,
		createdAt = NOW,
	)

private fun newCommand(): CreatePaymentCommand =
	CreatePaymentCommand(
		merchantId = MerchantId("mrc_test_001"),
		merchantOrderId = MerchantOrderId("order-001"),
		orderName = "테스트 주문",
		orderAmount = Money(10_000),
		network = BlockchainNetwork.BASE_SEPOLIA,
		receivingWallet = RECEIVING_WALLET,
		successUrl = HttpUrl("https://merchant.example.com/success"),
		cancelUrl = HttpUrl("https://merchant.example.com/cancel"),
	)

private class ImmediateTransactionManager : TransactionManager {
	override fun <T> runInTransaction(block: () -> T): T = block()
}

private class FakeIdGenerator : IdGenerator {
	private var counter = 0

	override fun newId(): String {
		counter += 1
		return "id$counter"
	}
}

class CreatePaymentUseCaseTest :
	FunSpec({

		test("creates Payment/PaymentQuote/CheckoutSession/OutboxEvent and returns their ids") {
			val merchantRepository = mockk<MerchantRepository>()
			val paymentRepository = mockk<PaymentRepository>(relaxed = true)
			val paymentQuoteRepository = mockk<PaymentQuoteRepository>(relaxed = true)
			val checkoutSessionRepository = mockk<CheckoutSessionRepository>(relaxed = true)
			val outboxEventRepository = mockk<OutboxEventRepository>(relaxed = true)
			val exchangeRateProvider = mockk<ExchangeRateProvider>()

			every { merchantRepository.findById(MerchantId("mrc_test_001")) } returns activeMerchant()
			every { paymentRepository.findByMerchantOrderId(any(), any()) } returns null
			every { exchangeRateProvider.currentRate() } returns
				MarketRateQuote(providerCode = "fake-market", rate = ExchangeRate(BigDecimal("1500")), quotedAt = NOW)

			val useCase =
				CreatePaymentUseCase(
					merchantRepository = merchantRepository,
					paymentRepository = paymentRepository,
					paymentQuoteRepository = paymentQuoteRepository,
					checkoutSessionRepository = checkoutSessionRepository,
					outboxEventRepository = outboxEventRepository,
					exchangeRateProvider = exchangeRateProvider,
					idGenerator = FakeIdGenerator(),
					transactionManager = ImmediateTransactionManager(),
					clock = FIXED_CLOCK,
				)

			val result = useCase.execute(newCommand())

			result.paymentId shouldBe PaymentId("pay_id1")
			result.checkoutSessionId shouldBe CheckoutSessionId("cs_id3")

			verify(exactly = 1) { paymentRepository.save(any()) }
			verify(exactly = 1) { paymentQuoteRepository.save(any()) }
			verify(exactly = 1) { checkoutSessionRepository.save(any()) }
			verify(exactly = 1) { outboxEventRepository.save(any()) }
		}

		test("saved Payment is already READY, not CREATED") {
			val merchantRepository = mockk<MerchantRepository>()
			val paymentRepository = mockk<PaymentRepository>(relaxed = true)
			val paymentQuoteRepository = mockk<PaymentQuoteRepository>(relaxed = true)
			val checkoutSessionRepository = mockk<CheckoutSessionRepository>(relaxed = true)
			val outboxEventRepository = mockk<OutboxEventRepository>(relaxed = true)
			val exchangeRateProvider = mockk<ExchangeRateProvider>()
			val savedPayments = mutableListOf<Payment>()

			every { merchantRepository.findById(any()) } returns activeMerchant()
			every { paymentRepository.findByMerchantOrderId(any(), any()) } returns null
			every { paymentRepository.save(capture(savedPayments)) } returns Unit
			every { exchangeRateProvider.currentRate() } returns
				MarketRateQuote(providerCode = "fake-market", rate = ExchangeRate(BigDecimal("1500")), quotedAt = NOW)

			val useCase =
				CreatePaymentUseCase(
					merchantRepository = merchantRepository,
					paymentRepository = paymentRepository,
					paymentQuoteRepository = paymentQuoteRepository,
					checkoutSessionRepository = checkoutSessionRepository,
					outboxEventRepository = outboxEventRepository,
					exchangeRateProvider = exchangeRateProvider,
					idGenerator = FakeIdGenerator(),
					transactionManager = ImmediateTransactionManager(),
					clock = FIXED_CLOCK,
				)

			useCase.execute(newCommand())

			savedPayments.single().status.name shouldBe "READY"
		}

		test("throws MerchantNotFoundException when the merchant does not exist") {
			val merchantRepository = mockk<MerchantRepository>()
			every { merchantRepository.findById(any()) } returns null

			val useCase =
				CreatePaymentUseCase(
					merchantRepository = merchantRepository,
					paymentRepository = mockk(),
					paymentQuoteRepository = mockk(),
					checkoutSessionRepository = mockk(),
					outboxEventRepository = mockk(),
					exchangeRateProvider = mockk(),
					idGenerator = FakeIdGenerator(),
					transactionManager = ImmediateTransactionManager(),
					clock = FIXED_CLOCK,
				)

			shouldThrow<MerchantNotFoundException> { useCase.execute(newCommand()) }
		}

		test("throws MerchantCannotAcceptPaymentsException when the merchant cannot accept payments") {
			val merchantRepository = mockk<MerchantRepository>()
			val suspendedMerchant = activeMerchant().apply { suspend(NOW) }
			every { merchantRepository.findById(any()) } returns suspendedMerchant

			val useCase =
				CreatePaymentUseCase(
					merchantRepository = merchantRepository,
					paymentRepository = mockk(),
					paymentQuoteRepository = mockk(),
					checkoutSessionRepository = mockk(),
					outboxEventRepository = mockk(),
					exchangeRateProvider = mockk(),
					idGenerator = FakeIdGenerator(),
					transactionManager = ImmediateTransactionManager(),
					clock = FIXED_CLOCK,
				)

			shouldThrow<MerchantCannotAcceptPaymentsException> { useCase.execute(newCommand()) }
		}

		test("replays the existing result idempotently instead of creating a duplicate") {
			val merchantRepository = mockk<MerchantRepository>()
			val paymentRepository = mockk<PaymentRepository>()
			val checkoutSessionRepository = mockk<CheckoutSessionRepository>()
			val exchangeRateProvider = mockk<ExchangeRateProvider>()

			val existingPayment =
				Payment.create(
					id = PaymentId("pay_existing"),
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
			val existingSession =
				CheckoutSession.create(
					id = CheckoutSessionId("cs_existing"),
					paymentId = existingPayment.id,
					successUrl = HttpUrl("https://merchant.example.com/success"),
					cancelUrl = null,
					expiresAt = NOW.plusSeconds(1_800),
					createdAt = NOW,
				)

			every { merchantRepository.findById(any()) } returns activeMerchant()
			every { paymentRepository.findByMerchantOrderId(any(), any()) } returns existingPayment
			every { checkoutSessionRepository.findByPaymentId(existingPayment.id) } returns existingSession

			val useCase =
				CreatePaymentUseCase(
					merchantRepository = merchantRepository,
					paymentRepository = paymentRepository,
					paymentQuoteRepository = mockk(),
					checkoutSessionRepository = checkoutSessionRepository,
					outboxEventRepository = mockk(),
					exchangeRateProvider = exchangeRateProvider,
					idGenerator = FakeIdGenerator(),
					transactionManager = ImmediateTransactionManager(),
					clock = FIXED_CLOCK,
				)

			val result = useCase.execute(newCommand())

			result.paymentId shouldBe existingPayment.id
			result.checkoutSessionId shouldBe existingSession.id
			verify(exactly = 0) { exchangeRateProvider.currentRate() }
		}
	})
