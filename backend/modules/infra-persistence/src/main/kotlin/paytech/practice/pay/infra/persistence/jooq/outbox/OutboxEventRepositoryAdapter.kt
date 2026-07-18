package paytech.practice.pay.infra.persistence.jooq.outbox

import org.jooq.DSLContext
import org.jooq.JSON
import org.springframework.stereotype.Repository
import paytech.practice.pay.application.port.outbound.OutboxEventRepository
import paytech.practice.pay.dbcore.jooq.tables.OutboxEvent.Companion.OUTBOX_EVENT
import paytech.practice.pay.dbcore.jooq.tables.records.OutboxEventRecord
import paytech.practice.pay.domain.outbox.OutboxEvent
import paytech.practice.pay.domain.outbox.OutboxEventStatus
import paytech.practice.pay.domain.shared.EventId
import paytech.practice.pay.infra.persistence.jooq.toUtcInstant
import paytech.practice.pay.infra.persistence.jooq.toUtcLocalDateTime
import java.time.Instant

/**
 * jOOQ로 [OutboxEventRepository] Port를 구현한다.
 *
 * `outbox_event`는 `version` 컬럼이 없다(`OutboxEvent`의 KDoc 참고) —
 * `BlockchainTransaction`/`Payment`와 달리 낙관적 잠금 없이 단순 UPDATE로 상태
 * 전이를 반영한다. 여러 발행 Worker 인스턴스가 동시에 같은 행을 집어가는 경합은
 * 이 Adapter가 막지 않는다 — 이 MVP는 배치 앱을 단일 인스턴스로만 돌린다고
 * 전제한다(`OutboxEvent`의 KDoc이 언급한 `SELECT ... FOR UPDATE` 같은 방어는
 * 아직 하지 않는다, 알려진 gap).
 *
 * `payload` 컬럼은 MySQL `JSON` 타입이라 jOOQ가 `String`이 아니라 [org.jooq.JSON]으로
 * 매핑한다 — [OutboxEvent.payload]는 이미 직렬화된 JSON 문자열이므로 쓸 때는
 * `JSON.valueOf`로 감싸고, 읽을 때는 `JSON.data()`로 다시 문자열을 꺼낸다.
 */
@Repository
class OutboxEventRepositoryAdapter(
	private val dsl: DSLContext,
) : OutboxEventRepository {
	override fun save(outboxEvent: OutboxEvent) {
		val existing =
			dsl
				.selectFrom(OUTBOX_EVENT)
				.where(OUTBOX_EVENT.EVENT_ID.eq(outboxEvent.eventId.value))
				.fetchOne()

		if (existing == null) {
			dsl
				.newRecord(OUTBOX_EVENT)
				.apply { fillFrom(outboxEvent) }
				.insert()
		} else {
			dsl
				.update(OUTBOX_EVENT)
				.set(OUTBOX_EVENT.EVENT_STATUS, outboxEvent.status.name)
				.set(OUTBOX_EVENT.RETRY_COUNT, outboxEvent.retryCount)
				.set(OUTBOX_EVENT.NEXT_RETRY_AT, outboxEvent.nextRetryAt?.toUtcLocalDateTime())
				.set(OUTBOX_EVENT.PUBLISHED_AT, outboxEvent.publishedAt?.toUtcLocalDateTime())
				.set(OUTBOX_EVENT.UPDATED_AT, outboxEvent.updatedAt.toUtcLocalDateTime())
				.where(OUTBOX_EVENT.OUTBOX_EVENT_SEQ.eq(existing.outboxEventSeq))
				.execute()
				.also { updatedRows ->
					check(updatedRows == 1) { "OutboxEvent(${outboxEvent.eventId.value}) 저장에 실패했습니다." }
				}
		}
	}

	override fun findById(eventId: EventId): OutboxEvent? =
		dsl
			.selectFrom(OUTBOX_EVENT)
			.where(OUTBOX_EVENT.EVENT_ID.eq(eventId.value))
			.fetchOne()
			?.toDomain()

	override fun findPendingPublication(now: Instant): List<OutboxEvent> =
		dsl
			.selectFrom(OUTBOX_EVENT)
			.where(OUTBOX_EVENT.EVENT_STATUS.eq(OutboxEventStatus.PENDING.name))
			.or(
				OUTBOX_EVENT.EVENT_STATUS
					.eq(OutboxEventStatus.RETRY_WAITING.name)
					.and(OUTBOX_EVENT.NEXT_RETRY_AT.le(now.toUtcLocalDateTime())),
			).orderBy(OUTBOX_EVENT.CREATED_AT.asc())
			.fetch()
			.map { it.toDomain() }

	private fun OutboxEventRecord.fillFrom(outboxEvent: OutboxEvent) {
		eventId = outboxEvent.eventId.value
		aggregateType = outboxEvent.aggregateType
		aggregateId = outboxEvent.aggregateId
		eventType = outboxEvent.eventType
		payload = JSON.valueOf(outboxEvent.payload)
		eventStatus = outboxEvent.status.name
		retryCount = outboxEvent.retryCount
		nextRetryAt = outboxEvent.nextRetryAt?.toUtcLocalDateTime()
		occurredAt = outboxEvent.occurredAt.toUtcLocalDateTime()
		publishedAt = outboxEvent.publishedAt?.toUtcLocalDateTime()
		createdAt = outboxEvent.createdAt.toUtcLocalDateTime()
		updatedAt = outboxEvent.updatedAt.toUtcLocalDateTime()
	}

	private fun OutboxEventRecord.toDomain(): OutboxEvent =
		OutboxEvent.reconstitute(
			eventId = EventId(eventId!!),
			aggregateType = aggregateType!!,
			aggregateId = aggregateId!!,
			eventType = eventType!!,
			payload = payload!!.data(),
			occurredAt = occurredAt!!.toUtcInstant(),
			createdAt = createdAt!!.toUtcInstant(),
			status = OutboxEventStatus.valueOf(eventStatus!!),
			retryCount = retryCount!!,
			nextRetryAt = nextRetryAt?.toUtcInstant(),
			publishedAt = publishedAt?.toUtcInstant(),
			updatedAt = updatedAt!!.toUtcInstant(),
		)
}
