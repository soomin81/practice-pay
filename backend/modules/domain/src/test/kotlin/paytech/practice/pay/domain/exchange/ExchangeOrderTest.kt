package paytech.practice.pay.domain.exchange

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.ExchangeRate
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.TokenAmount
import java.math.BigDecimal
import java.time.Instant

private val REQUESTED_AT: Instant = Instant.parse("2026-07-17T00:00:00Z")

private fun newOrder(): ExchangeOrder =
	ExchangeOrder.create(
		id = ExchangeOrderId("exo_test_001"),
		paymentId = PaymentId("pay_test_001"),
		exchangeProviderCode = "FAKE_EXCHANGE",
		clientOrderId = ClientOrderId("client-order-001"),
		orderSide = OrderSide.SELL,
		baseAsset = Asset.USDC,
		requestedAmount = TokenAmount(72_992_701),
		requestedAt = REQUESTED_AT,
	)

class ExchangeOrderTest :
	FunSpec({

		test("create starts in REQUESTED with no execution result") {
			val order = newOrder()

			order.status shouldBe ExchangeOrderStatus.REQUESTED
			order.providerOrderId.shouldBeNull()
			order.executedAmount.shouldBeNull()
			order.completedAt.shouldBeNull()
			order.updatedAt shouldBe REQUESTED_AT
		}

		test("create rejects a blank exchangeProviderCode") {
			shouldThrow<IllegalArgumentException> {
				ExchangeOrder.create(
					id = ExchangeOrderId("exo_test_002"),
					paymentId = PaymentId("pay_test_001"),
					exchangeProviderCode = "   ",
					clientOrderId = ClientOrderId("client-order-002"),
					orderSide = OrderSide.SELL,
					baseAsset = Asset.USDC,
					requestedAmount = TokenAmount(1_000_000),
					requestedAt = REQUESTED_AT,
				)
			}
		}

		test("create rejects a zero requestedAmount") {
			shouldThrow<IllegalArgumentException> {
				ExchangeOrder.create(
					id = ExchangeOrderId("exo_test_003"),
					paymentId = PaymentId("pay_test_001"),
					exchangeProviderCode = "FAKE_EXCHANGE",
					clientOrderId = ClientOrderId("client-order-003"),
					orderSide = OrderSide.SELL,
					baseAsset = Asset.USDC,
					requestedAmount = TokenAmount.ZERO,
					requestedAt = REQUESTED_AT,
				)
			}
		}

		test("Fake Exchange MVP flow: complete moves REQUESTED directly to COMPLETED") {
			val order = newOrder()
			val completedAt = REQUESTED_AT.plusSeconds(1)

			order.complete(
				executedAmount = TokenAmount(72_992_701),
				averageExecutionRate = ExchangeRate(BigDecimal("1370.000000000000")),
				receivedAmount = Money(100_000),
				exchangeFeeAmount = null,
				completedAt = completedAt,
			)

			order.status shouldBe ExchangeOrderStatus.COMPLETED
			order.executedAmount shouldBe TokenAmount(72_992_701)
			order.receivedAmount shouldBe Money(100_000)
			order.completedAt shouldBe completedAt
		}

		test("operational flow: submit, startProcessing then complete") {
			val order = newOrder()
			order.submit("provider-order-001", REQUESTED_AT.plusSeconds(1))
			order.status shouldBe ExchangeOrderStatus.SUBMITTED
			order.providerOrderId shouldBe "provider-order-001"

			order.startProcessing(REQUESTED_AT.plusSeconds(2))
			order.status shouldBe ExchangeOrderStatus.PROCESSING

			val completedAt = REQUESTED_AT.plusSeconds(3)
			order.complete(
				executedAmount = TokenAmount(72_992_701),
				averageExecutionRate = ExchangeRate(BigDecimal("1370.000000000000")),
				receivedAmount = Money(100_000),
				exchangeFeeAmount = Money(500),
				completedAt = completedAt,
			)

			order.status shouldBe ExchangeOrderStatus.COMPLETED
			order.exchangeFeeAmount shouldBe Money(500)
		}

		test("submit fails when not REQUESTED") {
			val order = newOrder()
			order.submit(null, REQUESTED_AT.plusSeconds(1))

			shouldThrow<IllegalStateException> { order.submit(null, REQUESTED_AT.plusSeconds(2)) }
		}

		test("startProcessing fails when not SUBMITTED") {
			val order = newOrder()

			shouldThrow<IllegalStateException> { order.startProcessing(REQUESTED_AT.plusSeconds(1)) }
		}

		test("complete fails once already COMPLETED") {
			val order = newOrder()
			order.complete(
				executedAmount = TokenAmount(72_992_701),
				averageExecutionRate = ExchangeRate(BigDecimal("1370.000000000000")),
				receivedAmount = Money(100_000),
				exchangeFeeAmount = null,
				completedAt = REQUESTED_AT.plusSeconds(1),
			)

			shouldThrow<IllegalStateException> {
				order.complete(
					executedAmount = TokenAmount(72_992_701),
					averageExecutionRate = ExchangeRate(BigDecimal("1370.000000000000")),
					receivedAmount = Money(100_000),
					exchangeFeeAmount = null,
					completedAt = REQUESTED_AT.plusSeconds(2),
				)
			}
		}

		test("fail moves REQUESTED, SUBMITTED or PROCESSING to FAILED") {
			val fromRequested = newOrder()
			fromRequested.fail("PROVIDER_ERROR", "connection timeout", REQUESTED_AT.plusSeconds(1))
			fromRequested.status shouldBe ExchangeOrderStatus.FAILED
			fromRequested.failureCode shouldBe "PROVIDER_ERROR"

			val fromProcessing = newOrder()
			fromProcessing.submit(null, REQUESTED_AT.plusSeconds(1))
			fromProcessing.startProcessing(REQUESTED_AT.plusSeconds(2))
			fromProcessing.fail(null, null, REQUESTED_AT.plusSeconds(3))
			fromProcessing.status shouldBe ExchangeOrderStatus.FAILED
		}

		test("fail fails once COMPLETED") {
			val order = newOrder()
			order.complete(
				executedAmount = TokenAmount(72_992_701),
				averageExecutionRate = ExchangeRate(BigDecimal("1370.000000000000")),
				receivedAmount = Money(100_000),
				exchangeFeeAmount = null,
				completedAt = REQUESTED_AT.plusSeconds(1),
			)

			shouldThrow<IllegalStateException> { order.fail(null, null, REQUESTED_AT.plusSeconds(2)) }
		}

		test("cancel moves REQUESTED, SUBMITTED or PROCESSING to CANCELLED") {
			val fromRequested = newOrder()
			fromRequested.cancel(REQUESTED_AT.plusSeconds(1))
			fromRequested.status shouldBe ExchangeOrderStatus.CANCELLED

			val fromSubmitted = newOrder()
			fromSubmitted.submit(null, REQUESTED_AT.plusSeconds(1))
			fromSubmitted.cancel(REQUESTED_AT.plusSeconds(2))
			fromSubmitted.status shouldBe ExchangeOrderStatus.CANCELLED
		}

		test("cancel fails once COMPLETED") {
			val order = newOrder()
			order.complete(
				executedAmount = TokenAmount(72_992_701),
				averageExecutionRate = ExchangeRate(BigDecimal("1370.000000000000")),
				receivedAmount = Money(100_000),
				exchangeFeeAmount = null,
				completedAt = REQUESTED_AT.plusSeconds(1),
			)

			shouldThrow<IllegalStateException> { order.cancel(REQUESTED_AT.plusSeconds(2)) }
		}

		test("reconstitute rejects COMPLETED without a full execution result") {
			shouldThrow<IllegalArgumentException> {
				ExchangeOrder.reconstitute(
					id = ExchangeOrderId("exo_test_004"),
					paymentId = PaymentId("pay_test_001"),
					exchangeProviderCode = "FAKE_EXCHANGE",
					clientOrderId = ClientOrderId("client-order-004"),
					orderSide = OrderSide.SELL,
					baseAsset = Asset.USDC,
					requestedAmount = TokenAmount(1_000_000),
					requestedAt = REQUESTED_AT,
					providerOrderId = null,
					status = ExchangeOrderStatus.COMPLETED,
					executedAmount = null,
					averageExecutionRate = null,
					receivedAmount = null,
					exchangeFeeAmount = null,
					failureCode = null,
					failureMessage = null,
					submittedAt = null,
					completedAt = null,
					updatedAt = REQUESTED_AT,
				)
			}
		}

		test("reconstitute restores a COMPLETED order faithfully") {
			val completedAt = REQUESTED_AT.plusSeconds(10)

			val order =
				ExchangeOrder.reconstitute(
					id = ExchangeOrderId("exo_test_005"),
					paymentId = PaymentId("pay_test_001"),
					exchangeProviderCode = "FAKE_EXCHANGE",
					clientOrderId = ClientOrderId("client-order-005"),
					orderSide = OrderSide.SELL,
					baseAsset = Asset.USDC,
					requestedAmount = TokenAmount(72_992_701),
					requestedAt = REQUESTED_AT,
					providerOrderId = "provider-order-005",
					status = ExchangeOrderStatus.COMPLETED,
					executedAmount = TokenAmount(72_992_701),
					averageExecutionRate = ExchangeRate(BigDecimal("1370.000000000000")),
					receivedAmount = Money(100_000),
					exchangeFeeAmount = Money(500),
					failureCode = null,
					failureMessage = null,
					submittedAt = REQUESTED_AT.plusSeconds(1),
					completedAt = completedAt,
					updatedAt = completedAt,
				)

			order.status shouldBe ExchangeOrderStatus.COMPLETED
			order.completedAt shouldBe completedAt
			order.receivedAmount shouldBe Money(100_000)
		}
	})
