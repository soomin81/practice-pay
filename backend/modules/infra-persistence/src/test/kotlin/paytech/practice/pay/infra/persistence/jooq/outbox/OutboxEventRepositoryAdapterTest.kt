package paytech.practice.pay.infra.persistence.jooq.outbox

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import paytech.practice.pay.dbcore.jooq.tables.OutboxEvent.Companion.OUTBOX_EVENT
import paytech.practice.pay.domain.outbox.OutboxEvent
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
			// MySQL's JSON column type re-serializes the value on write (e.g. inserts a
			// space after ':') rather than preserving the exact literal we sent, so this
			// only checks the content round-tripped, not byte-for-byte formatting.
			record.payload!!.data() shouldContain "pay_test_001"
		}
	})
