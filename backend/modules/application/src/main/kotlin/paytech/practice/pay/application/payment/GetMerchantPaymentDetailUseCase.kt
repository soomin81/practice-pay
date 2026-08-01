package paytech.practice.pay.application.payment

import paytech.practice.pay.application.port.outbound.PaymentDetailProjection
import paytech.practice.pay.application.port.outbound.PaymentDetailView
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.PaymentId

/**
 * 가맹점 콘솔이 **자기 가맹점의** 결제 한 건을 조회하는 Use Case다
 * (`GET /merchant/payments/{paymentId}`).
 *
 * **단건 조회는 목록보다 위험하다.** 목록은 "필터가 비면 전체가 나온다"는 형태로 새지만,
 * 단건은 **"남의 것을 ID로 찍어 볼 수 있다"**는 형태로 샌다 — 범위를 좁히는 필터가 아예
 * 없으므로, 조회한 뒤 **그 결제가 요청한 가맹점 것인지 직접 확인해야 한다.**
 *
 * **남의 결제는 "없음"으로 취급한다**(`null`) — `403`으로 돌려주면 "그 결제는 존재한다"는
 * 사실이 새어 나가고, 그것만으로 결제 식별자를 훑어 다른 가맹점의 거래량을 추정할 수 있다.
 * `RevokeMerchantApiKeyUseCase`가 다른 가맹점 Key를 404로 가리는 것과 같은 판단이다.
 */
class GetMerchantPaymentDetailUseCase(
	private val paymentDetailProjection: PaymentDetailProjection,
) {
	/** 없거나 **다른 가맹점의 결제면** `null` — 호출부가 404로 옮긴다. */
	fun execute(
		merchantId: MerchantId,
		paymentId: PaymentId,
	): PaymentDetailView? = paymentDetailProjection.findByPaymentId(paymentId)?.takeIf { it.payment.merchantId == merchantId }
}
