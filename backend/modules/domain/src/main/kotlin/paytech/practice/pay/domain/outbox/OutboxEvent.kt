package paytech.practice.pay.domain.outbox

import paytech.practice.pay.domain.shared.EventId
import java.time.Instant

/**
 * Outbox 이벤트(OutboxEvent) Aggregate Root다.
 *
 * Transactional Outbox 패턴에서, 다른 Aggregate의 상태 변경과 같은 DB 트랜잭션
 * 안에서 함께 적재되는 "발행 대기 중인 도메인 이벤트" 레코드다
 * (`docs/architecture/persistence-jooq.md`의 "트랜잭션 경계", "Async 부수효과는
 * Outbox 패턴을 통한다" 참고). 별도 발행 Worker가 이 레코드를 읽어 실제 메시지
 * 발행(예: Webhook 트리거)을 수행하고 상태를 갱신한다.
 *
 * [payload]는 직렬화된 JSON 문자열 그대로 보관한다 — 도메인은 Spring/jOOQ뿐
 * 아니라 JSON 라이브러리에도 의존하지 않으므로, 실제 JSON 직렬화는 애플리케이션
 * 또는 어댑터 계층의 책임이다([paytech.practice.pay.domain.webhook.WebhookDelivery]와
 * 동일한 규칙).
 *
 * 다른 Aggregate와 달리 별도의 공개 ID VO가 없다 — `outbox_event` 테이블 자체에
 * `outbox_event_id` 같은 컬럼이 없고, [eventId]가 곧 이 레코드의 대외 식별자다
 * (`docs/architecture/mvp-scope.md`가 아니라 스키마에서 그대로 확인된다). 마찬가지로
 * `updated_at`은 있지만 `version` 컬럼은 없다 — 낙관적 잠금 대신, 발행 Worker가
 * 상태를 `PENDING`/`RETRY_WAITING`인 행만 골라 처리하는 방식(예: `SELECT ... FOR
 * UPDATE`)으로 동시성을 다룬다고 추론했다.
 *
 * 인스턴스는 [create]로 새로 만들거나 [reconstitute]로 저장된 값을 복원해서 얻는다.
 *
 * @see OutboxEventStatus
 */
