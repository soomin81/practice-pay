package paytech.practice.pay.infra.persistence.jooq.webhook

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.shared.EventId
import paytech.practice.pay.domain.shared.HttpUrl
import paytech.practice.pay.domain.webhook.WebhookDelivery
import paytech.practice.pay.domain.webhook.WebhookDeliveryId
import paytech.practice.pay.domain.webhook.WebhookDeliveryStatus
import paytech.practice.pay.infra.persistence.jooq.PersistenceTestSupport
import paytech.practice.pay.infra.persistence.jooq.insertTestMerchant
import paytech.practice.pay.infra.persistence.jooq.uniqueSuffix
import java.time.Instant

private val NOW: Instant = Instant.parse("2026-07-17T00:00:00Z")

private fun newWebhookDelivery(
	merchantId: MerchantId,
	eventId: EventId = EventId("evt_${uniqueSuffix()}"),
): WebhookDelivery =
	WebhookDelivery.create(
		id = WebhookDeliveryId("wh_${uniqueSuffix()}"),
		merchantId = merchantId,
		eventId = eventId,
		eventType = "payment.succeeded",
		aggregateType = "Payment",
		aggregateId = "pay_test_001",
		destinationUrl = HttpUrl("https://merchant.example.com/webhooks"),
		payload = """{"paymentId":"pay_test_001"}""",
		createdAt = NOW,
	)

class WebhookDeliveryRepositoryAdapterTest :
	FunSpec({
		val adapter = WebhookDeliveryRepositoryAdapter(PersistenceTestSupport.dsl)

		test("save inserts a new WebhookDelivery and findByEventIdAndMerchantId round-trips it") {
			val merchantId = MerchantId(insertTestMerchant())
			val delivery = newWebhookDelivery(merchantId)

			adapter.save(delivery)
			val found = adapter.findByEventIdAndMerchantId(delivery.eventId, merchantId)

			found.shouldNotBeNull()
			found.id shouldBe delivery.id
			found.status shouldBe WebhookDeliveryStatus.PENDING
			found.attemptCount shouldBe 0
			found.destinationUrl shouldBe delivery.destinationUrl
		}

		test("save persists a status transition on an existing WebhookDelivery, guarded by version") {
			val merchantId = MerchantId(insertTestMerchant())
			val delivery = newWebhookDelivery(merchantId)
			adapter.save(delivery)

			delivery.startDelivering(NOW.plusSeconds(1))
			delivery.succeed(200, NOW.plusSeconds(2))
			adapter.save(delivery)

			val found = adapter.findByEventIdAndMerchantId(delivery.eventId, merchantId)
			found.shouldNotBeNull()
			found.status shouldBe WebhookDeliveryStatus.SUCCEEDED
			found.attemptCount shouldBe 1
			found.lastHttpStatus shouldBe 200
			found.deliveredAt shouldBe NOW.plusSeconds(2)
		}

		test("findByEventIdAndMerchantId returns null when no delivery exists for the pair") {
			val merchantId = MerchantId(insertTestMerchant())

			adapter.findByEventIdAndMerchantId(EventId("evt_${uniqueSuffix()}"), merchantId) shouldBe null
		}
	})
