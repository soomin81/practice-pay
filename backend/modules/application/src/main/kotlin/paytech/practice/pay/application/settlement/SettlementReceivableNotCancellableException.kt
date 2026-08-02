package paytech.practice.pay.application.settlement

import paytech.practice.pay.domain.settlement.SettlementReceivableId
import paytech.practice.pay.domain.settlement.SettlementReceivableStatus

/**
 * 이미 취소된 정산 채권을 다시 취소하려 할 때다 — 호출부가 `409`로 옮긴다.
 *
 * `CANCELLED`는 종료 상태이고 이 시스템은 종료 상태를 재사용하지 않는다(유일한 예외는
 * Webhook 재전송 — `docs/domain/state-transitions.md`).
 */
class SettlementReceivableNotCancellableException(
	val settlementReceivableId: SettlementReceivableId,
	val status: SettlementReceivableStatus,
) : RuntimeException("이미 취소된 정산 채권입니다. 현재 상태: $status")
