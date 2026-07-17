package paytech.practice.pay.domain.settlement

/**
 * [SettlementReceivable]의 상태를 표현한다.
 *
 * MVP 흐름: `PENDING → READY`. `READY`가 MVP의 최종 상태다(`docs/domain/glossary.md`,
 * ADR-005).
 *
 * `ASSIGNED`/`SETTLED`는 향후 가맹점 단위 집계 정산(`Settlement`, 아직 없는
 * Aggregate)이 생겨야 의미가 있는 상태로, DB 스키마 CHECK 제약이 이미 값을
 * 나열해 두고 있어 enum에는 포함하지만 이 Aggregate에는 그 상태로 가는 전이
 * 메서드가 없다(ADR-005).
 *
 * `HELD`/`CANCELLED`는 MVP 스키마에 이미 있는 `hold_reason_code` 컬럼과 함께
 * 지금 바로 지원한다 — `PENDING`/`READY`/`HELD`(취소의 경우) 어디서든 전이할 수
 * 있다고 해석했다(`docs/domain/state-transitions.md`가 명시하지 않아 스키마 기반
 * 추론이다).
 *
 * @see docs/domain/state-transitions.md
 */
enum class SettlementReceivableStatus {
	PENDING,
	READY,
	ASSIGNED,
	SETTLED,
	HELD,
	CANCELLED,
}
