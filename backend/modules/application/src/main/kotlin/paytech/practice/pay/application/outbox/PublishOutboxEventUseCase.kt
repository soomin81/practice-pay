package paytech.practice.pay.application.outbox

import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.MerchantRepository
import paytech.practice.pay.application.port.outbound.OutboxEventRepository
import paytech.practice.pay.application.port.outbound.PaymentRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.application.port.outbound.WebhookDeliveryRepository
import paytech.practice.pay.application.port.outbound.WebhookSendResult
import paytech.practice.pay.application.port.outbound.WebhookSender
import paytech.practice.pay.domain.merchant.Merchant
import paytech.practice.pay.domain.outbox.OutboxEvent
import paytech.practice.pay.domain.outbox.OutboxEventStatus
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.webhook.WebhookDelivery
import paytech.practice.pay.domain.webhook.WebhookDeliveryId
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * "OutboxEvent 발행" Use Case다 — `OutboxEvent`의 자기 KDoc이 "별도 발행 Worker가
 * 이 레코드를 읽어 실제 메시지 발행(예: Webhook 트리거)을 수행하고 상태를
 * 갱신한다"고 남겨뒀던 그 Worker다.
 *
 * 이미 `PENDING`/`RETRY_WAITING` 중 하나인 `OutboxEvent` 하나를 대상으로 한 발행
 * **시도 한 번**이다 — `ConfirmBlockchainTransactionUseCase`가 하나의
 * `BlockchainTransaction`을 대상으로 한 폴링 한 번인 것과 같은 모양이다. 이
 * Use Case 자체는 스스로 반복하지 않는다 — `docs/database/database-design.md`의
 * "Outbox 발행: `event_status + next_retry_at + created_at`" 인덱스가 암시하는
 * 대로, 향후 Worker(`apps:batch`)가 대상 목록을 뽑아 하나씩 호출하는 것을 전제로
 * 설계했다.
 *
 * 흐름:
 * 1. `OutboxEvent.startPublishing()`으로 `PROCESSING`으로 넘어간다.
 * 2. `aggregateType`으로 대상 Payment를 찾고, 그 `Payment.merchantId`로 Merchant를
 *    찾는다 — 지금은 `CreatePaymentUseCase`/`ConfirmBlockchainTransactionUseCase`
 *    둘 다 `aggregateType = "Payment"`로만 이벤트를 만들어서 그 경우만 다룬다.
 * 3. `Merchant.webhookUrl`이 없으면(가맹점이 Webhook을 설정하지 않은 정상적인
 *    경우) 보낼 곳이 없으니 `WebhookDelivery`를 만들지 않고 바로 `publish()`로
 *    끝낸다.
 * 4. `webhookUrl`이 있으면 `WebhookDelivery`를 찾거나(재시도, `(eventId,
 *    merchantId)`로 멱등) 새로 만들고, `startDelivering()` → [WebhookSender.send]
 *    → 2xx면 성공(`WebhookDelivery.succeed` + `OutboxEvent.publish`), 아니면
 *    [MAX_WEBHOOK_ATTEMPTS] 미만이면 재시도 예약(`scheduleRetry`를 두 Aggregate
 *    모두에), 이상이면 최종 실패(`fail`을 두 Aggregate 모두에) 처리한다.
 *
 * `OutboxEvent + WebhookDelivery`를 함께 저장하는 이 트랜잭션 경계는
 * `docs/architecture/persistence-jooq.md`가 명시한 세 경계(결제 생성/결제 완료/
 * 환전 완료) 어디에도 없다 — `SubmitPaymentTransactionUseCase`의 "결제 제출"
 * 경계처럼 이 Use Case가 새로 정의한 것이다.
 *
 * [MAX_WEBHOOK_ATTEMPTS]/[RETRY_DELAY]는 `docs/`에 값이 없어 이 Use Case가 상수로
 * 고정했다 — `docs/domain/state-transitions.md`의 `WebhookDelivery` KDoc도 "최대
 * 횟수"를 명시하지 않고 호출부(이 Use Case)의 판단으로 남겨뒀다.
 */
