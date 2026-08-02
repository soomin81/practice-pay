package paytech.practice.pay.domain.webhook

import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.shared.EventId
import paytech.practice.pay.domain.shared.HttpUrl
import java.time.Instant

/**
 * Webhook 전송(WebhookDelivery) Aggregate Root다.
 *
 * 전송 URL, Payload, 시도 횟수, 재시도 일정, 최종 상태를 관리한다. PG가 가맹점
 * 서버로 비동기 결과를 통지하는 방식이며(`docs/domain/glossary.md`), 상태는 이
 * 클래스의 메서드를 통해서만 변경된다. `Merchant`는 ID로만 참조한다.
 *
 * [payload]는 직렬화된 JSON 문자열 그대로 보관한다 — 도메인은 Spring/jOOQ뿐
 * 아니라 JSON 라이브러리에도 의존하지 않으므로, 실제 JSON 파싱/생성은 애플리케이션
 * 또는 어댑터 계층의 책임이다.
 *
 * 실패 시 몇 번까지 재시도할지(최대 횟수)는 이 Aggregate가 결정하지 않는다 —
 * [attemptCount]를 노출할 뿐이고, 재시도할지 최종 실패 처리할지는 호출부가
 * [scheduleRetry] 또는 [fail] 중 하나를 선택해서 판단한다.
 *
 * 인스턴스는 [create]로 새로 만들거나 [reconstitute]로 저장된 값을 복원해서 얻는다.
 *
 * @see docs/domain/domain-model.md
 * @see docs/domain/state-transitions.md
 */
