package paytech.practice.pay.application.customer

import paytech.practice.pay.application.port.outbound.PaymentCustomerSearchEntry

/**
 * [SearchPaymentCustomersUseCase]의 결과다.
 *
 * **마스킹된 값만 담는다** — 검색은 복호화 경로를 타지 않는다(ADR-008). 원문이 필요하면
 * [RevealPaymentCustomerUseCase]를 거쳐야 하고, 그쪽에는 감사 기록이 붙는다.
 *
 * 찾지 못하면 빈 목록이다. "그런 사람이 없다"를 예외로 만들지 않는다 — 검색은 없는 것이
 * 정상적인 결과다.
 */
data class SearchPaymentCustomersResult(
	val matches: List<PaymentCustomerSearchEntry>,
)