class PublishOutboxEventUseCase(
	private val outboxEventRepository: OutboxEventRepository,
	private val webhookDeliveryRepository: WebhookDeliveryRepository,
	private val paymentRepository: PaymentRepository,
	private val merchantRepository: MerchantRepository,
	private val webhookSender: WebhookSender,
	private val idGenerator: IdGenerator,
	private val transactionManager: TransactionManager,
	private val clock: Clock,
) {
	fun execute(command: PublishOutboxEventCommand): PublishOutboxEventResult {
		val outboxEvent =
			outboxEventRepository.findById(command.eventId)
				?: throw OutboxEventNotFoundException(command.eventId)
		check(
			outboxEvent.status == OutboxEventStatus.PENDING || outboxEvent.status == OutboxEventStatus.RETRY_WAITING,
		) {
			"OutboxEvent(${outboxEvent.eventId.value})가 이미 처리 중이거나 종료 상태입니다: ${outboxEvent.status}"
		}

		val now = clock.instant()
		outboxEvent.startPublishing(now)

		val merchant = resolveMerchant(outboxEvent)
		val webhookUrl = merchant.webhookUrl

		if (webhookUrl == null) {
			outboxEvent.publish(now)
			return transactionManager.runInTransaction {
				outboxEventRepository.save(outboxEvent)
				resultOf(outboxEvent)
			}
		}

		val webhookDelivery =
			webhookDeliveryRepository.findByEventIdAndMerchantId(outboxEvent.eventId, merchant.id)
				?: WebhookDelivery.create(
					id = WebhookDeliveryId("wh_" + idGenerator.newId()),
					merchantId = merchant.id,
					eventId = outboxEvent.eventId,
					eventType = outboxEvent.eventType,
					aggregateType = outboxEvent.aggregateType,
					aggregateId = outboxEvent.aggregateId,
					destinationUrl = webhookUrl,
					payload = outboxEvent.payload,
					createdAt = now,
				)
		webhookDelivery.startDelivering(now)

		when (val sendResult = webhookSender.send(webhookUrl, outboxEvent.payload)) {
			is WebhookSendResult.Responded ->
				if (sendResult.httpStatus in SUCCESS_HTTP_STATUS_RANGE) {
					webhookDelivery.succeed(sendResult.httpStatus, now)
					outboxEvent.publish(now)
				} else {
					recordFailure(webhookDelivery, outboxEvent, sendResult.httpStatus, "HTTP ${sendResult.httpStatus}", now)
				}
			is WebhookSendResult.Failed -> recordFailure(webhookDelivery, outboxEvent, null, sendResult.errorMessage, now)
		}

		return transactionManager.runInTransaction {
			webhookDeliveryRepository.save(webhookDelivery)
			outboxEventRepository.save(outboxEvent)
			resultOf(outboxEvent)
		}
	}

	private fun resolveMerchant(outboxEvent: OutboxEvent): Merchant =
		when (outboxEvent.aggregateType) {
			"Payment" -> {
				val payment =
					paymentRepository.findById(PaymentId(outboxEvent.aggregateId))
						?: error("OutboxEvent(${outboxEvent.eventId.value})의 Payment(${outboxEvent.aggregateId})를 찾을 수 없습니다.")
				merchantRepository.findById(payment.merchantId)
					?: error("Payment(${payment.id.value})의 Merchant(${payment.merchantId.value})를 찾을 수 없습니다.")
			}
			else -> error("지원하지 않는 aggregateType입니다: ${outboxEvent.aggregateType}")
		}

	private fun recordFailure(
		webhookDelivery: WebhookDelivery,
		outboxEvent: OutboxEvent,
		httpStatus: Int?,
		errorMessage: String,
		now: Instant,
	) {
		if (webhookDelivery.attemptCount >= MAX_WEBHOOK_ATTEMPTS) {
			webhookDelivery.fail(httpStatus, errorMessage, now)
			outboxEvent.fail(now)
		} else {
			val nextRetryAt = now.plus(RETRY_DELAY)
			webhookDelivery.scheduleRetry(httpStatus, errorMessage, nextRetryAt, now)
			outboxEvent.scheduleRetry(nextRetryAt, now)
		}
	}

	private fun resultOf(outboxEvent: OutboxEvent): PublishOutboxEventResult =
		PublishOutboxEventResult(eventId = outboxEvent.eventId, outboxEventStatus = outboxEvent.status)

	companion object {
		/** 이 횟수를 채우고도 실패하면 재시도 대신 최종 실패(`FAILED`)로 처리한다. */
		private const val MAX_WEBHOOK_ATTEMPTS = 5

		/** 재시도 간격. 지수 백오프 없이 고정 간격을 쓰는 MVP 단순화다. */
		private val RETRY_DELAY: Duration = Duration.ofMinutes(1)

		private val SUCCESS_HTTP_STATUS_RANGE = 200..299
	}
}
