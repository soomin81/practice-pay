package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.exchange.ExchangeOrder
import paytech.practice.pay.domain.payment.PaymentId

/**
 * [ExchangeOrder] Aggregate를 저장·복원하는 Command Repository Outbound Port다.
 */
interface ExchangeOrderRepository {
	/** ExchangeOrder를 저장한다(신규 생성·상태 변경 모두 이 메서드로 반영한다). */
	fun save(exchangeOrder: ExchangeOrder)

	/**
	 * `payment_seq`로 기존 ExchangeOrder를 찾는다.
	 *
	 * `uk_exchange_payment`(Payment 1건당 ExchangeOrder 1건) Unique 제약과
	 * 대응한다 — [paytech.practice.pay.application.exchange.SellToFakeExchangeUseCase]가
	 * 이미 처리된 Payment를 재시도했을 때 멱등하게 기존 결과를 돌려주는 데 쓴다.
	 */
	fun findByPaymentId(paymentId: PaymentId): ExchangeOrder?
}
