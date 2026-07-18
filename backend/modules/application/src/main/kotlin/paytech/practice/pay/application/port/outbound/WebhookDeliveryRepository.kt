package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.shared.EventId
import paytech.practice.pay.domain.webhook.WebhookDelivery

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
}
