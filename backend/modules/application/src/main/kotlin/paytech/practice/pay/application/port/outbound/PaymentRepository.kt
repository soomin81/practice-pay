package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.Payment
import paytech.practice.pay.domain.payment.PaymentId

/**
 * [Payment] Aggregate를 저장·복원하는 Command Repository Outbound Port다.
 */
interface PaymentRepository {
	/** Payment를 저장한다(신규 생성·상태 변경 모두 이 메서드로 반영한다). */
	fun save(payment: Payment)

	/** `payment_id`로 Payment를 찾는다. 없으면 `null`이다. */
	fun findById(paymentId: PaymentId): Payment?

	/**
	 * `(merchant_seq, merchant_order_id)` 조합으로 기존 Payment를 찾는다.
	 *
	 * 결제 생성의 멱등성 키다(`backend/CLAUDE.md`의 "Idempotency keys" 참고) — 같은
	 * 가맹점 주문으로 다시 결제 생성을 요청하면 새로 만들지 않고 이 조회 결과를
	 * 재사용한다.
	 */
	fun findByMerchantOrderId(
		merchantId: MerchantId,
		merchantOrderId: MerchantOrderId,
	): Payment?
}
