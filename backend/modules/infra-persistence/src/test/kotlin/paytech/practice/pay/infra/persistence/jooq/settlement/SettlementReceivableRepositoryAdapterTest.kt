package paytech.practice.pay.infra.persistence.jooq.settlement

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import paytech.practice.pay.domain.exchange.ClientOrderId
import paytech.practice.pay.domain.exchange.ExchangeOrder
import paytech.practice.pay.domain.exchange.ExchangeOrderId
import paytech.practice.pay.domain.exchange.OrderSide
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.settlement.SettlementReceivable
import paytech.practice.pay.domain.settlement.SettlementReceivableId
import paytech.practice.pay.domain.settlement.SettlementReceivableStatus
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.ExchangeRate
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.SignedMoney
import paytech.practice.pay.domain.shared.TokenAmount
import paytech.practice.pay.infra.persistence.jooq.PersistenceTestSupport
import paytech.practice.pay.infra.persistence.jooq.exchange.ExchangeOrderRepositoryAdapter
import paytech.practice.pay.infra.persistence.jooq.insertTestMerchant
import paytech.practice.pay.infra.persistence.jooq.insertTestPayment
import paytech.practice.pay.infra.persistence.jooq.uniqueSuffix
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

private val NOW: Instant = Instant.parse("2026-07-17T00:00:00Z")

private fun newSettlementReceivable(
	paymentId: PaymentId,
	merchantId: MerchantId,
): SettlementReceivable =
	SettlementReceivable.create(
		id = SettlementReceivableId("stl_${uniqueSuffix()}"),
		paymentId = paymentId,
		merchantId = merchantId,
		grossAmount = Money(10_000),
		feeRate = BigDecimal("0.015"),
		feeAmount = Money(150),
		adjustmentAmount = SignedMoney.ZERO,
		eligibleDate = LocalDate.of(2026, 7, 17),
		createdAt = NOW,
	)

private fun newCompletedExchangeOrder(paymentId: PaymentId): ExchangeOrder {
	val exchangeOrder =
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
	exchangeOrder.complete(
		executedAmount = TokenAmount(6_666_667),
		averageExecutionRate = ExchangeRate(BigDecimal("1400.000000000000")),
		receivedAmount = Money(9_333_334),
		exchangeFeeAmount = null,
		completedAt = NOW,
	)
	return exchangeOrder
}

class SettlementReceivableRepositoryAdapterTest :
	FunSpec({
		val adapter = SettlementReceivableRepositoryAdapter(PersistenceTestSupport.dsl)
		val exchangeOrderAdapter = ExchangeOrderRepositoryAdapter(PersistenceTestSupport.dsl)

		test("save inserts a new SettlementReceivable and findByPaymentId round-trips it") {
			val merchantId = MerchantId(insertTestMerchant())
			val paymentId = PaymentId(insertTestPayment(merchantId.value))
			val settlementReceivable = newSettlementReceivable(paymentId, merchantId)

			adapter.save(settlementReceivable)
			val found = adapter.findByPaymentId(paymentId)

			found.shouldNotBeNull()
			found.id shouldBe settlementReceivable.id
			found.status shouldBe SettlementReceivableStatus.PENDING
			found.grossAmount shouldBe settlementReceivable.grossAmount
			found.netAmount shouldBe settlementReceivable.netAmount
			found.exchangeOrderId.shouldBeNull()
		}

		test("save persists a markReady transition, including the ExchangeOrder FK") {
			val merchantId = MerchantId(insertTestMerchant())
			val paymentId = PaymentId(insertTestPayment(merchantId.value))
			val exchangeOrder = newCompletedExchangeOrder(paymentId)
			exchangeOrderAdapter.save(exchangeOrder)
			val settlementReceivable = newSettlementReceivable(paymentId, merchantId)
			adapter.save(settlementReceivable)

			settlementReceivable.markReady(
				exchangeOrderId = exchangeOrder.id,
				exchangeReceivedAmount = Money(9_333_334),
				exchangeProfitLossAmount = SignedMoney(-666_666),
				changedAt = NOW.plusSeconds(1),
			)
			adapter.save(settlementReceivable)

			val found = adapter.findByPaymentId(paymentId)
			found.shouldNotBeNull()
			found.status shouldBe SettlementReceivableStatus.READY
			found.exchangeOrderId shouldBe exchangeOrder.id
			found.exchangeReceivedAmount shouldBe Money(9_333_334)
			found.exchangeProfitLossAmount shouldBe SignedMoney(-666_666)
		}

		test("findByPaymentId returns null when no SettlementReceivable exists for the payment") {
			val merchantId = MerchantId(insertTestMerchant())
			val paymentId = PaymentId(insertTestPayment(merchantId.value))

			adapter.findByPaymentId(paymentId).shouldBeNull()
		}
	})
