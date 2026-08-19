package paytech.practice.pay.application.checkout

import paytech.practice.pay.domain.checkout.CheckoutSessionId
import paytech.practice.pay.domain.checkout.CheckoutSessionStatus

/**
 * 구매자 정보를 더 이상 입력·수정할 수 없는 상태에서 시도했을 때 던진다 — HTTP `409`로
 * 매핑된다.
 *
 * **경계는 `PAYMENT_SUBMITTED`다**(취소와 같은 자리 — `CheckoutSessionNotCancellableException`).
 * 전송이 브로드캐스트된 뒤에 연락처가 바뀌면, 그 결제에 문제가 생겼을 때 연락할 상대가
 * 소리 없이 달라진다. 고쳐야 할 일이 실제로 있다면 그건 고객이 아니라 내부 운영자가
 * 흔적을 남기며 할 일이고, 그 경로는 아직 없다(ADR-008의 "남긴 것").
 *
 * `CheckoutSession`에는 구매자 정보 입력에 해당하는 상태 전이가 없어서 도메인이 막아 주지
 * 않는다 — 이 확인이 유일한 방어선이다.
 */
class CheckoutCustomerNotEditableException(
	checkoutSessionId: CheckoutSessionId,
	status: CheckoutSessionStatus,
) : RuntimeException("CheckoutSession(${checkoutSessionId.value})에는 더 이상 구매자 정보를 입력할 수 없습니다(status=$status).")
