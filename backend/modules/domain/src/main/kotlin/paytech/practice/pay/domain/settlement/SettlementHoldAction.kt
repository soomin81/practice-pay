package paytech.practice.pay.domain.settlement

/**
 * 정산 채권에 사람이 가한 행위다 — [SettlementHoldAudit]가 남기는 값이며
 * `settlement_hold_audit.hold_action` CHECK 제약과 같은 목록이다.
 *
 * **[SettlementReceivableStatus]와 값이 겹쳐 보이지만 다른 축이다.** 상태는 "지금 어디에
 * 있나"이고 이건 "무엇을 했나"다 — 그래서 [RELEASED]에 대응하는 상태가 없다(해제하면
 * `PENDING` 또는 `READY`로 갈라진다). 상태 Enum을 그대로 재사용하면 그 갈라짐을 표현할
 * 수 없어 별도 Enum으로 둔다.
 */
enum class SettlementHoldAction {
	/** 정산을 막았다. 사유 코드가 함께 남는다. */
	HELD,

	/** 보류를 풀었다. 채권은 `PENDING` 또는 `READY`로 돌아간다. */
	RELEASED,

	/** 정산하지 않기로 확정했다. 종료 상태다. */
	CANCELLED,
}
