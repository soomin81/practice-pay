package paytech.practice.pay.application.exchange

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import paytech.practice.pay.application.port.outbound.ExchangeOrderRepository
import paytech.practice.pay.application.port.outbound.ExchangeRateProvider
import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.MarketRateQuote
import paytech.practice.pay.application.port.outbound.OutboxEventRepository
import paytech.practice.pay.application.port.outbound.PaymentRepository
import paytech.practice.pay.application.port.outbound.SettlementReceivableRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.domain.exchange.ClientOrderId
import paytech.practice.pay.domain.exchange.ExchangeOrder
import paytech.practice.pay.domain.exchange.ExchangeOrderId
import paytech.practice.pay.domain.exchange.ExchangeOrderStatus
import paytech.practice.pay.domain.exchange.OrderSide
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.Payment
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.settlement.SettlementReceivable
import paytech.practice.pay.domain.settlement.SettlementReceivableId
import paytech.practice.pay.domain.settlement.SettlementReceivableStatus
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.ExchangeRate
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.SignedMoney
import paytech.practice.pay.domain.shared.TokenAmount
import paytech.practice.pay.domain.shared.WalletAddress
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val NOW: Instant = Instant.parse("2026-07-17T00:00:00Z")
private val FIXED_CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
private val PAYMENT_ID = PaymentId("pay_test_001")
private val MERCHANT_ID = MerchantId("mrc_test_001")
private val RECEIVING_WALLET = WalletAddress("0x" + "a".repeat(40))
private val MARKET_QUOTE = MarketRateQuote(providerCode = "fake-exchange", rate = ExchangeRate(BigDecimal("1400")), quotedAt = NOW)

