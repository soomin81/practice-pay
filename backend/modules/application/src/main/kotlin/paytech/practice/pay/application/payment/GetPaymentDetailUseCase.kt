package paytech.practice.pay.application.payment

import paytech.practice.pay.application.port.outbound.PaymentDetailProjection
import paytech.practice.pay.application.port.outbound.PaymentDetailView
import paytech.practice.pay.domain.payment.PaymentId

/**
 * 내부 운영자 콘솔이 결제 **한 건의 전체 맥락**을 조회하는 Use Case다
 * (`GET /admin/payments/{paymentId}`).
 *
 * 조회는 인증된 내부 사용자 전원(`VIEWER` 포함)에게 열려 있어 요청자 검사가 없다
 * (`GET /admin/payments`와 같은 스코핑).
 *
 * **가맹점 콘솔용은 아직 없다** — 만들 때는 목록과 같은 규율로 `merchantId`를 필수 인자로
 * 받는 별도 Use Case를 두고, **조회한 결제가 그 가맹점 것인지 확인해야 한다.** 단건 조회는
 * 목록과 달리 "필터가 비면 전체가 나온다"가 아니라 "남의 것을 ID로 찍어 볼 수 있다"는
 * 형태로 새기 때문에 더 위험하다.
 */
class GetPaymentDetailUseCase(
	private val paymentDetailProjection: PaymentDetailProjection,
) {
	/** 없으면 `null` — 호출부가 404로 옮긴다. */
	fun execute(paymentId: PaymentId): PaymentDetailView? = paymentDetailProjection.findByPaymentId(paymentId)
}
