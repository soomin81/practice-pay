package paytech.practice.pay.domain.outbox

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import paytech.practice.pay.domain.shared.EventId
import java.time.Instant

private val CREATED_AT: Instant = Instant.parse("2026-07-17T00:00:00Z")

private fun newEvent(): OutboxEvent =
	OutboxEvent.create(
		eventId = EventId("evt_test_001"),
		aggregateType = "Payment",
		aggregateId = "pay_test_001",
		eventType = "payment.created",
		payload = """{"paymentId":"pay_test_001"}""",
		occurredAt = CREATED_AT,
		createdAt = CREATED_AT,
	)

class OutboxEventTest :
	FunSpec({

		test("create starts in PENDING with zero retries") {
			val event = newEvent()

			event.status shouldBe OutboxEventStatus.PENDING
			event.retryCount shouldBe 0
			event.publishedAt.shouldBeNull()
			event.updatedAt shouldBe CREATED_AT
		}

		test("create rejects a blank payload") {
			shouldThrow<IllegalArgumentException> {
				OutboxEvent.create(
					eventId = EventId("evt_test_002"),
					aggregateType = "Payment",
					aggregateId = "pay_test_001",
					eventType = "payment.created",
					payload = "   ",
					occurredAt = CREATED_AT,
					createdAt = CREATED_AT,
				)
			}
		}

		test("startPublishing moves PENDING to PROCESSING and increments retryCount") {
			val event = newEvent()
			val changedAt = CREATED_AT.plusSeconds(1)

			event.startPublishing(changedAt)

			event.status shouldBe OutboxEventStatus.PROCESSING
			event.retryCount shouldBe 1
			event.updatedAt shouldBe changedAt
		}

		test("startPublishing fails when not PENDING or RETRY_WAITING") {
			val event = newEvent()
			event.startPublishing(CREATED_AT.plusSeconds(1))
			event.publish(CREATED_AT.plusSeconds(2))

			shouldThrow<IllegalStateException> { event.startPublishing(CREATED_AT.plusSeconds(3)) }
		}

		test("publish moves PROCESSING to PUBLISHED") {
			val event = newEvent()
			event.startPublishing(CREATED_AT.plusSeconds(1))
			val publishedAt = CREATED_AT.plusSeconds(2)

			event.publish(publishedAt)

			event.status shouldBe OutboxEventStatus.PUBLISHED
			event.publishedAt shouldBe publishedAt
		}

		test("publish fails when not PROCESSING") {
			val event = newEvent()

			shouldThrow<IllegalStateException> { event.publish(CREATED_AT.plusSeconds(1)) }
		}

		test("scheduleRetry moves PROCESSING to RETRY_WAITING and records the next attempt time") {
			val event = newEvent()
			event.startPublishing(CREATED_AT.plusSeconds(1))
			val nextRetryAt = CREATED_AT.plusSeconds(60)

			event.scheduleRetry(nextRetryAt, CREATED_AT.plusSeconds(2))

			event.status shouldBe OutboxEventStatus.RETRY_WAITING
			event.nextRetryAt shouldBe nextRetryAt
		}

		test("a retry cycle: RETRY_WAITING can start publishing again, clearing nextRetryAt") {
			val event = newEvent()
			event.startPublishing(CREATED_AT.plusSeconds(1))
			event.scheduleRetry(CREATED_AT.plusSeconds(60), CREATED_AT.plusSeconds(2))

			event.startPublishing(CREATED_AT.plusSeconds(60))

			event.status shouldBe OutboxEventStatus.PROCESSING
			event.retryCount shouldBe 2
			event.nextRetryAt.shouldBeNull()
		}

		test("fail moves PROCESSING to FAILED") {
			val event = newEvent()
			event.startPublishing(CREATED_AT.plusSeconds(1))
			val failedAt = CREATED_AT.plusSeconds(2)

			event.fail(failedAt)

			event.status shouldBe OutboxEventStatus.FAILED
			event.updatedAt shouldBe failedAt
		}

		test("fail fails when not PROCESSING") {
			val event = newEvent()

			shouldThrow<IllegalStateException> { event.fail(CREATED_AT.plusSeconds(1)) }
		}

		/**
		 * **`WebhookDelivery.redeliver`와 짝으로만 쓰인다** — 전송만 되돌리고 이쪽을 두면
		 * 발행 Worker(`findPendingPublication`)가 대상으로 집지 않아 아무 일도 안 일어난다.
		 * 되돌려 놓으면 평소와 똑같은 경로로 다시 발행된다.
		 */
		test("reopenForRedelivery moves FAILED back to PENDING so the worker picks it up again") {
			val event = newEvent()
			event.startPublishing(CREATED_AT.plusSeconds(1))
			event.fail(CREATED_AT.plusSeconds(2))
			val reopenedAt = CREATED_AT.plusSeconds(600)

			event.reopenForRedelivery(reopenedAt)

			event.status shouldBe OutboxEventStatus.PENDING
			event.updatedAt shouldBe reopenedAt
			event.nextRetryAt.shouldBeNull()
		}

		/** 예외를 **좁게** 둔다 — `FAILED`만 되돌릴 수 있고 `PUBLISHED`는 아니다. */
		test("reopenForRedelivery refuses anything other than FAILED") {
			shouldThrow<IllegalStateException> { newEvent().reopenForRedelivery(CREATED_AT.plusSeconds(1)) }

			val published = newEvent()
			published.startPublishing(CREATED_AT.plusSeconds(1))
			published.publish(CREATED_AT.plusSeconds(2))
			shouldThrow<IllegalStateException> { published.reopenForRedelivery(CREATED_AT.plusSeconds(3)) }
		}

		test("reconstitute rejects PUBLISHED without publishedAt") {
			shouldThrow<IllegalArgumentException> {
				OutboxEvent.reconstitute(
					eventId = EventId("evt_test_003"),
					aggregateType = "Payment",
					aggregateId = "pay_test_001",
					eventType = "payment.created",
					payload = """{"paymentId":"pay_test_001"}""",
					occurredAt = CREATED_AT,
					createdAt = CREATED_AT,
					status = OutboxEventStatus.PUBLISHED,
					retryCount = 1,
					nextRetryAt = null,
					publishedAt = null,
					updatedAt = CREATED_AT,
				)
			}
		}

		test("reconstitute restores a PUBLISHED event faithfully") {
			val publishedAt = CREATED_AT.plusSeconds(10)

			val event =
				OutboxEvent.reconstitute(
					eventId = EventId("evt_test_004"),
					aggregateType = "Payment",
					aggregateId = "pay_test_001",
					eventType = "payment.created",
					payload = """{"paymentId":"pay_test_001"}""",
					occurredAt = CREATED_AT,
					createdAt = CREATED_AT,
					status = OutboxEventStatus.PUBLISHED,
					retryCount = 1,
					nextRetryAt = null,
					publishedAt = publishedAt,
					updatedAt = publishedAt,
				)

			event.status shouldBe OutboxEventStatus.PUBLISHED
			event.publishedAt shouldBe publishedAt
			event.retryCount shouldBe 1
		}
	})