private fun newSucceededPayment(): Payment {
	val payment =
		Payment.create(
			id = PAYMENT_ID,
			merchantId = MERCHANT_ID,
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
	payment.submit(RECEIVING_WALLET, NOW.minusSeconds(40))
	payment.startConfirmation(NOW.minusSeconds(30))
	payment.succeed(NOW.minusSeconds(10))
	return payment
}

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

private fun newUseCase(
	paymentRepository: PaymentRepository,
	exchangeOrderRepository: ExchangeOrderRepository = mockk(relaxed = true),
	settlementReceivableRepository: SettlementReceivableRepository = mockk(relaxed = true),
	outboxEventRepository: OutboxEventRepository = mockk(relaxed = true),
	exchangeRateProvider: ExchangeRateProvider = mockk(),
): SellToFakeExchangeUseCase =
	SellToFakeExchangeUseCase(
		paymentRepository = paymentRepository,
		exchangeOrderRepository = exchangeOrderRepository,
		settlementReceivableRepository = settlementReceivableRepository,
		outboxEventRepository = outboxEventRepository,
		exchangeRateProvider = exchangeRateProvider,
		idGenerator = FakeIdGenerator(),
		transactionManager = ImmediateTransactionManager(),
		clock = FIXED_CLOCK,
	)

class SellToFakeExchangeUseCaseTest :
	FunSpec({

		test("completes an ExchangeOrder and marks the SettlementReceivable ready") {
			val paymentRepository = mockk<PaymentRepository>()
			val exchangeOrderRepository = mockk<ExchangeOrderRepository>(relaxed = true)
			val settlementReceivableRepository = mockk<SettlementReceivableRepository>(relaxed = true)
			val outboxEventRepository = mockk<OutboxEventRepository>(relaxed = true)
			val exchangeRateProvider = mockk<ExchangeRateProvider>()
			every { paymentRepository.findById(PAYMENT_ID) } returns newSucceededPayment()
			every { exchangeOrderRepository.findByPaymentId(PAYMENT_ID) } returns null
			every { exchangeRateProvider.currentRate() } returns MARKET_QUOTE
			val savedExchangeOrders = mutableListOf<ExchangeOrder>()
			val savedSettlements = mutableListOf<SettlementReceivable>()
			every { exchangeOrderRepository.save(capture(savedExchangeOrders)) } returns Unit
			every { settlementReceivableRepository.save(capture(savedSettlements)) } returns Unit

			val result =
				newUseCase(paymentRepository, exchangeOrderRepository, settlementReceivableRepository, outboxEventRepository, exchangeRateProvider)
					.execute(SellToFakeExchangeCommand(PAYMENT_ID))

			result.exchangeOrderStatus shouldBe ExchangeOrderStatus.COMPLETED
			result.settlementReceivableStatus shouldBe SettlementReceivableStatus.READY
			savedExchangeOrders.single().requestedAmount shouldBe TokenAmount(6_666_667)
			savedExchangeOrders.single().receivedAmount shouldBe Money(9_333)
			savedSettlements.single().grossAmount shouldBe Money(10_000)
			savedSettlements.single().exchangeOrderId shouldBe savedExchangeOrders.single().id
			verify(exactly = 1) { outboxEventRepository.save(any()) }
		}

		test("replays the existing result idempotently instead of creating a duplicate") {
			val paymentRepository = mockk<PaymentRepository>()
			val exchangeOrderRepository = mockk<ExchangeOrderRepository>()
			val settlementReceivableRepository = mockk<SettlementReceivableRepository>()
			val exchangeRateProvider = mockk<ExchangeRateProvider>()

			val existingExchangeOrder =
				ExchangeOrder.create(
					id = ExchangeOrderId("exo_existing"),
					paymentId = PAYMENT_ID,
					exchangeProviderCode = "fake-exchange",
					clientOrderId = ClientOrderId("sell_${PAYMENT_ID.value}"),
					orderSide = OrderSide.SELL,
					baseAsset = Asset.USDC,
					requestedAmount = TokenAmount(6_666_667),
					requestedAt = NOW.minusSeconds(5),
				)
			existingExchangeOrder.complete(
				executedAmount = TokenAmount(6_666_667),
				averageExecutionRate = ExchangeRate(BigDecimal("1400")),
				receivedAmount = Money(9_333),
				exchangeFeeAmount = null,
				completedAt = NOW.minusSeconds(4),
			)
			val existingSettlement =
				SettlementReceivable.create(
					id = SettlementReceivableId("stl_existing"),
					paymentId = PAYMENT_ID,
					merchantId = MERCHANT_ID,
					grossAmount = Money(10_000),
					feeRate = BigDecimal("0.015"),
					feeAmount = Money(150),
					adjustmentAmount = SignedMoney.ZERO,
					eligibleDate = LocalDate.of(2026, 7, 17),
					createdAt = NOW.minusSeconds(5),
				)
			existingSettlement.markReady(
				exchangeOrderId = existingExchangeOrder.id,
				exchangeReceivedAmount = Money(9_333),
				exchangeProfitLossAmount = SignedMoney(-667),
				changedAt = NOW.minusSeconds(4),
			)

			every { paymentRepository.findById(PAYMENT_ID) } returns newSucceededPayment()
			every { exchangeOrderRepository.findByPaymentId(PAYMENT_ID) } returns existingExchangeOrder
			every { settlementReceivableRepository.findByPaymentId(PAYMENT_ID) } returns existingSettlement

			val result =
				newUseCase(paymentRepository, exchangeOrderRepository, settlementReceivableRepository, exchangeRateProvider = exchangeRateProvider)
					.execute(SellToFakeExchangeCommand(PAYMENT_ID))

			result.exchangeOrderId shouldBe existingExchangeOrder.id
			result.settlementReceivableId shouldBe existingSettlement.id
			verify(exactly = 0) { exchangeRateProvider.currentRate() }
		}

		test("throws PaymentNotFoundException when the payment does not exist") {
			val paymentRepository = mockk<PaymentRepository>()
			every { paymentRepository.findById(PAYMENT_ID) } returns null

			shouldThrow<PaymentNotFoundException> {
				newUseCase(paymentRepository).execute(SellToFakeExchangeCommand(PAYMENT_ID))
			}
		}

		test("throws when the payment is not SUCCEEDED") {
			val paymentRepository = mockk<PaymentRepository>()
			val payment =
				Payment.create(
					id = PAYMENT_ID,
					merchantId = MERCHANT_ID,
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
			every { paymentRepository.findById(PAYMENT_ID) } returns payment

			shouldThrow<IllegalStateException> {
				newUseCase(paymentRepository).execute(SellToFakeExchangeCommand(PAYMENT_ID))
			}
		}
	})
