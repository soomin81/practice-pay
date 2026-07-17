package paytech.practice.pay.domain.webhook

/**
 * [WebhookDelivery]의 상태를 표현한다.
 *
 * 정상 흐름: `PENDING → DELIVERING → SUCCEEDED`
 *
 * 실패 시 `RETRY_WAITING`을 거쳐 재전송하고, 최대 횟수를 초과하면 `FAILED`로
 * 처리한다(`docs/domain/state-transitions.md`). "최대 횟수"의 구체적인 값은
 * 문서에 없다 — 이 Aggregate는 시도 횟수([WebhookDelivery.attemptCount])만 셀
 * 뿐, 재시도할지 최종 실패 처리할지의 판단은 호출부(애플리케이션 서비스)의
 * 책임이다.
 *
 * `SUCCEEDED`, `FAILED`는 종료 상태다.
 *
 * @see docs/domain/state-transitions.md
 */
enum class WebhookDeliveryStatus {
	PENDING,
	DELIVERING,
	SUCCEEDED,
	RETRY_WAITING,
	FAILED,
}
