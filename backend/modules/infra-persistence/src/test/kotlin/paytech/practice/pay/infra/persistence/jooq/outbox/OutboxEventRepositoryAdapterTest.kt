package paytech.practice.pay.infra.persistence.jooq.outbox

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import paytech.practice.pay.dbcore.jooq.tables.OutboxEvent.Companion.OUTBOX_EVENT
import paytech.practice.pay.domain.outbox.OutboxEvent
import paytech.practice.pay.domain.outbox.OutboxEventStatus
import paytech.practice.pay.domain.shared.EventId
import paytech.practice.pay.infra.persistence.jooq.PersistenceTestSupport
import paytech.practice.pay.infra.persistence.jooq.uniqueSuffix
import java.time.Instant

private val NOW: Instant = Instant.parse("2026-07-17T00:00:00Z")

class OutboxEventRepositoryAdapterTest :
	FunSpec({
		val adapter = OutboxEventRepositoryAdapter(PersistenceTestSupport.dsl)

		test("save inserts an OutboxEvent with its JSON payload readable back through raw jOOQ") {
			val eventId = "evt_${uniqueSuffix()}"
			val event =
				OutboxEvent.create(
					eventId = EventId(eventId),
					aggregateType = "Payment",
					aggregateId = "pay_test_001",
					eventType = "payment.created",
					payload = """{"paymentId":"pay_test_001"}""",
					occurredAt = NOW,
					createdAt = NOW,
				)

			adapter.save(event)

			val record =
				PersistenceTestSupport.dsl
					.selectFrom(OUTBOX_EVENT)
					.where(OUTBOX_EVENT.EVENT_ID.eq(eventId))
					.fetchOne()!!
			record.aggregateType shouldBe "Payment"
			record.eventType shouldBe "payment.created"
			record.eventStatus shouldBe "PENDING"
			record.retryCount shouldBe 0
			// MySQL의 JSON 컬럼은 쓰기 시점에 값을 다시 직렬화해서(예: `:` 뒤에 공백을 넣는다)
			// 우리가 보낸 리터럴을 그대로 보존하지 않는다 — 그래서 바이트 단위 형식이 아니라
			// 내용이 왕복했는지만 확인한다.
			record.payload!!.data() shouldContain "pay_test_001"
		}

		test("save persists a status transition on an existing OutboxEvent") {
			val event =
				OutboxEvent.create(
					eventId = EventId("evt_${uniqueSuffix()}"),
					aggregateType = "Payment",
					aggregateId = "pay_test_001",
					eventType = "payment.created",
					payload = """{"paymentId":"pay_test_001"}""",
					occurredAt = NOW,
					createdAt = NOW,
				)
			adapter.save(event)

			event.startPublishing(NOW.plusSeconds(1))
			event.publish(NOW.plusSeconds(2))
			adapter.save(event)

			val found = adapter.findById(event.eventId)
			found.shouldNotBeNull()
			found.status shouldBe OutboxEventStatus.PUBLISHED
			found.retryCount shouldBe 1
			found.publishedAt shouldBe NOW.plusSeconds(2)
		}

		test("findById returns null when no OutboxEvent exists for the id") {
			adapter.findById(EventId("evt_${uniqueSuffix()}")) shouldBe null
		}

		test("findPendingPublication returns PENDING and due RETRY_WAITING events, excluding others") {
			val pending =
				OutboxEvent.create(
					eventId = EventId("evt_${uniqueSuffix()}"),
					aggregateType = "Payment",
					aggregateId = "pay_test_001",
					eventType = "payment.created",
					payload = """{"paymentId":"pay_test_001"}""",
					occurredAt = NOW,
					createdAt = NOW,
				)
			val dueRetry =
				OutboxEvent.create(
					eventId = EventId("evt_${uniqueSuffix()}"),
					aggregateType = "Payment",
					aggregateId = "pay_test_002",
					eventType = "payment.created",
					payload = """{"paymentId":"pay_test_002"}""",
					occurredAt = NOW,
					createdAt = NOW,
				)
			dueRetry.startPublishing(NOW.plusSeconds(1))
			dueRetry.scheduleRetry(NOW.plusSeconds(2), NOW.plusSeconds(2))
			val notYetDueRetry =
				OutboxEvent.create(
					eventId = EventId("evt_${uniqueSuffix()}"),
					aggregateType = "Payment",
					aggregateId = "pay_test_003",
					eventType = "payment.created",
					payload = """{"paymentId":"pay_test_003"}""",
					occurredAt = NOW,
					createdAt = NOW,
				)
			notYetDueRetry.startPublishing(NOW.plusSeconds(1))
			notYetDueRetry.scheduleRetry(NOW.plusSeconds(3_600), NOW.plusSeconds(2))
			val published =
				OutboxEvent.create(
					eventId = EventId("evt_${uniqueSuffix()}"),
					aggregateType = "Payment",
					aggregateId = "pay_test_004",
					eventType = "payment.created",
					payload = """{"paymentId":"pay_test_004"}""",
					occurredAt = NOW,
					createdAt = NOW,
				)
			published.startPublishing(NOW.plusSeconds(1))
			published.publish(NOW.plusSeconds(2))
			listOf(pending, dueRetry, notYetDueRetry, published).forEach { adapter.save(it) }

			val result = adapter.findPendingPublication(NOW.plusSeconds(2))

			val resultIds = result.map { it.eventId }
			resultIds.contains(pending.eventId) shouldBe true
			resultIds.contains(dueRetry.eventId) shouldBe true
			resultIds.contains(notYetDueRetry.eventId) shouldBe false
			resultIds.contains(published.eventId) shouldBe false
		}
	})
