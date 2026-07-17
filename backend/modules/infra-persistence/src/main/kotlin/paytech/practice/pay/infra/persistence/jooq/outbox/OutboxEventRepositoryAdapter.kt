package paytech.practice.pay.infra.persistence.jooq.outbox

import org.jooq.DSLContext
import org.jooq.JSON
import org.springframework.stereotype.Repository
import paytech.practice.pay.application.port.outbound.OutboxEventRepository
import paytech.practice.pay.dbcore.jooq.tables.OutboxEvent.Companion.OUTBOX_EVENT
import paytech.practice.pay.domain.outbox.OutboxEvent
import paytech.practice.pay.infra.persistence.jooq.toUtcLocalDateTime

/**
 * jOOQ로 [OutboxEventRepository] Port를 구현한다.
 *
 * Port가 `save`만 정의하는 것과 동일하게(발행 Worker Use Case가 생기기 전까지는
 * 새 이벤트 적재만 필요) 이 Adapter도 INSERT만 한다.
 *
 * `payload` 컬럼은 MySQL `JSON` 타입이라 jOOQ가 `String`이 아니라 [org.jooq.JSON]으로
 * 매핑한다 — [OutboxEvent.payload]는 이미 직렬화된 JSON 문자열이므로 `JSON.valueOf`로
 * 감싸기만 하면 된다(그 반대인 읽기 경로는 이 슬라이스에서 아직 필요 없다).
 */
@Repository
class OutboxEventRepositoryAdapter(
	private val dsl: DSLContext,
) : OutboxEventRepository {
	override fun save(outboxEvent: OutboxEvent) {
		dsl
			.newRecord(OUTBOX_EVENT)
			.apply {
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
			}.insert()
	}
}