class OutboxEvent private constructor(
	val eventId: EventId,
	val aggregateType: String,
	val aggregateId: String,
	val eventType: String,
	val payload: String,
	val occurredAt: Instant,
	val createdAt: Instant,
	status: OutboxEventStatus,
	retryCount: Int,
	nextRetryAt: Instant?,
	publishedAt: Instant?,
	updatedAt: Instant,
) {
	var status: OutboxEventStatus = status
		private set

	/** 지금까지 시도한 발행 횟수. [startPublishing]을 호출할 때마다 늘어난다. */
	var retryCount: Int = retryCount
		private set

	var nextRetryAt: Instant? = nextRetryAt
		private set

	/** 이벤트가 `PUBLISHED`로 확정된 시각. `PUBLISHED` 상태에서는 항상 값이 있다. */
	var publishedAt: Instant? = publishedAt
		private set

	var updatedAt: Instant = updatedAt
		private set

	init {
		require(aggregateType.isNotBlank()) { "aggregateType은 공백일 수 없습니다." }
		require(aggregateId.isNotBlank()) { "aggregateId는 공백일 수 없습니다." }
		require(eventType.isNotBlank()) { "eventType은 공백일 수 없습니다." }
		require(payload.isNotBlank()) { "payload는 공백일 수 없습니다." }
		require(retryCount >= 0) { "retryCount는 음수일 수 없습니다: $retryCount" }
		require(status != OutboxEventStatus.PUBLISHED || publishedAt != null) {
			"PUBLISHED 상태는 publishedAt이 반드시 있어야 합니다."
		}
	}

	/** (`PENDING` 또는 `RETRY_WAITING`) → `PROCESSING`. 발행을 시도하며 시도 횟수를 늘린다. */
	fun startPublishing(changedAt: Instant) {
		checkTransition(
			status == OutboxEventStatus.PENDING || status == OutboxEventStatus.RETRY_WAITING,
			OutboxEventStatus.PROCESSING,
		)
		status = OutboxEventStatus.PROCESSING
		retryCount += 1
		nextRetryAt = null
		updatedAt = changedAt
	}

	/** `PROCESSING` → `PUBLISHED`. */
	fun publish(publishedAt: Instant) {
		checkTransition(status == OutboxEventStatus.PROCESSING, OutboxEventStatus.PUBLISHED)
		status = OutboxEventStatus.PUBLISHED
		this.publishedAt = publishedAt
		updatedAt = publishedAt
	}

	/** `PROCESSING` → `RETRY_WAITING`. 다음 재시도 시각을 기록한다. */
	fun scheduleRetry(
		nextRetryAt: Instant,
		changedAt: Instant,
	) {
		checkTransition(status == OutboxEventStatus.PROCESSING, OutboxEventStatus.RETRY_WAITING)
		status = OutboxEventStatus.RETRY_WAITING
		this.nextRetryAt = nextRetryAt
		updatedAt = changedAt
	}

	/** `PROCESSING` → `FAILED`. 최대 재시도 횟수를 초과했을 때 호출부가 선택해서 호출한다. */
	fun fail(failedAt: Instant) {
		checkTransition(status == OutboxEventStatus.PROCESSING, OutboxEventStatus.FAILED)
		status = OutboxEventStatus.FAILED
		updatedAt = failedAt
	}

	/**
	 * `FAILED` → `PENDING`. **내부 운영자가 Webhook 재전송을 실행할 때만** 호출한다.
	 *
	 * `WebhookDelivery.redeliver`와 **짝으로만** 쓰인다 — 전송을 되돌려 놓아도 이쪽이
	 * `FAILED`로 남아 있으면 발행 Worker(`findPendingPublication`)가 대상으로 집지 않아
	 * 아무 일도 일어나지 않는다. 되돌려 놓으면 **평소와 똑같은 경로로** 다시 발행된다
	 * (재전송 전용 경로를 따로 만들지 않는 이유다).
	 *
	 * 공통 규칙("종료 상태는 재사용하지 않는다")의 의도된 예외이고, 근거는
	 * `docs/domain/state-transitions.md`의 "수동 재전송" 절에 있다.
	 */
	fun reopenForRedelivery(changedAt: Instant) {
		checkTransition(status == OutboxEventStatus.FAILED, OutboxEventStatus.PENDING)
		status = OutboxEventStatus.PENDING
		nextRetryAt = null
		updatedAt = changedAt
	}

	private fun checkTransition(
		allowed: Boolean,
		target: OutboxEventStatus,
	) {
		check(allowed) { "OutboxEvent 상태를 $status 에서 $target (으)로 전이할 수 없습니다." }
	}

	companion object {
		/** 새 Outbox 이벤트를 `PENDING` 상태로 생성한다. */
		fun create(
			eventId: EventId,
			aggregateType: String,
			aggregateId: String,
			eventType: String,
			payload: String,
			occurredAt: Instant,
			createdAt: Instant,
		): OutboxEvent =
			OutboxEvent(
				eventId = eventId,
				aggregateType = aggregateType,
				aggregateId = aggregateId,
				eventType = eventType,
				payload = payload,
				occurredAt = occurredAt,
				createdAt = createdAt,
				status = OutboxEventStatus.PENDING,
				retryCount = 0,
				nextRetryAt = null,
				publishedAt = null,
				updatedAt = createdAt,
			)

		/** 영속 계층에 저장되어 있던 값으로 Aggregate를 복원한다. */
		fun reconstitute(
			eventId: EventId,
			aggregateType: String,
			aggregateId: String,
			eventType: String,
			payload: String,
			occurredAt: Instant,
			createdAt: Instant,
			status: OutboxEventStatus,
			retryCount: Int,
			nextRetryAt: Instant?,
			publishedAt: Instant?,
			updatedAt: Instant,
		): OutboxEvent =
			OutboxEvent(
				eventId = eventId,
				aggregateType = aggregateType,
				aggregateId = aggregateId,
				eventType = eventType,
				payload = payload,
				occurredAt = occurredAt,
				createdAt = createdAt,
				status = status,
				retryCount = retryCount,
				nextRetryAt = nextRetryAt,
				publishedAt = publishedAt,
				updatedAt = updatedAt,
			)
	}
}
