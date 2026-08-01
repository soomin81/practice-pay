package paytech.practice.pay.application.checkout

import paytech.practice.pay.domain.checkout.CheckoutSessionId

/**
 * 유효 시간이 지난 CheckoutSession을 변경하려 할 때 던진다 — HTTP `410 Gone`으로
 * 매핑된다(`docs/architecture/checkout-api.md`의 5절).
 *
 * "잘못된 상태"(`409`)와 구분하는 이유는 프론트가 만료 전용 화면을 그릴 수 있게
 * 하려는 것이다. 만료는 요청이 틀린 게 아니라 유효했던 자원의 수명이 끝난 것이다.
 *
 * **`CheckoutSession.status`가 아니라 `expiresAt`으로 판단한다.** 만료 상태로
 * 전이시키는 Sweep Worker(`apps:batch`의 `expireCheckoutsJob`)는 60초 주기라, 그 사이에는
 * 시간이 지났어도 DB의 상태가 `OPEN`인 채로 남아 있다 — 시각을 직접 비교해야 실제 만료를 잡는다.
 */
class CheckoutSessionExpiredException(
	checkoutSessionId: CheckoutSessionId,
) : RuntimeException("CheckoutSession의 유효 시간이 지났습니다: ${checkoutSessionId.value}")
