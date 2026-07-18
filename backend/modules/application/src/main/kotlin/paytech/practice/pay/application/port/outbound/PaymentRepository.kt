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

	/**
	 * 아직 Fake Exchange 매도 처리가 안 된 `SUCCEEDED` Payment를 전부 찾는다 —
	 * 발행 Worker(`apps:batch`)가 폴링 대상 목록을 뽑을 때 쓴다.
	 *
	 * `payment` 레코드에는 정산 상태를 두지 않는다는 규칙(루트 `CLAUDE.md`) 때문에
	 * Payment 테이블만으로는 "이미 매도 처리됐는지"를 판단할 수 없다 — 그래서 이
	 * 조회는 `exchange_order`(Payment 1건당 최대 1건, `uk_exchange_payment`)가
	 * 아직 없는 `SUCCEEDED` Payment를 찾는 크로스 애그리게이트 Join으로 구현된다
	 * (`docs/database/database-design.md`에 이 폴링만을 위한 전용 인덱스가 명시돼
	 * 있지는 않다 — Confirm Worker/Outbox 발행과 달리 알려진 gap).
	 */
	fun findPendingExchangeSettlement(): List<Payment>
}
