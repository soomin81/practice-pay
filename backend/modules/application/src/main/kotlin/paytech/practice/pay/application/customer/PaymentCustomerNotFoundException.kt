package paytech.practice.pay.application.customer

import paytech.practice.pay.domain.payment.PaymentId

/**
 * 원본을 열람하려는 결제에 구매자 정보가 없을 때 던진다 — HTTP `404`로 매핑된다.
 *
 * **결제 자체가 없는 경우와 구분하지 않는다**(`docs/architecture/admin-console-api.md`의 4.8).
 * 둘을 나눠서 알려주면 "그 결제는 존재한다"가 응답으로 새어 나가고, 개인정보를 다루는
 * 경로에서 존재 여부를 확인해 주는 것은 그 자체가 정보다.
 *
 * 보관 기간이 지나 파기된 경우도 여기로 온다 — 지워진 것과 애초에 없던 것은 같은 응답이다.
 */
class PaymentCustomerNotFoundException(
	paymentId: PaymentId,
) : RuntimeException("결제(${paymentId.value})의 구매자 정보를 찾을 수 없습니다.")
