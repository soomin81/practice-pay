package paytech.practice.pay.batch.job

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import paytech.practice.pay.application.outbox.PublishOutboxEventCommand
import paytech.practice.pay.application.outbox.PublishOutboxEventUseCase
import paytech.practice.pay.application.port.outbound.OutboxEventRepository
import paytech.practice.pay.domain.outbox.OutboxEvent
import paytech.practice.pay.domain.shared.EventId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private val NOW: Instant = Instant.parse("2026-07-17T00:00:00Z")
private val FIXED_CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)

private fun newOutboxEvent(id: String): OutboxEvent =
	OutboxEvent.create(
		eventId = EventId(id),
		aggregateType = "Payment",
		aggregateId = "pay_$id",
		eventType = "payment.succeeded",
		payload = """{"paymentId":"pay_$id"}""",
		occurredAt = NOW.minusSeconds(10),
		createdAt = NOW.minusSeconds(10),
	)

class PublishPendingOutboxEventsTaskletTest :
	FunSpec({

		test("calls the use case once for every pending OutboxEvent") {
			val outboxEventRepository = mockk<OutboxEventRepository>()
			val publishOutboxEventUseCase = mockk<PublishOutboxEventUseCase>(relaxed = true)
			val pending = listOf(newOutboxEvent("evt1"), newOutboxEvent("evt2"), newOutboxEvent("evt3"))
			every { outboxEventRepository.findPendingPublication(NOW) } returns pending

			val result =
				PublishPendingOutboxEventsTasklet(outboxEventRepository, publishOutboxEventUseCase, FIXED_CLOCK)
					.execute(mockk<StepContribution>(), mockk<ChunkContext>())

			result shouldBe RepeatStatus.FINISHED
			pending.forEach { event ->
				verify(exactly = 1) { publishOutboxEventUseCase.execute(PublishOutboxEventCommand(event.eventId)) }
			}
		}

		test("a failure for one event does not stop the rest from being processed") {
			val outboxEventRepository = mockk<OutboxEventRepository>()
			val publishOutboxEventUseCase = mockk<PublishOutboxEventUseCase>()
			val failing = newOutboxEvent("evt-failing")
			val succeeding = newOutboxEvent("evt-succeeding")
			every { outboxEventRepository.findPendingPublication(NOW) } returns listOf(failing, succeeding)
			every { publishOutboxEventUseCase.execute(PublishOutboxEventCommand(failing.eventId)) } throws
				IllegalStateException("boom")
			every { publishOutboxEventUseCase.execute(PublishOutboxEventCommand(succeeding.eventId)) } returns mockk()

			val result =
				PublishPendingOutboxEventsTasklet(outboxEventRepository, publishOutboxEventUseCase, FIXED_CLOCK)
					.execute(mockk<StepContribution>(), mockk<ChunkContext>())

			result shouldBe RepeatStatus.FINISHED
			verify(exactly = 1) { publishOutboxEventUseCase.execute(PublishOutboxEventCommand(succeeding.eventId)) }
		}

		test("an empty pending list is a no-op") {
			val outboxEventRepository = mockk<OutboxEventRepository>()
			val publishOutboxEventUseCase = mockk<PublishOutboxEventUseCase>()
			every { outboxEventRepository.findPendingPublication(NOW) } returns emptyList()

			val result =
				PublishPendingOutboxEventsTasklet(outboxEventRepository, publishOutboxEventUseCase, FIXED_CLOCK)
					.execute(mockk<StepContribution>(), mockk<ChunkContext>())

			result shouldBe RepeatStatus.FINISHED
			verify(exactly = 0) { publishOutboxEventUseCase.execute(any()) }
		}
	})
