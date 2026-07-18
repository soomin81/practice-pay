package paytech.practice.pay.infra.persistence.jooq.exchange

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import paytech.practice.pay.domain.exchange.ClientOrderId
import paytech.practice.pay.domain.exchange.ExchangeOrder
import paytech.practice.pay.domain.exchange.ExchangeOrderId
import paytech.practice.pay.domain.exchange.ExchangeOrderStatus
import paytech.practice.pay.domain.exchange.OrderSide
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.ExchangeRate
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.TokenAmount
import paytech.practice.pay.infra.persistence.jooq.PersistenceTestSupport
import paytech.practice.pay.infra.persistence.jooq.insertTestMerchant
import paytech.practice.pay.infra.persistence.jooq.insertTestPayment
import paytech.practice.pay.infra.persistence.jooq.uniqueSuffix
import java.math.BigDecimal
import java.time.Instant

private val NOW: Instant = Instant.parse("2026-07-17T00:00:00Z")

private fun newExchangeOrder(paymentId: PaymentId): ExchangeOrder =
	ExchangeOrder.create(
		id = ExchangeOrderId("exo_${uniqueSuffix()}"),
		paymentId = paymentId,
		exchangeProviderCode = "fake-exchange",
		clientOrderId = ClientOrderId("sell_${paymentId.value}"),
		orderSide = OrderSide.SELL,
		baseAsset = Asset.USDC,
		requestedAmount = TokenAmount(6_666_667),
		requestedAt = NOW,
	)

private fun newPaymentId(): PaymentId {
	val merchantId = insertTestMerchant()
	return PaymentId(insertTestPayment(merchantId))
}

class ExchangeOrderRepositoryAdapterTest :
	FunSpec({
		val adapter = ExchangeOrderRepositoryAdapter(PersistenceTestSupport.dsl)

		test("save inserts a new ExchangeOrder and findByPaymentId round-trips it") {
			val paymentId = newPaymentId()
			val exchangeOrder = newExchangeOrder(paymentId)

			adapter.save(exchangeOrder)
			val found = adapter.findByPaymentId(paymentId)

			found.shouldNotBeNull()
			found.id shouldBe exchangeOrder.id
			found.status shouldBe ExchangeOrderStatus.REQUESTED
			found.requestedAmount shouldBe exchangeOrder.requestedAmount
			found.clientOrderId shouldBe exchangeOrder.clientOrderId
		}

		test("save persists a completion transition on an existing ExchangeOrder") {
			val paymentId = newPaymentId()
			val exchangeOrder = newExchangeOrder(paymentId)
			adapter.save(exchangeOrder)

			exchangeOrder.complete(
				executedAmount = TokenAmount(6_666_667),
				averageExecutionRate = ExchangeRate(BigDecimal("1400.000000000000")),
				receivedAmount = Money(9_333_334),
				exchangeFeeAmount = null,
				completedAt = NOW.plusSeconds(1),
			)
			adapter.save(exchangeOrder)

			val found = adapter.findByPaymentId(paymentId)
			found.shouldNotBeNull()
			found.status shouldBe ExchangeOrderStatus.COMPLETED
			found.executedAmount shouldBe TokenAmount(6_666_667)
			found.receivedAmount shouldBe Money(9_333_334)
			found.completedAt shouldBe NOW.plusSeconds(1)
		}

		test("findByPaymentId returns null when no ExchangeOrder exists for the payment") {
			val paymentId = newPaymentId()

			adapter.findByPaymentId(paymentId).shouldBeNull()
		}
	})
