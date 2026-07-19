package paytech.practice.pay.application.checkout

import paytech.practice.pay.application.port.outbound.CheckoutSessionView
import paytech.practice.pay.application.port.outbound.CheckoutViewProjection
import paytech.practice.pay.domain.checkout.CheckoutSessionId

/**
 * 체크아웃 화면 렌더에 필요한 정보를 한 번에 조회하는 Use Case다
 * (`docs/architecture/checkout-api.md`의 4.1).
 *
 * **상태를 바꾸지 않는다.** `CheckoutSession.open()`(`CREATED → OPEN`)을 여기서
 * 호출하지 않는 이유는 [ConnectCheckoutWalletUseCase]의 KDoc에 있는 판단과 같다 —
 * 조회는 `GET`으로 남기고, 고객이 실제로 처음 행동하는 순간(지갑 연결)에 `open()`을
 * 함께 처리한다.
 *
 * **만료·취소·완료된 세션도 그대로 돌려준다.** 프론트가 만료 화면·완료 화면을
 * 그려야 하기 때문이다 — 상태로 막는 것은 변경 엔드포인트의 몫이다.
 *
 * 입력이 식별자 하나뿐이라 별도 `Command` 클래스를 두지 않는다 — 의미 없는 래퍼를
 * 만들지 않는다는 점에서 `ListMerchantsUseCase`가 빈 `Command`를 만들지 않은 것과
 * 같은 판단이다. 상태를 바꾸는 Use Case(`ConnectCheckoutWalletUseCase`,
 * [CancelCheckoutSessionUseCase])는 기존대로 `Command`를 받는다.
 */
class GetCheckoutSessionUseCase(
	private val checkoutViewProjection: CheckoutViewProjection,
) {
	fun execute(checkoutSessionId: CheckoutSessionId): CheckoutSessionView =
		checkoutViewProjection.findSessionView(checkoutSessionId)
			?: throw CheckoutSessionNotFoundException(checkoutSessionId)
}
