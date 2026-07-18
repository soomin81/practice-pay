package paytech.practice.pay.application.exchange

import paytech.practice.pay.domain.payment.PaymentId

/**
 * [SellToFakeExchangeUseCase]의 입력이다.
 *
 * 이미 `SUCCEEDED`인 Payment 하나를 대상으로 한 Fake Exchange 매도 시도 한 번이다
 * — `apps:batch`의 폴링 Worker가 대상 목록을 뽑아 하나씩 호출하는 것을 전제로
 * 설계했다.
 */
data class SellToFakeExchangeCommand(
	val paymentId: PaymentId,
)
