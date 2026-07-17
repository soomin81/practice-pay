package paytech.practice.pay.domain.payment

/**
 * [Payment]의 상태를 표현한다.
 *
 * 정상 흐름: `CREATED → READY → PROCESSING → CONFIRMING → SUCCEEDED`
 *
 * 예외 흐름:
 * - `CREATED` 또는 `READY` → `EXPIRED`
 * - `PROCESSING` 또는 `CONFIRMING` → `FAILED`
 *
 * `SUCCEEDED`, `EXPIRED`, `FAILED`는 종료 상태이며 재사용하지 않는다.
 *
 * @see docs/domain/state-transitions.md
 */
enum class PaymentStatus {
	CREATED,
	READY,
	PROCESSING,
	CONFIRMING,
	SUCCEEDED,
	EXPIRED,
	FAILED,
}
