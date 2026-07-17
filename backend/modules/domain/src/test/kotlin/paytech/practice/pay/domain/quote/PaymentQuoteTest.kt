package paytech.practice.pay.domain.quote

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.ExchangeRate
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.TokenAmount
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

private val QUOTED_AT: Instant = Instant.parse("2026-07-17T00:00:00Z")
private val EXPIRES_AT: Instant = QUOTED_AT.plus(1, ChronoUnit.MINUTES)

private fun newQuote(): PaymentQuote =
	PaymentQuote(
		id = PaymentQuoteId("pq_test_001"),
		paymentId = PaymentId("pay_test_001"),
		marketProviderCode = "FAKE_MARKET",
		baseAsset = Asset.USDC,
		marketRate = ExchangeRate(BigDecimal("1370.000000000000")),
		appliedRate = ExchangeRate(BigDecimal("1370.500000000000")),
		spreadRate = BigDecimal("0.00050000"),
		orderAmount = Money(100_000),
		paymentAmount = TokenAmount(72_970_000),
		quotedAt = QUOTED_AT,
		expiresAt = EXPIRES_AT,
		createdAt = QUOTED_AT,
	)

class PaymentQuoteTest :
	FunSpec({

		test("holds the given snapshot values") {
			val quote = newQuote()

			quote.marketRate shouldBe ExchangeRate(BigDecimal("1370.000000000000"))
			quote.appliedRate shouldBe ExchangeRate(BigDecimal("1370.500000000000"))
			quote.orderAmount shouldBe Money(100_000)
			quote.paymentAmount shouldBe TokenAmount(72_970_000)
		}

		test("two quotes with identical values are equal (data class structural equality)") {
			newQuote() shouldBe newQuote()
		}

		test("rejects a blank marketProviderCode") {
			shouldThrow<IllegalArgumentException> {
				newQuote().copy(marketProviderCode = "   ")
			}
		}

		test("rejects an expiresAt that is not after quotedAt") {
			shouldThrow<IllegalArgumentException> {
				newQuote().copy(expiresAt = QUOTED_AT)
			}
		}

		test("allows a negative spreadRate since the schema places no sign constraint on it") {
			val quote = newQuote().copy(spreadRate = BigDecimal("-0.0001"))

			quote.spreadRate shouldBe BigDecimal("-0.0001")
		}
	})
