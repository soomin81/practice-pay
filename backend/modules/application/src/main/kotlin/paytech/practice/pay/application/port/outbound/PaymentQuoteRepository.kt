package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.quote.PaymentQuote

/**
 * [PaymentQuote] 스냅샷을 저장하는 Command Repository Outbound Port다.
 *
 * `PaymentQuote`는 한 번 저장되면 값이 바뀌지 않는 불변 스냅샷이라(`PaymentQuote`의
 * KDoc 참고) `save`만 있으면 충분하다 — 갱신도, 상태 전이도 없다.
 */
interface PaymentQuoteRepository {
	/** Payment 생성 시점의 견적 스냅샷을 저장한다. */
	fun save(quote: PaymentQuote)
}
