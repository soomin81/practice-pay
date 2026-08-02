package paytech.practice.pay.application.settlement

import paytech.practice.pay.domain.settlement.SettlementReceivableId
import paytech.practice.pay.domain.settlement.SettlementReceivableStatus

/**
 * 보류되지 않은 정산 채권을 해제하려 할 때다 — 호출부가 `409`로 옮긴다.
 *
 * **현재 상태를 문구에 담는다**(`TransactionNotReorgeableException`과 같은 판단) — 이미 풀린
 * 것인지 취소된 것인지에 따라 운영자의 다음 행동이 달라진다.
 */
class SettlementReceivableNotReleasableException(
	val settlementReceivableId: SettlementReceivableId,
	val status: SettlementReceivableStatus,
) : RuntimeException("보류된 정산 채권만 해제할 수 있습니다. 현재 상태: $status")
