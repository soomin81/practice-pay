package paytech.practice.pay.api.admin.web

import jakarta.validation.constraints.NotBlank
import java.time.Instant

/**
 * `POST /admin/payment-customers/{paymentId}/reveal`의 요청 본문이다(계약 4.8).
 *
 * **실행 주체를 본문에서 받지 않는다** — 인증 주체에서 가져온다. 본문에서 받으면 감사
 * 기록이 자기 신고가 된다.
 */
data class RevealPaymentCustomerRequest(
	/** 왜 보는지. Use Case도 공백을 거부하지만, 여기서 먼저 걸러 왕복을 줄인다. */
	@field:NotBlank
	val reason: String,
)

/**
 * 원본 열람 결과 — **이 저장소에서 구매자 원문이 실리는 유일한 응답**이다.
 *
 * 다른 모든 응답은 마스킹된 값을 쓴다. 이 타입을 다른 곳에서 재사용하지 않는다.
 */
data class RevealPaymentCustomerResponse(
	val paymentId: String,
	val name: String,
	val email: String,
	val phone: String,
	/** 감사 기록에 남은 것과 같은 시각이다 — 화면이 따로 계산하지 않게 한다. */
	val revealedAt: Instant,
)
