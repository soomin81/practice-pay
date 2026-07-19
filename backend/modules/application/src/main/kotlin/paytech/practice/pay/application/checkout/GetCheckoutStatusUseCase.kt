package paytech.practice.pay.application.checkout

import paytech.practice.pay.application.port.outbound.CheckoutStatusView
import paytech.practice.pay.application.port.outbound.CheckoutViewProjection
import paytech.practice.pay.domain.checkout.CheckoutSessionId

/**
 * Confirm 대기 구간에서 프론트가 폴링하는 경량 조회 Use Case다
 * (`docs/architecture/checkout-api.md`의 4.2).
 *
 * [GetCheckoutSessionUseCase]와 나눈 이유는 호출 빈도다 — Base의 블록 주기(~2초)에
 * 필요 Confirm 수 12를 곱하면 폴링이 10회 이상 반복되는데, 그때마다 주문·견적
 * 정보까지 다시 실어 보낼 이유가 없다.
 */
class GetCheckoutStatusUseCase(
	private val checkoutViewProjection: CheckoutViewProjection,
) {
	fun execute(checkoutSessionId: CheckoutSessionId): CheckoutStatusView =
		checkoutViewProjection.findStatusView(checkoutSessionId)
			?: throw CheckoutSessionNotFoundException(checkoutSessionId)
}
