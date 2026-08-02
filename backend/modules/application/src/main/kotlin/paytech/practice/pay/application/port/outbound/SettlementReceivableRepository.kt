package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.settlement.SettlementReceivable
import paytech.practice.pay.domain.settlement.SettlementReceivableId

/**
 * [SettlementReceivable] Aggregate를 저장·복원하는 Command Repository Outbound Port다.
 */
interface SettlementReceivableRepository {
	/** SettlementReceivable을 저장한다(신규 생성·상태 변경 모두 이 메서드로 반영한다). */
	fun save(settlementReceivable: SettlementReceivable)

	/**
	 * `payment_seq`로 기존 SettlementReceivable을 찾는다.
	 *
	 * `uk_settlement_receivable_payment`(Payment 1건당 SettlementReceivable 1건)
	 * Unique 제약과 대응한다 — 실제 멱등성 키는 `settlement_receivable_id`가 아니라
	 * 이 `payment_seq`다(`SettlementReceivableId`의 KDoc 참고).
	 */
	fun findByPaymentId(paymentId: PaymentId): SettlementReceivable?

	/**
	 * 공개 ID로 기존 SettlementReceivable을 찾는다.
	 *
	 * 내부 운영자가 **화면에서 채권 한 건을 지목해** 보류를 풀거나 취소하는 경로가 쓴다 —
	 * 그쪽은 결제가 아니라 채권을 골라 오므로 [findByPaymentId]로는 닿을 수 없다.
	 */
	fun findById(settlementReceivableId: SettlementReceivableId): SettlementReceivable?
}
