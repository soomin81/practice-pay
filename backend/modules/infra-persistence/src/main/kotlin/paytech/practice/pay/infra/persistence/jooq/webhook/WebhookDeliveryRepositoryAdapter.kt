package paytech.practice.pay.infra.persistence.jooq.webhook

import org.jooq.DSLContext
import org.jooq.JSON
import org.springframework.stereotype.Repository
import paytech.practice.pay.application.port.outbound.WebhookDeliveryRepository
import paytech.practice.pay.dbcore.jooq.tables.WebhookDelivery.Companion.WEBHOOK_DELIVERY
import paytech.practice.pay.dbcore.jooq.tables.records.WebhookDeliveryRecord
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.shared.EventId
import paytech.practice.pay.domain.shared.HttpUrl
import paytech.practice.pay.domain.webhook.WebhookDelivery
import paytech.practice.pay.domain.webhook.WebhookDeliveryId
import paytech.practice.pay.domain.webhook.WebhookDeliveryStatus
import paytech.practice.pay.infra.persistence.jooq.merchantId
import paytech.practice.pay.infra.persistence.jooq.merchantSeq
import paytech.practice.pay.infra.persistence.jooq.toUtcInstant
import paytech.practice.pay.infra.persistence.jooq.toUtcLocalDateTime

/**
 * jOOQ로 [WebhookDeliveryRepository] Port를 구현한다.
 *
 * `save`의 낙관적 잠금 한계는 [paytech.practice.pay.infra.persistence.jooq.payment.PaymentRepositoryAdapter]와
 * 동일하다 — 도메인 [WebhookDelivery]가 자신의 `version`을 모르기 때문에, DB에서
 * 방금 읽은 version을 그대로 +1 해서 쓴다.
 */
@Repository
class WebhookDeliveryRepositoryAdapter(
	private val dsl: DSLContext,
) : WebhookDeliveryRepository {
	override fun save(webhookDelivery: WebhookDelivery) {
		val existing =
			dsl
				.selectFrom(WEBHOOK_DELIVERY)
				.where(WEBHOOK_DELIVERY.WEBHOOK_DELIVERY_ID.eq(webhookDelivery.id.value))
				.fetchOne()

		if (existing == null) {
			dsl
				.newRecord(WEBHOOK_DELIVERY)
				.apply {
					fillFrom(webhookDelivery)
					version = 0L
				}.insert()
		} else {
			dsl
				.update(WEBHOOK_DELIVERY)
				.set(WEBHOOK_DELIVERY.DELIVERY_STATUS, webhookDelivery.status.name)
				.set(WEBHOOK_DELIVERY.ATTEMPT_COUNT, webhookDelivery.attemptCount)
				.set(WEBHOOK_DELIVERY.LAST_HTTP_STATUS, webhookDelivery.lastHttpStatus)
				.set(WEBHOOK_DELIVERY.LAST_ERROR_MESSAGE, webhookDelivery.lastErrorMessage)
				.set(WEBHOOK_DELIVERY.NEXT_RETRY_AT, webhookDelivery.nextRetryAt?.toUtcLocalDateTime())
				.set(WEBHOOK_DELIVERY.DELIVERED_AT, webhookDelivery.deliveredAt?.toUtcLocalDateTime())
				.set(WEBHOOK_DELIVERY.UPDATED_AT, webhookDelivery.updatedAt.toUtcLocalDateTime())
				.set(WEBHOOK_DELIVERY.VERSION, (existing.version ?: 0L) + 1)
				.where(WEBHOOK_DELIVERY.WEBHOOK_DELIVERY_SEQ.eq(existing.webhookDeliverySeq))
				.and(WEBHOOK_DELIVERY.VERSION.eq(existing.version))
				.execute()
				.also { updatedRows ->
					check(updatedRows == 1) {
						"WebhookDelivery(${webhookDelivery.id.value}) 저장에 실패했습니다 — " +
							"동시에 변경된 것으로 보입니다(예상 version=${existing.version})."
					}
				}
		}
	}

	override fun findByEventIdAndMerchantId(
		eventId: EventId,
		merchantId: MerchantId,
	): WebhookDelivery? =
		dsl
			.selectFrom(WEBHOOK_DELIVERY)
			.where(WEBHOOK_DELIVERY.EVENT_ID.eq(eventId.value))
			.and(WEBHOOK_DELIVERY.MERCHANT_SEQ.eq(dsl.merchantSeq(merchantId)))
			.fetchOne()
			?.toDomain(merchantId)

	/**
	 * `merchantId`를 인자로 받지 않으므로 행에서 **거꾸로 얻는다** — 화면이 넘기는 것은
	 * 전송 식별자 하나뿐이다(`RedeliverWebhookUseCase`).
	 */
	override fun findById(webhookDeliveryId: WebhookDeliveryId): WebhookDelivery? =
		dsl
			.selectFrom(WEBHOOK_DELIVERY)
			.where(WEBHOOK_DELIVERY.WEBHOOK_DELIVERY_ID.eq(webhookDeliveryId.value))
			.fetchOne()
			?.let { it.toDomain(dsl.merchantId(it.merchantSeq!!)) }

	private fun WebhookDeliveryRecord.fillFrom(webhookDelivery: WebhookDelivery) {
		webhookDeliveryId = webhookDelivery.id.value
		merchantSeq = dsl.merchantSeq(webhookDelivery.merchantId)
		eventId = webhookDelivery.eventId.value
		eventType = webhookDelivery.eventType
		aggregateType = webhookDelivery.aggregateType
		aggregateId = webhookDelivery.aggregateId
		destinationUrl = webhookDelivery.destinationUrl.value
		payload = JSON.valueOf(webhookDelivery.payload)
		deliveryStatus = webhookDelivery.status.name
		attemptCount = webhookDelivery.attemptCount
		lastHttpStatus = webhookDelivery.lastHttpStatus
		lastErrorMessage = webhookDelivery.lastErrorMessage
		nextRetryAt = webhookDelivery.nextRetryAt?.toUtcLocalDateTime()
		deliveredAt = webhookDelivery.deliveredAt?.toUtcLocalDateTime()
		createdAt = webhookDelivery.createdAt.toUtcLocalDateTime()
		updatedAt = webhookDelivery.updatedAt.toUtcLocalDateTime()
	}

	private fun WebhookDeliveryRecord.toDomain(merchantId: MerchantId): WebhookDelivery =
		WebhookDelivery.reconstitute(
			id = WebhookDeliveryId(webhookDeliveryId!!),
			merchantId = merchantId,
			eventId = EventId(eventId!!),
			eventType = eventType!!,
			aggregateType = aggregateType!!,
			aggregateId = aggregateId!!,
			destinationUrl = HttpUrl(destinationUrl!!),
			payload = payload!!.data(),
			createdAt = createdAt!!.toUtcInstant(),
			status = WebhookDeliveryStatus.valueOf(deliveryStatus!!),
			attemptCount = attemptCount!!,
			lastHttpStatus = lastHttpStatus,
			lastErrorMessage = lastErrorMessage,
			nextRetryAt = nextRetryAt?.toUtcInstant(),
			deliveredAt = deliveredAt?.toUtcInstant(),
			updatedAt = updatedAt!!.toUtcInstant(),
		)
}
