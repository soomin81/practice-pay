package paytech.practice.pay.domain.webhook

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.shared.EventId
import paytech.practice.pay.domain.shared.HttpUrl
import java.time.Instant

private val CREATED_AT: Instant = Instant.parse("2026-07-17T00:00:00Z")

private fun newDelivery(): WebhookDelivery =
	WebhookDelivery.create(
		id = WebhookDeliveryId("wh_test_001"),
		merchantId = MerchantId("mrc_test_001"),
		eventId = EventId("evt_test_001"),
		eventType = "payment.succeeded",
		aggregateType = "Payment",
		aggregateId = "pay_test_001",
		destinationUrl = HttpUrl("https://merchant.example.com/webhooks/stablecoin"),
		payload = """{"paymentId":"pay_test_001"}""",
		createdAt = CREATED_AT,
	)

class WebhookDeliveryTest :
	FunSpec({

		test("create starts in PENDING with zero attempts") {
			val delivery = newDelivery()

			delivery.status shouldBe WebhookDeliveryStatus.PENDING
			delivery.attemptCount shouldBe 0
			delivery.deliveredAt.shouldBeNull()
			delivery.updatedAt shouldBe CREATED_AT
		}

		test("create rejects a blank payload") {
			shouldThrow<IllegalArgumentException> {
				WebhookDelivery.create(
					id = WebhookDeliveryId("wh_test_002"),
					merchantId = MerchantId("mrc_test_001"),
					eventId = EventId("evt_test_002"),
					eventType = "payment.succeeded",
					aggregateType = "Payment",
					aggregateId = "pay_test_001",
					destinationUrl = HttpUrl("https://merchant.example.com/webhooks/stablecoin"),
					payload = "   ",
					createdAt = CREATED_AT,
				)
			}
		}

		test("startDelivering moves PENDING to DELIVERING and increments attemptCount") {
			val delivery = newDelivery()
			val changedAt = CREATED_AT.plusSeconds(1)

			delivery.startDelivering(changedAt)

			delivery.status shouldBe WebhookDeliveryStatus.DELIVERING
			delivery.attemptCount shouldBe 1
			delivery.updatedAt shouldBe changedAt
		}

		test("startDelivering fails when not PENDING or RETRY_WAITING") {
			val delivery = newDelivery()
			delivery.startDelivering(CREATED_AT.plusSeconds(1))
			delivery.succeed(200, CREATED_AT.plusSeconds(2))

			shouldThrow<IllegalStateException> { delivery.startDelivering(CREATED_AT.plusSeconds(3)) }
		}

		test("succeed moves DELIVERING to SUCCEEDED") {
			val delivery = newDelivery()
			delivery.startDelivering(CREATED_AT.plusSeconds(1))
			val deliveredAt = CREATED_AT.plusSeconds(2)

			delivery.succeed(200, deliveredAt)

			delivery.status shouldBe WebhookDeliveryStatus.SUCCEEDED
			delivery.lastHttpStatus shouldBe 200
			delivery.deliveredAt shouldBe deliveredAt
		}

		test("succeed fails when not DELIVERING") {
			val delivery = newDelivery()

			shouldThrow<IllegalStateException> { delivery.succeed(200, CREATED_AT.plusSeconds(1)) }
		}

		test("scheduleRetry moves DELIVERING to RETRY_WAITING and records the next attempt time") {
			val delivery = newDelivery()
			delivery.startDelivering(CREATED_AT.plusSeconds(1))
			val nextRetryAt = CREATED_AT.plusSeconds(60)

			delivery.scheduleRetry(503, "service unavailable", nextRetryAt, CREATED_AT.plusSeconds(2))

			delivery.status shouldBe WebhookDeliveryStatus.RETRY_WAITING
			delivery.lastHttpStatus shouldBe 503
			delivery.lastErrorMessage shouldBe "service unavailable"
			delivery.nextRetryAt shouldBe nextRetryAt
		}

		test("a retry cycle: RETRY_WAITING can start delivering again, clearing nextRetryAt") {
			val delivery = newDelivery()
			delivery.startDelivering(CREATED_AT.plusSeconds(1))
			delivery.scheduleRetry(503, "service unavailable", CREATED_AT.plusSeconds(60), CREATED_AT.plusSeconds(2))

			delivery.startDelivering(CREATED_AT.plusSeconds(60))

			delivery.status shouldBe WebhookDeliveryStatus.DELIVERING
			delivery.attemptCount shouldBe 2
			delivery.nextRetryAt.shouldBeNull()
		}

		test("fail moves DELIVERING to FAILED") {
			val delivery = newDelivery()
			delivery.startDelivering(CREATED_AT.plusSeconds(1))
			val failedAt = CREATED_AT.plusSeconds(2)

			delivery.fail(500, "internal server error", failedAt)

			delivery.status shouldBe WebhookDeliveryStatus.FAILED
			delivery.lastHttpStatus shouldBe 500
			delivery.updatedAt shouldBe failedAt
		}

		test("fail fails when not DELIVERING") {
			val delivery = newDelivery()

			shouldThrow<IllegalStateException> { delivery.fail(null, null, CREATED_AT.plusSeconds(1)) }
		}

		test("reconstitute rejects an out-of-range lastHttpStatus") {
			shouldThrow<IllegalArgumentException> {
				WebhookDelivery.reconstitute(
					id = WebhookDeliveryId("wh_test_003"),
					merchantId = MerchantId("mrc_test_001"),
					eventId = EventId("evt_test_003"),
					eventType = "payment.succeeded",
					aggregateType = "Payment",
					aggregateId = "pay_test_001",
					destinationUrl = HttpUrl("https://merchant.example.com/webhooks/stablecoin"),
					payload = """{"paymentId":"pay_test_001"}""",
					createdAt = CREATED_AT,
					status = WebhookDeliveryStatus.FAILED,
					attemptCount = 1,
					lastHttpStatus = 700,
					lastErrorMessage = null,
					nextRetryAt = null,
					deliveredAt = null,
					updatedAt = CREATED_AT,
				)
			}
		}

		test("reconstitute rejects SUCCEEDED without deliveredAt") {
			shouldThrow<IllegalArgumentException> {
				WebhookDelivery.reconstitute(
					id = WebhookDeliveryId("wh_test_004"),
					merchantId = MerchantId("mrc_test_001"),
					eventId = EventId("evt_test_004"),
					eventType = "payment.succeeded",
					aggregateType = "Payment",
					aggregateId = "pay_test_001",
					destinationUrl = HttpUrl("https://merchant.example.com/webhooks/stablecoin"),
					payload = """{"paymentId":"pay_test_001"}""",
					createdAt = CREATED_AT,
					status = WebhookDeliveryStatus.SUCCEEDED,
					attemptCount = 1,
					lastHttpStatus = 200,
					lastErrorMessage = null,
					nextRetryAt = null,
					deliveredAt = null,
					updatedAt = CREATED_AT,
				)
			}
		}

		test("reconstitute restores a SUCCEEDED delivery faithfully") {
			val deliveredAt = CREATED_AT.plusSeconds(10)

			val delivery =
				WebhookDelivery.reconstitute(
					id = WebhookDeliveryId("wh_test_005"),
					merchantId = MerchantId("mrc_test_001"),
					eventId = EventId("evt_test_005"),
					eventType = "payment.succeeded",
					aggregateType = "Payment",
					aggregateId = "pay_test_001",
					destinationUrl = HttpUrl("https://merchant.example.com/webhooks/stablecoin"),
					payload = """{"paymentId":"pay_test_001"}""",
					createdAt = CREATED_AT,
					status = WebhookDeliveryStatus.SUCCEEDED,
					attemptCount = 1,
					lastHttpStatus = 200,
					lastErrorMessage = null,
					nextRetryAt = null,
					deliveredAt = deliveredAt,
					updatedAt = deliveredAt,
				)

			delivery.status shouldBe WebhookDeliveryStatus.SUCCEEDED
			delivery.deliveredAt shouldBe deliveredAt
			delivery.attemptCount shouldBe 1
		}
	})
