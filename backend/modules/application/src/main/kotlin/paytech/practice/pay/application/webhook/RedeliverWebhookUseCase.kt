package paytech.practice.pay.application.webhook

import paytech.practice.pay.application.port.outbound.OutboxEventRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.application.port.outbound.WebhookDeliveryRepository
import paytech.practice.pay.domain.webhook.WebhookDeliveryId
import paytech.practice.pay.domain.webhook.WebhookDeliveryStatus
import java.time.Clock

/**
 * 내부 운영자가 **실패한 Webhook 전송을 다시 보내게** 하는 Use Case다
 * (`POST /admin/webhook-deliveries/{webhookDeliveryId}/redeliver`).
 *
 * ## 보내지 않고 "되돌려 놓기만" 한다
 *
 * 이 Use Case는 HTTP를 직접 호출하지 않는다. 두 애그리게이트를 `FAILED → PENDING`으로
 * 되돌려 놓으면 **기존 발행 Worker**(`apps:batch`의 `publishOutboxEventJob`, 10초 주기)가
 * 평소와 똑같은 경로로 집어 간다.
 *
 * 그렇게 한 이유:
 * - 재전송 전용 전송 경로를 따로 만들면 **서명·재시도·상태 갱신이 두 벌**이 되고, 둘이
 *   어긋나는 순간 "화면에서 보낸 것과 자동으로 나간 것이 다르다"는 진단하기 어려운
 *   상황이 된다.
 * - `api-admin`은 웹훅을 보내는 앱이 아니다 — `WebhookSender` 구현을 이 앱에 끌어오면
 *   전송 책임이 두 배포 단위로 흩어진다.
 *
 * 그래서 화면은 "보냈다"가 아니라 **"다시 보내도록 예약했다"**고 알려야 한다.
 *
 * ## 왜 종료 상태를 되돌리나
 *
 * 이 시스템의 공통 규칙은 "종료 상태는 재사용하지 않는다"이고, 이것이 **유일한 예외**다.
 * `uk_webhook_event_merchant` 때문에 같은 이벤트로 새 전송 행을 만들 수 없고, 그 제약은
 * 자동 재시도의 멱등성을 받치는 장치라 뗄 수 없다 — 근거 전체는
 * `docs/domain/state-transitions.md`의 "수동 재전송" 절에 있다.
 *
 * ## 범위
 *
 * **요청자 검사가 없다** — `SecurityConfig`가 이 경로를 `SUPER_ADMIN`/`OPERATOR`로 좁힌다
 * (`AdminChangeMerchantUserStatusUseCase`와 같은 판단). 가맹점 콘솔에는 이 기능이 없다:
 * 장애 대응은 PG가 하고, 가맹점에게 열면 자기 서버로 요청을 반복시킬 여지가 생긴다.
 */
class RedeliverWebhookUseCase(
	private val webhookDeliveryRepository: WebhookDeliveryRepository,
	private val outboxEventRepository: OutboxEventRepository,
	private val transactionManager: TransactionManager,
	private val clock: Clock,
) {
	fun execute(webhookDeliveryId: WebhookDeliveryId): RedeliverWebhookResult {
		val delivery =
			webhookDeliveryRepository.findById(webhookDeliveryId)
				?: throw WebhookDeliveryNotFoundException(webhookDeliveryId)

		// 되돌릴 수 있는 것은 FAILED뿐이다. 이미 성공했거나 아직 진행 중인 전송을 건드리면
		// 중복 발송이거나 진행 중인 시도를 망가뜨리는 것이라, 상태를 먼저 확인해 알려준다.
		if (delivery.status != WebhookDeliveryStatus.FAILED) {
			throw WebhookDeliveryNotRedeliverableException(webhookDeliveryId, delivery.status)
		}

		val outboxEvent =
			outboxEventRepository.findById(delivery.eventId)
				?: error("WebhookDelivery(${webhookDeliveryId.value})의 OutboxEvent(${delivery.eventId.value})를 찾을 수 없습니다.")

		val now = clock.instant()
		delivery.redeliver(now)
		outboxEvent.reopenForRedelivery(now)

		// **둘은 반드시 함께 되돌아가야 한다.** 전송만 PENDING이 되고 이벤트가 FAILED로
		// 남으면 Worker가 집지 않아 영영 대기 상태로 멈추고, 반대면 전송 행이 없는 채로
		// 발행이 돌아 새 전송을 만들려다 UNIQUE 제약에 걸린다.
		return transactionManager.runInTransaction {
			webhookDeliveryRepository.save(delivery)
			outboxEventRepository.save(outboxEvent)
			RedeliverWebhookResult(
				webhookDeliveryId = delivery.id,
				status = delivery.status,
				attemptCount = delivery.attemptCount,
			)
		}
	}
}

/**
 * 재전송 예약 결과다.
 *
 * @property status 되돌린 뒤의 상태(`PENDING`). 화면이 "예약됨"을 그리는 근거다.
 * @property attemptCount **초기화하지 않은 누적 시도 횟수** — 재전송이 자동 재시도 예산을
 * 새로 주는 것이 아니라는 사실이 이 값으로 드러난다.
 */
data class RedeliverWebhookResult(
	val webhookDeliveryId: WebhookDeliveryId,
	val status: WebhookDeliveryStatus,
	val attemptCount: Int,
)
