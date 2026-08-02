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

/** 자동 재시도를 소진해 `FAILED`로 끝난 전송 — 재전송 테스트의 출발점이다. */
private fun failedDelivery(): WebhookDelivery {
	val delivery = newDelivery()
	delivery.startDelivering(CREATED_AT.plusSeconds(1))
	delivery.fail(500, "internal server error", CREATED_AT.plusSeconds(2))
	return delivery
}

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

		/**
		 * **이 시스템에서 유일하게 종료 상태를 되돌리는 전이다**(`docs/domain/state-transitions.md`의
		 * "수동 재전송"). 되돌린 뒤 발행 Worker가 평소 경로로 다시 집어 간다.
		 */
		test("redeliver moves FAILED back to PENDING") {
			val delivery = failedDelivery()
			val redeliveredAt = CREATED_AT.plusSeconds(600)

			delivery.redeliver(redeliveredAt)

			delivery.status shouldBe WebhookDeliveryStatus.PENDING
			delivery.updatedAt shouldBe redeliveredAt
			// 자동 재시도 대기가 아니라 즉시 발행 대상이다.
			delivery.nextRetryAt.shouldBeNull()
		}

		/**
		 * **`attemptCount`를 초기화하지 않는 것이 중요하다.** 그 값은 "이 이벤트를 몇 번
		 * 시도했나"라는 누적 사실이라, 0으로 되돌리면 이력이 지워지고 재전송이 자동 재시도
		 * 예산을 새로 주는 것처럼 동작한다.
		 */
		test("redeliver keeps the accumulated attempt count") {
			val delivery = failedDelivery()
			val attemptsBefore = delivery.attemptCount

			delivery.redeliver(CREATED_AT.plusSeconds(600))

			delivery.attemptCount shouldBe attemptsBefore
		}

		/**
		 * 예외를 **좁게** 둔 것이 핵심이다 — 되돌릴 수 있는 것은 `FAILED`뿐이고,
		 * `SUCCEEDED`를 되돌리는 것은 재전송이 아니라 중복 발송이다.
		 */
		test("redeliver refuses anything other than FAILED") {
			shouldThrow<IllegalStateException> { newDelivery().redeliver(CREATED_AT.plusSeconds(1)) }

			val succeeded = newDelivery()
			succeeded.startDelivering(CREATED_AT.plusSeconds(1))
			succeeded.succeed(200, CREATED_AT.plusSeconds(2))
			shouldThrow<IllegalStateException> { succeeded.redeliver(CREATED_AT.plusSeconds(3)) }
		}

		/** 되돌린 뒤에는 평소의 발행 흐름이 그대로 이어져야 한다 — 특별한 경로가 없다는 뜻이다. */
		test("a redelivered webhook can go through the ordinary delivering flow again") {
			val delivery = failedDelivery()
			delivery.redeliver(CREATED_AT.plusSeconds(600))

			delivery.startDelivering(CREATED_AT.plusSeconds(601))
			delivery.succeed(200, CREATED_AT.plusSeconds(602))

			delivery.status shouldBe WebhookDeliveryStatus.SUCCEEDED
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
