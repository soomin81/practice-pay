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
	;

	/**
	 * **가맹점에게 지급될 경로에 아직 살아 있는가.**
	 *
	 * 화면의 정산 예정 금액 합계와 엑셀의 "정산 예정 금액" 열이 **같은 기준으로 금액을
	 * 고르는 근거**다. `HELD`는 지급을 막아 둔 돈이고 `CANCELLED`는 정산하지 않기로 끝낸
	 * 돈이라, 더하면 실제로 나갈 금액보다 큰 답이 된다(ADR-007).
	 *
	 * 두 곳이 각자 상태 목록을 들고 있으면 한쪽만 바뀌었을 때 **화면과 파일이 다른 답을
	 * 한다** — 그래서 판단을 상태 자신에게 둔다.
	 *
	 * `ASSIGNED`/`SETTLED`는 아직 전이 경로가 없지만(ADR-005) 생긴다면 각각 "지급 예정"과
	 * "지급 완료"다 — 여기서는 제외한다. 전자는 이미 가맹점 단위 집계로 넘어갔고 후자는
	 * 이미 나갔으므로, 둘 다 "앞으로 나갈 돈"에 다시 세면 중복이다.
	 */
	val isOnPayoutPath: Boolean
		get() = this == PENDING || this == READY
}
