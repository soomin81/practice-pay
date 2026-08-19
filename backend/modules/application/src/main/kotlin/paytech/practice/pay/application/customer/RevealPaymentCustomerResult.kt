package paytech.practice.pay.application.customer

import paytech.practice.pay.domain.customer.CustomerEmail
import paytech.practice.pay.domain.customer.CustomerName
import paytech.practice.pay.domain.customer.CustomerPhone
import paytech.practice.pay.domain.payment.PaymentId
import java.time.Instant

/**
 * [RevealPaymentCustomerUseCase]의 결과 — **이 저장소에서 구매자 원문을 담아 나가는 유일한
 * 타입**이다.
 *
 * 다른 모든 응답 경로는 마스킹된 값을 쓴다. 이 타입을 쓰는 곳이 늘어나면 그만큼 원문이
 * 로그·응답·파일로 샐 자리가 늘어나므로, **호출부를 하나로 유지한다**(ADR-008의 6).
 *
 * @property revealedAt 열람한 시각. 감사 기록에 남은 것과 **같은 값**이다 — 화면이 "언제
 * 봤는지"를 따로 계산하지 않게 한다.
 */
data class RevealPaymentCustomerResult(
	val paymentId: PaymentId,
	val name: CustomerName,
	val email: CustomerEmail,
	val phone: CustomerPhone,
	val revealedAt: Instant,
)
