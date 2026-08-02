package paytech.practice.pay.api.admin.web

import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import paytech.practice.pay.application.webhook.RedeliverWebhookUseCase
import paytech.practice.pay.domain.webhook.WebhookDeliveryId

/**
 * 실패한 Webhook 전송을 다시 보내는 API를 노출하는 inbound Adapter다.
 *
 * **보내는 것이 아니라 예약한다** — 이 요청은 전송을 `PENDING`으로 되돌려 놓기만 하고,
 * 실제 발송은 기존 발행 Worker(`apps:batch`, 10초 주기)가 평소 경로로 한다
 * (`RedeliverWebhookUseCase`의 KDoc). 그래서 응답은 "전송됨"이 아니라 되돌려진 상태다.
 *
 * `SecurityConfig`가 `SUPER_ADMIN`/`OPERATOR`로 좁힌다 — `VIEWER`는 조회만 한다.
 * **가맹점 콘솔에는 이 기능이 없다**: 장애 대응은 PG가 하고, 가맹점에게 열면 자기
 * 서버로 요청을 반복시킬 여지가 생긴다.
 */
@RestController
@RequestMapping("/admin/webhook-deliveries")
class WebhookDeliveryController(
	private val redeliverWebhookUseCase: RedeliverWebhookUseCase,
) {
	@PostMapping("/{webhookDeliveryId}/redeliver")
	fun redeliver(
		@PathVariable webhookDeliveryId: String,
	): RedeliverWebhookResponse {
		val result = redeliverWebhookUseCase.execute(WebhookDeliveryId(webhookDeliveryId))

		return RedeliverWebhookResponse(
			webhookDeliveryId = result.webhookDeliveryId.value,
			status = result.status.name,
			attemptCount = result.attemptCount,
		)
	}
}

/**
 * 재전송 예약 결과다.
 *
 * @property status 되돌린 뒤의 상태(`PENDING`). **아직 보내지 않았다는 뜻**이라, 화면은
 * 이 값을 "예약됨"으로 읽어야지 "성공"으로 읽으면 안 된다.
 * @property attemptCount 누적 시도 횟수 — 재전송은 이 값을 초기화하지 않는다(재시도 예산을
 * 새로 주는 것이 아니라 시도 한 번을 뜻한다).
 */
data class RedeliverWebhookResponse(
	val webhookDeliveryId: String,
	val status: String,
	val attemptCount: Int,
)
