package paytech.practice.pay.application.webhook

import paytech.practice.pay.domain.webhook.WebhookDeliveryId
import paytech.practice.pay.domain.webhook.WebhookDeliveryStatus

/**
 * 재전송하려는 Webhook 전송이 없을 때다 — 호출부가 `404`로 옮긴다.
 *
 * 메시지에 식별자를 담지 않는다(`PaymentNotFoundException`과 같은 규칙).
 */
class WebhookDeliveryNotFoundException(
	val webhookDeliveryId: WebhookDeliveryId,
) : RuntimeException("Webhook 전송을 찾을 수 없습니다.")

/**
 * `FAILED`가 아닌 전송을 재전송하려 할 때다 — 호출부가 `409`로 옮긴다.
 *
 * **여기서는 현재 상태를 알려준다.** 위의 "없음"과 달리 이건 감출 것이 없고(운영자는 이미
 * 그 전송을 화면에서 보고 있다), "왜 안 되는지"를 모르면 같은 버튼을 계속 누르게 된다.
 */
class WebhookDeliveryNotRedeliverableException(
	val webhookDeliveryId: WebhookDeliveryId,
	val status: WebhookDeliveryStatus,
) : RuntimeException("실패한 전송만 다시 보낼 수 있습니다. 현재 상태: $status")
