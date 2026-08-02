package paytech.practice.pay.application.webhook

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import paytech.practice.pay.application.port.outbound.OutboxEventRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.application.port.outbound.WebhookDeliveryRepository
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.outbox.OutboxEvent
import paytech.practice.pay.domain.outbox.OutboxEventStatus
import paytech.practice.pay.domain.shared.EventId
import paytech.practice.pay.domain.shared.HttpUrl
import paytech.practice.pay.domain.webhook.WebhookDelivery
import paytech.practice.pay.domain.webhook.WebhookDeliveryId
import paytech.practice.pay.domain.webhook.WebhookDeliveryStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private val NOW: Instant = Instant.parse("2026-08-02T00:00:00Z")
private val FIXED_CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
private val DELIVERY_ID = WebhookDeliveryId("wh_test_001")
private val EVENT_ID = EventId("evt_test_001")
private val MERCHANT_ID = MerchantId("mrc_test_001")

private class ImmediateTransactionManager : TransactionManager {
	override fun <T> runInTransaction(block: () -> T): T = block()
}

private fun newDelivery(): WebhookDelivery =
	WebhookDelivery.create(
		id = DELIVERY_ID,
		merchantId = MERCHANT_ID,
		eventId = EVENT_ID,
		eventType = "payment.succeeded",
		aggregateType = "Payment",
		aggregateId = "pay_test_001",
		destinationUrl = HttpUrl("https://merchant.example.com/webhooks"),
		payload = """{"paymentId":"pay_test_001"}""",
		createdAt = NOW.minusSeconds(3_600),
	)

/** 자동 재시도를 소진해 `FAILED`로 끝난 전송 — 재전송의 출발점이다. */
private fun failedDelivery(): WebhookDelivery =
	newDelivery().apply {
		startDelivering(NOW.minusSeconds(3_000))
		fail(500, "internal server error", NOW.minusSeconds(2_900))
	}

private fun newEvent(): OutboxEvent =
	OutboxEvent.create(
		eventId = EVENT_ID,
		aggregateType = "Payment",
		aggregateId = "pay_test_001",
		eventType = "payment.succeeded",
		payload = """{"paymentId":"pay_test_001"}""",
		occurredAt = NOW.minusSeconds(3_600),
		createdAt = NOW.minusSeconds(3_600),
	)

private fun failedEvent(): OutboxEvent =
	newEvent().apply {
		startPublishing(NOW.minusSeconds(3_000))
		fail(NOW.minusSeconds(2_900))
	}

private fun newUseCase(
	deliveryRepository: WebhookDeliveryRepository,
	outboxEventRepository: OutboxEventRepository = mockk(relaxed = true),
): RedeliverWebhookUseCase =
	RedeliverWebhookUseCase(
		webhookDeliveryRepository = deliveryRepository,
		outboxEventRepository = outboxEventRepository,
		transactionManager = ImmediateTransactionManager(),
		clock = FIXED_CLOCK,
	)

class RedeliverWebhookUseCaseTest :
	FunSpec({

		/**
		 * **둘을 함께 되돌리는 것이 이 Use Case의 전부다.** 전송만 `PENDING`이 되고
		 * 이벤트가 `FAILED`로 남으면 발행 Worker가 대상으로 집지 않아 영영 대기 상태로
		 * 멈춘다 — 화면은 "예약됨"이라고 말하는데 아무 일도 일어나지 않는다.
		 */
		test("reopens both the delivery and the outbox event") {
			val deliveryRepository = mockk<WebhookDeliveryRepository>(relaxed = true)
			val outboxEventRepository = mockk<OutboxEventRepository>(relaxed = true)
			val delivery = failedDelivery()
			val event = failedEvent()
			every { deliveryRepository.findById(DELIVERY_ID) } returns delivery
			every { outboxEventRepository.findById(EVENT_ID) } returns event

			val result = newUseCase(deliveryRepository, outboxEventRepository).execute(DELIVERY_ID)

			result.status shouldBe WebhookDeliveryStatus.PENDING
			delivery.status shouldBe WebhookDeliveryStatus.PENDING
			event.status shouldBe OutboxEventStatus.PENDING
			verify(exactly = 1) { deliveryRepository.save(delivery) }
			verify(exactly = 1) { outboxEventRepository.save(event) }
		}

		/**
		 * **HTTP를 직접 부르지 않는다** — 되돌려 놓기만 하고 발송은 기존 발행 Worker가
		 * 평소 경로로 한다. 이 Use Case에 전송 Port가 아예 없다는 사실이 그 설계를 강제한다.
		 */
		test("returns the reopened state rather than a delivery outcome") {
			val deliveryRepository = mockk<WebhookDeliveryRepository>(relaxed = true)
			every { deliveryRepository.findById(DELIVERY_ID) } returns failedDelivery()
			val outboxEventRepository = mockk<OutboxEventRepository>(relaxed = true)
			every { outboxEventRepository.findById(EVENT_ID) } returns failedEvent()

			val result = newUseCase(deliveryRepository, outboxEventRepository).execute(DELIVERY_ID)

			// SUCCEEDED가 아니라 PENDING이다 — 아직 보내지 않았다는 뜻이다.
			result.status shouldBe WebhookDeliveryStatus.PENDING
		}

		/**
		 * **누적 시도 횟수를 초기화하지 않는다** — 재전송은 자동 재시도 예산을 새로 주는
		 * 것이 아니라 시도 한 번을 뜻한다(`docs/domain/state-transitions.md`).
		 */
		test("keeps the accumulated attempt count so the retry budget is not reset") {
			val deliveryRepository = mockk<WebhookDeliveryRepository>(relaxed = true)
			val delivery = failedDelivery()
			val attemptsBefore = delivery.attemptCount
			every { deliveryRepository.findById(DELIVERY_ID) } returns delivery
			val outboxEventRepository = mockk<OutboxEventRepository>(relaxed = true)
			every { outboxEventRepository.findById(EVENT_ID) } returns failedEvent()

			val result = newUseCase(deliveryRepository, outboxEventRepository).execute(DELIVERY_ID)

			result.attemptCount shouldBe attemptsBefore
		}

		test("an unknown delivery is reported as not found") {
			val deliveryRepository = mockk<WebhookDeliveryRepository>()
			every { deliveryRepository.findById(DELIVERY_ID) } returns null

			shouldThrow<WebhookDeliveryNotFoundException> { newUseCase(deliveryRepository).execute(DELIVERY_ID) }
		}

		/**
		 * 되돌릴 수 있는 것은 `FAILED`뿐이다. **이미 성공한 전송을 다시 보내는 것은
		 * 재전송이 아니라 중복 발송**이라, 상태를 먼저 확인해 거절한다.
		 */
		test("a delivery that has not failed is refused with its current status") {
			val deliveryRepository = mockk<WebhookDeliveryRepository>(relaxed = true)
			val succeeded =
				newDelivery().apply {
					startDelivering(NOW.minusSeconds(3_000))
					succeed(200, NOW.minusSeconds(2_900))
				}
			every { deliveryRepository.findById(DELIVERY_ID) } returns succeeded

			val thrown =
				shouldThrow<WebhookDeliveryNotRedeliverableException> {
					newUseCase(deliveryRepository).execute(DELIVERY_ID)
				}

			thrown.status shouldBe WebhookDeliveryStatus.SUCCEEDED
			// 거절했으면 아무것도 저장하지 않아야 한다.
			verify(exactly = 0) { deliveryRepository.save(any()) }
		}
	})
