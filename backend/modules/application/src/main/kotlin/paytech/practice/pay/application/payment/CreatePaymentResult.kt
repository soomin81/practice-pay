package paytech.practice.pay.application.payment

import paytech.practice.pay.domain.checkout.CheckoutSessionId
import paytech.practice.pay.domain.payment.PaymentId

/**
 * [CreatePaymentUseCase]의 결과다.
 *
 * Command가 예외적으로 값을 반환하는 경우다 — 생성된 리소스의 식별자를 돌려주는
 * 것은 일반적인 CQS 관례상 허용된다(`backend/CLAUDE.md`의 CQS 규칙은 도메인
 * Aggregate 메서드에 적용되는 것이지, Command Handler/Use Case가 생성 결과의 ID를
 * 반환하는 것까지 막지 않는다). 호출부(Inbound Adapter)가 체크아웃 페이지로
 * Redirect시키는 데 [checkoutSessionId]가 필요하다.
 *
 * @property paymentId 생성되거나(멱등 재요청이면) 이미 있던 Payment의 ID.
 * @property checkoutSessionId 함께 생성되거나 이미 있던 CheckoutSession의 ID.
 */
data class CreatePaymentResult(
	val paymentId: PaymentId,
	val checkoutSessionId: CheckoutSessionId,
)
