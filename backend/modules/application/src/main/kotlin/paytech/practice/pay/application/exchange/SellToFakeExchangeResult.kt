package paytech.practice.pay.application.exchange

import paytech.practice.pay.domain.exchange.ExchangeOrderId
import paytech.practice.pay.domain.exchange.ExchangeOrderStatus
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.settlement.SettlementReceivableId
import paytech.practice.pay.domain.settlement.SettlementReceivableStatus

/** [SellToFakeExchangeUseCase]의 결과다. 이번 시도 이후의 최종 상태를 돌려준다. */
data class SellToFakeExchangeResult(
	val paymentId: PaymentId,
	val exchangeOrderId: ExchangeOrderId,
	val exchangeOrderStatus: ExchangeOrderStatus,
	val settlementReceivableId: SettlementReceivableId,
	val settlementReceivableStatus: SettlementReceivableStatus,
)
