package paytech.practice.pay.infra.persistence.jooq.payment

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import paytech.practice.pay.dbcore.jooq.tables.Payment.Companion.PAYMENT
import paytech.practice.pay.dbcore.jooq.tables.PaymentQuote.Companion.PAYMENT_QUOTE
import paytech.practice.pay.dbcore.jooq.tables.WebhookDelivery.Companion.WEBHOOK_DELIVERY
import paytech.practice.pay.domain.blockchain.BlockchainTransaction
import paytech.practice.pay.domain.blockchain.BlockchainTransactionId
import paytech.practice.pay.domain.blockchain.ChainId
import paytech.practice.pay.domain.blockchain.TransactionHash
import paytech.practice.pay.domain.blockchain.TransactionType
import paytech.practice.pay.domain.checkout.CheckoutSession
import paytech.practice.pay.domain.checkout.CheckoutSessionId
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.HttpUrl
import paytech.practice.pay.infra.persistence.jooq.PersistenceTestSupport
import paytech.practice.pay.infra.persistence.jooq.blockchain.BlockchainTransactionRepositoryAdapter
import paytech.practice.pay.infra.persistence.jooq.checkout.CheckoutSessionRepositoryAdapter
import paytech.practice.pay.infra.persistence.jooq.insertTestMerchant
import paytech.practice.pay.infra.persistence.jooq.insertTestPayment
import paytech.practice.pay.infra.persistence.jooq.uniqueSuffix
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime

private val NOW: Instant = Instant.parse("2026-08-01T04:00:00Z")

/** 견적은 결제 생성 트랜잭션에서 함께 만들어지므로 상세 조회에 항상 있어야 한다. */
private fun insertQuote(paymentId: String) {
	val dsl = PersistenceTestSupport.dsl
	val paymentSeq =
		dsl
			.select(PAYMENT.PAYMENT_SEQ)
			.from(PAYMENT)
			.where(PAYMENT.PAYMENT_ID.eq(paymentId))
			.fetchOne(PAYMENT.PAYMENT_SEQ)!!

	dsl
		.newRecord(PAYMENT_QUOTE)
		.apply {
			paymentQuoteId = "pq_${uniqueSuffix()}"
			this.paymentSeq = paymentSeq
			marketProviderCode = "FAKE"
			baseAssetCode = "USDC"
			quoteCurrency = "KRW"
			marketRate = BigDecimal("1400.000000000000")
			appliedRate = BigDecimal("1393.000000000000")
			spreadRate = BigDecimal("0.005000000000")
			orderAmount = 20_000
			paymentAmountMinor = 14_357_502
			quotedAt = LocalDateTime.now()
			expiresAt = LocalDateTime.now().plusMinutes(30)
			createdAt = LocalDateTime.now()
		}.insert()
}

private fun insertCheckoutSession(paymentId: String) {
	CheckoutSessionRepositoryAdapter(PersistenceTestSupport.dsl).save(
		CheckoutSession.create(
			id = CheckoutSessionId("cs_${uniqueSuffix()}"),
			paymentId = PaymentId(paymentId),
			successUrl = HttpUrl("https://merchant.example.com/done"),
			cancelUrl = null,
			expiresAt = NOW.plusSeconds(1_800),
			createdAt = NOW,
		),
	)
}

private fun insertWebhookDelivery(
	paymentId: String,
	eventType: String,
	status: String,
	createdAt: LocalDateTime,
) {
	val dsl = PersistenceTestSupport.dsl
	val merchantSeq =
		dsl
			.select(PAYMENT.MERCHANT_SEQ)
			.from(PAYMENT)
			.where(PAYMENT.PAYMENT_ID.eq(paymentId))
			.fetchOne(PAYMENT.MERCHANT_SEQ)!!

	dsl
		.newRecord(WEBHOOK_DELIVERY)
		.apply {
			webhookDeliveryId = "whd_${uniqueSuffix()}"
			this.merchantSeq = merchantSeq
			eventId = "evt_${uniqueSuffix()}"
			this.eventType = eventType
			aggregateType = "Payment"
			aggregateId = paymentId
			destinationUrl = "http://localhost:9000/webhook"
			payload = org.jooq.JSON.valueOf("{}")
			deliveryStatus = status
			attemptCount = 1
			lastHttpStatus = 200
			// ck_webhook_delivery_delivered_at이 "SUCCEEDED면 delivered_at이 있어야 한다"를
			// 강제한다 — 상태만 바꿔도 제약에 걸리지 않도록 상태에서 끌어낸다.
			deliveredAt = if (status == "SUCCEEDED") createdAt else null
			this.createdAt = createdAt
			updatedAt = createdAt
			version = 0
		}.insert()
}

