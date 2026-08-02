package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.shared.EventId
import paytech.practice.pay.domain.webhook.WebhookDelivery
import paytech.practice.pay.domain.webhook.WebhookDeliveryId

/**
 * [WebhookDelivery] Aggregate를 저장·복원하는 Command Repository Outbound Port다.
 */
interface WebhookDeliveryRepository {
	/** WebhookDelivery를 저장한다(신규 생성·상태 변경 모두 이 메서드로 반영한다). */
	fun save(webhookDelivery: WebhookDelivery)

	/**
	 * `(event_id, merchant_seq)` 조합으로 기존 WebhookDelivery를 찾는다.
	 *
	 * 이 조합이 멱등성 키다(`docs/database/database-design.md`의 "주요 Unique" —
	 * `uk_webhook_event_merchant`) — 같은 OutboxEvent를 다시 발행 시도할 때 새로
	 * 만들지 않고 기존 WebhookDelivery를 이어서 쓴다.
	 */
	fun findByEventIdAndMerchantId(
		eventId: EventId,
		merchantId: MerchantId,
	): WebhookDelivery?

	/**
	 * `webhook_delivery_id`로 찾는다. 없으면 `null`이다.
	 *
	 * 자동 발행 경로는 [findByEventIdAndMerchantId]로 충분하다 — 이쪽은 **사람이 화면에서
	 * 특정 전송을 골라 재전송할 때** 쓴다(`RedeliverWebhookUseCase`).
	 */
	fun findById(webhookDeliveryId: WebhookDeliveryId): WebhookDelivery?
}
