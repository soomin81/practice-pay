package paytech.practice.pay.infra.persistence.jooq.payment

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import paytech.practice.pay.domain.exchange.ClientOrderId
import paytech.practice.pay.domain.exchange.ExchangeOrder
import paytech.practice.pay.domain.exchange.ExchangeOrderId
import paytech.practice.pay.domain.exchange.OrderSide
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.Payment
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.TokenAmount
import paytech.practice.pay.domain.shared.WalletAddress
import paytech.practice.pay.infra.persistence.jooq.PersistenceTestSupport
import paytech.practice.pay.infra.persistence.jooq.exchange.ExchangeOrderRepositoryAdapter
import paytech.practice.pay.infra.persistence.jooq.insertTestMerchant
import paytech.practice.pay.infra.persistence.jooq.uniqueSuffix
import java.time.Instant

private val NOW: Instant = Instant.parse("2026-07-17T00:00:00Z")
private val RECEIVING_WALLET = WalletAddress("0x" + "a".repeat(40))

private fun newPayment(
	merchantId: MerchantId,
	merchantOrderId: MerchantOrderId = MerchantOrderId("order-${uniqueSuffix()}"),
): Payment =
	Payment.create(
		id = PaymentId("pay_${uniqueSuffix()}"),
		merchantId = merchantId,
		merchantOrderId = merchantOrderId,
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

class PaymentRepositoryAdapterTest :
	FunSpec({
		val adapter = PaymentRepositoryAdapter(PersistenceTestSupport.dsl)

		test("save inserts a new Payment and findByMerchantOrderId round-trips it") {
			val merchantId = MerchantId(insertTestMerchant())
			val payment = newPayment(merchantId)

			adapter.save(payment)
			val found = adapter.findByMerchantOrderId(merchantId, payment.merchantOrderId)

			found.shouldNotBeNull()
			found.id shouldBe payment.id
			found.status shouldBe PaymentStatus.CREATED
			found.orderAmount shouldBe payment.orderAmount
			found.paymentAmount shouldBe payment.paymentAmount
			found.receivingWallet shouldBe RECEIVING_WALLET
		}

		test("save persists a status transition on an existing Payment") {
			val merchantId = MerchantId(insertTestMerchant())
			val payment = newPayment(merchantId)
			adapter.save(payment)

			payment.ready(NOW.plusSeconds(1))
			adapter.save(payment)

			val found = adapter.findByMerchantOrderId(merchantId, payment.merchantOrderId)
			found.shouldNotBeNull()
			found.status shouldBe PaymentStatus.READY
		}

		test("findByMerchantOrderId returns null when no such order exists") {
			val merchantId = MerchantId(insertTestMerchant())

			adapter.findByMerchantOrderId(merchantId, MerchantOrderId("no-such-order")).shouldBeNull()
		}

		test("save inserts a new Payment and findById round-trips it") {
			val merchantId = MerchantId(insertTestMerchant())
			val payment = newPayment(merchantId)

			adapter.save(payment)
			val found = adapter.findById(payment.id)

			found.shouldNotBeNull()
			found.id shouldBe payment.id
		}

		test("findById returns null when no such payment exists") {
			adapter.findById(PaymentId("pay_no-such-payment")).shouldBeNull()
		}

		test("findPendingExchangeSettlement returns SUCCEEDED payments without an ExchangeOrder, excluding others") {
			val merchantId = MerchantId(insertTestMerchant())
			val exchangeOrderAdapter = ExchangeOrderRepositoryAdapter(PersistenceTestSupport.dsl)

			val pendingPayment = newPayment(merchantId)
			pendingPayment.ready(NOW.plusSeconds(1))
			pendingPayment.submit(RECEIVING_WALLET, NOW.plusSeconds(2))
			pendingPayment.startConfirmation(NOW.plusSeconds(3))
			pendingPayment.succeed(NOW.plusSeconds(4))
			adapter.save(pendingPayment)

			val alreadySoldPayment = newPayment(merchantId)
			alreadySoldPayment.ready(NOW.plusSeconds(1))
			alreadySoldPayment.submit(RECEIVING_WALLET, NOW.plusSeconds(2))
			alreadySoldPayment.startConfirmation(NOW.plusSeconds(3))
			alreadySoldPayment.succeed(NOW.plusSeconds(4))
			adapter.save(alreadySoldPayment)
			exchangeOrderAdapter.save(
				ExchangeOrder.create(
					id = ExchangeOrderId("exo_${uniqueSuffix()}"),
					paymentId = alreadySoldPayment.id,
					exchangeProviderCode = "fake-exchange",
					clientOrderId = ClientOrderId("sell_${alreadySoldPayment.id.value}"),
					orderSide = OrderSide.SELL,
					baseAsset = Asset.USDC,
					requestedAmount = alreadySoldPayment.paymentAmount,
					requestedAt = NOW.plusSeconds(5),
				),
			)

			val readyPayment = newPayment(merchantId)
			readyPayment.ready(NOW.plusSeconds(1))
			adapter.save(readyPayment)

			val result = adapter.findPendingExchangeSettlement()

			result.map { it.id } shouldContain pendingPayment.id
			result.map { it.id } shouldNotContain alreadySoldPayment.id
			result.map { it.id } shouldNotContain readyPayment.id
		}
	})