/**
 * 상세 조회는 한 쿼리로 여러 테이블을 조인한다 — **행이 복제되지 않는다는 것**과
 * **아직 진행되지 않은 단계가 `null`로 온다는 것**이 핵심이다.
 */
class PaymentDetailProjectionAdapterTest :
	FunSpec({
		val projection = PaymentDetailProjectionAdapter(PersistenceTestSupport.dsl)

		test("returns null for an unknown payment") {
			projection.findByPaymentId(PaymentId("pay_nope_${uniqueSuffix()}")).shouldBeNull()
		}

		/**
		 * 결제 직후에는 견적과 체크아웃만 있고 나머지는 없다 — **그 `null`이 "어디까지
		 * 갔는지"를 말해준다.**
		 */
		test("returns quote and checkout session, leaving later stages null") {
			val merchantId = insertTestMerchant()
			val paymentId = insertTestPayment(merchantId, merchantOrderId = "order-detail-1")
			insertQuote(paymentId)
			insertCheckoutSession(paymentId)

			val view = projection.findByPaymentId(PaymentId(paymentId)).shouldNotBeNull()

			view.payment.merchantOrderId.value shouldBe "order-detail-1"
			view.payment.merchantName shouldBe "테스트 가맹점"
			view.quote.appliedRate.compareTo(BigDecimal("1393")) shouldBe 0
			view.checkoutSession.connectedWallet.shouldBeNull()
			view.blockchainTransaction.shouldBeNull()
			view.exchangeOrder.shouldBeNull()
			view.settlementReceivable.shouldBeNull()
			view.webhookDeliveries.size shouldBe 0
		}

		test("includes the on-chain transaction once the hash is submitted") {
			val merchantId = insertTestMerchant()
			val paymentId = insertTestPayment(merchantId)
			insertQuote(paymentId)
			insertCheckoutSession(paymentId)
			val hash = TransactionHash("0x" + "e".repeat(64))
			BlockchainTransactionRepositoryAdapter(PersistenceTestSupport.dsl).save(
				BlockchainTransaction.create(
					id = BlockchainTransactionId("btx_${uniqueSuffix()}"),
					paymentId = PaymentId(paymentId),
					transactionType = TransactionType.PAYMENT,
					network = BlockchainNetwork.BASE_SEPOLIA,
					chainId = ChainId(84_532),
					transactionHash = hash,
					fromAddress = null,
					toAddress = null,
					tokenContractAddress = null,
					tokenAsset = Asset.USDC,
					amountMinor = null,
					requiredConfirmationCount = 12,
					submittedAt = NOW,
				),
			)

			val view = projection.findByPaymentId(PaymentId(paymentId)).shouldNotBeNull()

			view.blockchainTransaction
				.shouldNotBeNull()
				.transactionHash.value shouldBe hash.value
			view.blockchainTransaction.shouldNotBeNull().requiredConfirmationCount shouldBe 12
		}

		/**
		 * **Webhook만 1:N이라 따로 읽는다.** 같은 쿼리에 넣었다면 결제 행이 전송 수만큼
		 * 복제되면서 조인한 다른 값들도 함께 늘어난다 — 이 테스트가 그것을 막는다.
		 */
		test("lists webhook deliveries oldest first without duplicating the payment") {
			val merchantId = insertTestMerchant()
			val paymentId = insertTestPayment(merchantId)
			insertQuote(paymentId)
			insertCheckoutSession(paymentId)
			val base = LocalDateTime.parse("2026-08-01T04:00:00")
			insertWebhookDelivery(paymentId, "payment.created", "SUCCEEDED", base)
			insertWebhookDelivery(paymentId, "payment.succeeded", "FAILED", base.plusMinutes(5))

			val view = projection.findByPaymentId(PaymentId(paymentId)).shouldNotBeNull()

			view.webhookDeliveries.size shouldBe 2
			view.webhookDeliveries.first().eventType shouldBe "payment.created"
			view.webhookDeliveries.last().eventType shouldBe "payment.succeeded"
			// 결제 본문은 한 번만 나온다(복제되지 않았다).
			view.payment.paymentId.value shouldBe paymentId
		}
	})
