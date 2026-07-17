package paytech.practice.pay.domain.checkout

/**
 * [CheckoutSession]의 상태를 표현한다.
 *
 * 정상 흐름: `CREATED → OPEN → WALLET_CONNECTED → PAYMENT_SUBMITTED → COMPLETED`
 *
 * 예외 흐름: `PAYMENT_SUBMITTED` 이전(`CREATED`/`OPEN`/`WALLET_CONNECTED`)에서만
 * `CANCELLED`(고객 취소) 또는 `EXPIRED`(세션 만료)로 전이할 수 있다.
 * `PAYMENT_SUBMITTED` 이후에는 고객 취소를 허용하지 않는다(`docs/domain/state-transitions.md`).
 *
 * `COMPLETED`, `EXPIRED`, `CANCELLED`는 종료 상태이며 재사용하지 않는다.
 *
 * @see docs/domain/state-transitions.md
 */
enum class CheckoutSessionStatus {
	CREATED,
	OPEN,
	WALLET_CONNECTED,
	PAYMENT_SUBMITTED,
	COMPLETED,
	EXPIRED,
	CANCELLED,
}