class WebhookDelivery private constructor(
	val id: WebhookDeliveryId,
	val merchantId: MerchantId,
	val eventId: EventId,
	val eventType: String,
	val aggregateType: String,
	val aggregateId: String,
	val destinationUrl: HttpUrl,
	val payload: String,
	val createdAt: Instant,
	status: WebhookDeliveryStatus,
	attemptCount: Int,
	lastHttpStatus: Int?,
	lastErrorMessage: String?,
	nextRetryAt: Instant?,
	deliveredAt: Instant?,
	updatedAt: Instant,
) {
	var status: WebhookDeliveryStatus = status
		private set

	/** 지금까지 시도한 전송 횟수. [startDelivering]을 호출할 때마다 늘어난다. */
	var attemptCount: Int = attemptCount
		private set

	var lastHttpStatus: Int? = lastHttpStatus
		private set

	var lastErrorMessage: String? = lastErrorMessage
		private set

	var nextRetryAt: Instant? = nextRetryAt
		private set

	/** 전송이 `SUCCEEDED`로 확정된 시각. `SUCCEEDED` 상태에서는 항상 값이 있다. */
	var deliveredAt: Instant? = deliveredAt
		private set

	var updatedAt: Instant = updatedAt
		private set

	init {
		require(eventType.isNotBlank()) { "eventType은 공백일 수 없습니다." }
		require(aggregateType.isNotBlank()) { "aggregateType은 공백일 수 없습니다." }
		require(aggregateId.isNotBlank()) { "aggregateId는 공백일 수 없습니다." }
		require(payload.isNotBlank()) { "payload는 공백일 수 없습니다." }
		require(attemptCount >= 0) { "attemptCount는 음수일 수 없습니다: $attemptCount" }
		require(lastHttpStatus == null || lastHttpStatus in 100..599) {
			"lastHttpStatus는 100에서 599 사이여야 합니다: $lastHttpStatus"
		}
		require(status != WebhookDeliveryStatus.SUCCEEDED || deliveredAt != null) {
			"SUCCEEDED 상태는 deliveredAt이 반드시 있어야 합니다."
		}
	}

	/** (`PENDING` 또는 `RETRY_WAITING`) → `DELIVERING`. 전송을 시도하며 시도 횟수를 늘린다. */
	fun startDelivering(changedAt: Instant) {
		checkTransition(
			status == WebhookDeliveryStatus.PENDING || status == WebhookDeliveryStatus.RETRY_WAITING,
			WebhookDeliveryStatus.DELIVERING,
		)
		status = WebhookDeliveryStatus.DELIVERING
		attemptCount += 1
		nextRetryAt = null
		updatedAt = changedAt
	}

	/** `DELIVERING` → `SUCCEEDED`. */
	fun succeed(
		httpStatus: Int,
		deliveredAt: Instant,
	) {
		checkTransition(status == WebhookDeliveryStatus.DELIVERING, WebhookDeliveryStatus.SUCCEEDED)
		status = WebhookDeliveryStatus.SUCCEEDED
		lastHttpStatus = httpStatus
		this.deliveredAt = deliveredAt
		updatedAt = deliveredAt
	}

	/** `DELIVERING` → `RETRY_WAITING`. 다음 재시도 시각을 기록한다. */
	fun scheduleRetry(
		httpStatus: Int?,
		errorMessage: String?,
		nextRetryAt: Instant,
		changedAt: Instant,
	) {
		checkTransition(status == WebhookDeliveryStatus.DELIVERING, WebhookDeliveryStatus.RETRY_WAITING)
		status = WebhookDeliveryStatus.RETRY_WAITING
		lastHttpStatus = httpStatus
		lastErrorMessage = errorMessage
		this.nextRetryAt = nextRetryAt
		updatedAt = changedAt
	}

	/** `DELIVERING` → `FAILED`. 최대 재시도 횟수를 초과했을 때 호출부가 선택해서 호출한다. */
	fun fail(
		httpStatus: Int?,
		errorMessage: String?,
		failedAt: Instant,
	) {
		checkTransition(status == WebhookDeliveryStatus.DELIVERING, WebhookDeliveryStatus.FAILED)
		status = WebhookDeliveryStatus.FAILED
		lastHttpStatus = httpStatus
		lastErrorMessage = errorMessage
		updatedAt = failedAt
	}

	/**
	 * `FAILED` → `PENDING`. **내부 운영자가 명시적으로 재전송을 실행할 때만** 호출한다.
	 *
	 * 이 시스템에서 **유일하게 종료 상태를 되돌리는 전이**이고, 공통 규칙("종료 상태는
	 * 재사용하지 않는다")의 의도된 예외다 — 근거는 `docs/domain/state-transitions.md`의
	 * "수동 재전송" 절에 있다. 요약하면: 자동 재시도가 소진된 원인은 대개 가맹점 쪽
	 * 일시 장애이고, `uk_webhook_event_merchant` 때문에 같은 이벤트로 새 행을 만들 수도
	 * 없어서 기존 행을 되돌리는 것 말고는 길이 없다.
	 *
	 * **`attemptCount`를 초기화하지 않는다.** 그 값은 "이 이벤트를 몇 번 시도했나"라는
	 * 누적 사실이라 0으로 되돌리면 이력이 지워진다. 그래서 재전송 한 번은 자동 재시도
	 * 예산을 새로 주는 것이 아니라 **시도 한 번**을 뜻한다.
	 *
	 * `nextRetryAt`은 비운다 — 자동 재시도 대기가 아니라 즉시 발행 대상이다.
	 *
	 * `SUCCEEDED`는 되돌리지 않는다(중복 발송이지 재전송이 아니다).
	 */
	fun redeliver(changedAt: Instant) {
		checkTransition(status == WebhookDeliveryStatus.FAILED, WebhookDeliveryStatus.PENDING)
		status = WebhookDeliveryStatus.PENDING
		nextRetryAt = null
		updatedAt = changedAt
	}

	private fun checkTransition(
		allowed: Boolean,
		target: WebhookDeliveryStatus,
	) {
		check(allowed) { "WebhookDelivery 상태를 $status 에서 $target (으)로 전이할 수 없습니다." }
	}

	companion object {
		/** 새 Webhook 전송을 `PENDING` 상태로 생성한다. */
		fun create(
			id: WebhookDeliveryId,
			merchantId: MerchantId,
			eventId: EventId,
			eventType: String,
			aggregateType: String,
			aggregateId: String,
			destinationUrl: HttpUrl,
			payload: String,
			createdAt: Instant,
		): WebhookDelivery =
			WebhookDelivery(
				id = id,
				merchantId = merchantId,
				eventId = eventId,
				eventType = eventType,
				aggregateType = aggregateType,
				aggregateId = aggregateId,
				destinationUrl = destinationUrl,
				payload = payload,
				createdAt = createdAt,
				status = WebhookDeliveryStatus.PENDING,
				attemptCount = 0,
				lastHttpStatus = null,
				lastErrorMessage = null,
				nextRetryAt = null,
				deliveredAt = null,
				updatedAt = createdAt,
			)

		/** 영속 계층에 저장되어 있던 값으로 Aggregate를 복원한다. */
		fun reconstitute(
			id: WebhookDeliveryId,
			merchantId: MerchantId,
			eventId: EventId,
			eventType: String,
			aggregateType: String,
			aggregateId: String,
			destinationUrl: HttpUrl,
			payload: String,
			createdAt: Instant,
			status: WebhookDeliveryStatus,
			attemptCount: Int,
			lastHttpStatus: Int?,
			lastErrorMessage: String?,
			nextRetryAt: Instant?,
			deliveredAt: Instant?,
			updatedAt: Instant,
		): WebhookDelivery =
			WebhookDelivery(
				id = id,
				merchantId = merchantId,
				eventId = eventId,
				eventType = eventType,
				aggregateType = aggregateType,
				aggregateId = aggregateId,
				destinationUrl = destinationUrl,
				payload = payload,
				createdAt = createdAt,
				status = status,
				attemptCount = attemptCount,
				lastHttpStatus = lastHttpStatus,
				lastErrorMessage = lastErrorMessage,
				nextRetryAt = nextRetryAt,
				deliveredAt = deliveredAt,
				updatedAt = updatedAt,
			)
	}
}
