package paytech.practice.pay.domain.exchange

/**
 * [ExchangeOrder]의 상태를 표현한다.
 *
 * 운영 확장 흐름: `REQUESTED → SUBMITTED → PROCESSING → COMPLETED`
 *
 * Fake Exchange MVP는 `REQUESTED`에서 바로 `COMPLETED`로 전이한다 —
 * `SUBMITTED`/`PROCESSING`을 거치지 않아도 된다(`docs/domain/state-transitions.md`).
 *
 * `docs/domain/state-transitions.md`는 `FAILED`/`CANCELLED`로의 전이를 별도로
 * 다루지 않지만, DB 스키마의 `exchange_order_status` CHECK 제약이 이미 두 값을
 * 나열해 두고 있어 enum에도 포함한다 — 종료 전 상태(`REQUESTED`/`SUBMITTED`/
 * `PROCESSING`) 어디서든 실패·취소로 빠질 수 있다고 본다.
 *
 * `COMPLETED`, `FAILED`, `CANCELLED`는 종료 상태다.
 *
 * @see docs/domain/state-transitions.md
 */
enum class ExchangeOrderStatus {
	REQUESTED,
	SUBMITTED,
	PROCESSING,
	COMPLETED,
	FAILED,
	CANCELLED,
}
