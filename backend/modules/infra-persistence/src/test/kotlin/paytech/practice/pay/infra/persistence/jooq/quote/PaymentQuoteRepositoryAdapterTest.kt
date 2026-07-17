package paytech.practice.pay.infra.persistence.jooq.quote

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import paytech.practice.pay.dbcore.jooq.tables.PaymentQuote.Companion.PAYMENT_QUOTE
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.quote.PaymentQuote
import paytech.practice.pay.domain.quote.PaymentQuoteId
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

class PaymentQuoteRepositoryAdapterTest :
	FunSpec({
		val adapter = PaymentQuoteRepositoryAdapter(PersistenceTestSupport.dsl)

		test("save inserts a PaymentQuote snapshot readable back through raw jOOQ") {
			val merchantId = insertTestMerchant()
			val paymentId = insertTestPayment(merchantId)
			val quote =
				PaymentQuote(
					id = PaymentQuoteId("pq_${uniqueSuffix()}"),
					paymentId = PaymentId(paymentId),
					marketProviderCode = "fake-market",
					baseAsset = Asset.USDC,
					marketRate = ExchangeRate(BigDecimal("1500.000000000000")),
					appliedRate = ExchangeRate(BigDecimal("1492.500000000000")),
					spreadRate = BigDecimal("0.00500000"),
					orderAmount = Money(10_000),
					paymentAmount = TokenAmount(6_700_000),
					quotedAt = NOW,
					expiresAt = NOW.plusSeconds(1_800),
					createdAt = NOW,
				)

			adapter.save(quote)

			val record =
				PersistenceTestSupport.dsl
					.selectFrom(PAYMENT_QUOTE)
					.where(PAYMENT_QUOTE.PAYMENT_QUOTE_ID.eq(quote.id.value))
					.fetchOne()!!
			record.marketProviderCode shouldBe "fake-market"
			record.baseAssetCode shouldBe "USDC"
			record.quoteCurrency shouldBe "KRW"
			record.orderAmount shouldBe 10_000L
			record.paymentAmountMinor shouldBe 6_700_000L
		}
	})
