package paytech.practice.pay.application.checkout

import paytech.practice.pay.domain.checkout.CheckoutSessionId
import paytech.practice.pay.domain.checkout.CheckoutSessionStatus
import paytech.practice.pay.domain.shared.HttpUrl

/**
 * 취소 결과다. [cancelUrl]은 가맹점이 지정하지 않았으면 `null`이고, 그때 프론트는
 * 돌아갈 곳 없이 취소 화면에 머문다.
 */
data class CancelCheckoutSessionResult(
	val checkoutSessionId: CheckoutSessionId,
	val checkoutSessionStatus: CheckoutSessionStatus,
	val cancelUrl: HttpUrl?,
)
