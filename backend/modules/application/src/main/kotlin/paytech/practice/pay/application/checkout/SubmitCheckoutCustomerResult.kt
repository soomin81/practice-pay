package paytech.practice.pay.application.checkout

import paytech.practice.pay.domain.checkout.CheckoutSessionId
import paytech.practice.pay.domain.checkout.CheckoutSessionStatus

/**
 * [SubmitCheckoutCustomerUseCase]의 결과다.
 *
 * **마스킹된 값만 돌려준다.** 방금 입력한 고객 본인에게 되돌려 주는 것이라 평문을 실어도
 * 새로 새는 정보는 없지만, "이 시스템의 응답에는 구매자 원본이 실리지 않는다"를 예외 없는
 * 규칙으로 두는 편이 낫다 — 예외가 하나 있으면 다음 응답도 그 예외를 근거로 삼는다.
 * 고객에게는 입력이 제대로 접수됐음을 확인시키는 용도로 충분하다.
 */
data class SubmitCheckoutCustomerResult(
	val checkoutSessionId: CheckoutSessionId,
	val checkoutSessionStatus: CheckoutSessionStatus,
	val maskedName: String,
	val maskedEmail: String,
	val maskedPhone: String,
)
