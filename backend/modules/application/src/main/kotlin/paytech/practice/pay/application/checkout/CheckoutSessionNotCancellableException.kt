package paytech.practice.pay.application.checkout

import paytech.practice.pay.domain.checkout.CheckoutSessionId
import paytech.practice.pay.domain.checkout.CheckoutSessionStatus

/**
 * 이미 취소할 수 없는 상태의 CheckoutSession을 취소하려 할 때 던진다 — HTTP `409`로
 * 매핑된다.
 *
 * `PAYMENT_SUBMITTED` 이후에는 고객이 취소할 수 없다는 규칙
 * (`docs/domain/state-transitions.md`)을 그대로 반영한다. 이미 전송이 브로드캐스트된
 * 뒤라 취소해도 온체인에서 되돌릴 수 없기 때문이다.
 *
 * 도메인의 `CheckoutSession.cancel()`이 던지는 `IllegalStateException`을 그대로
 * 흘려보내지 않고 Use Case가 먼저 확인해 이 예외로 바꾸는 이유는
 * `RevokeMerchantApiKeyUseCase`가 `isUsable()`을 먼저 보는 것과 같다 — 매핑이 없는
 * `IllegalStateException`은 raw 500으로 샌다.
 */
class CheckoutSessionNotCancellableException(
	checkoutSessionId: CheckoutSessionId,
	status: CheckoutSessionStatus,
) : RuntimeException("CheckoutSession(${checkoutSessionId.value})을 취소할 수 없습니다(status=$status).")
