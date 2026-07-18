package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.checkout.CheckoutSession
import paytech.practice.pay.domain.checkout.CheckoutSessionId
import paytech.practice.pay.domain.payment.PaymentId

/**
 * [CheckoutSession] Aggregate를 저장·복원하는 Command Repository Outbound Port다.
 */
interface CheckoutSessionRepository {
	/** CheckoutSession을 저장한다(신규 생성·상태 변경 모두 이 메서드로 반영한다). */
	fun save(checkoutSession: CheckoutSession)

	/** `checkout_session_id`로 CheckoutSession을 찾는다. 없으면 `null`이다. */
	fun findById(checkoutSessionId: CheckoutSessionId): CheckoutSession?

	/**
	 * `payment_seq`로 CheckoutSession을 찾는다. `Payment`와 1:1 관계라 최대 하나만 있다.
	 *
	 * 결제 생성이 멱등하게 재요청됐을 때(`PaymentRepository.findByMerchantOrderId`
	 * 참고), 기존 Payment에 딸린 CheckoutSession을 함께 돌려주기 위해 쓴다.
	 */
	fun findByPaymentId(paymentId: PaymentId): CheckoutSession?
}
