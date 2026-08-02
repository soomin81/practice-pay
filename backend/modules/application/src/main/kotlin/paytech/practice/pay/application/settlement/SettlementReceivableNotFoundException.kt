package paytech.practice.pay.application.settlement

import paytech.practice.pay.domain.settlement.SettlementReceivableId

/** 없는 정산 채권을 지목했을 때다 — 호출부가 `404`로 옮긴다. */
class SettlementReceivableNotFoundException(
	val settlementReceivableId: SettlementReceivableId,
) : RuntimeException("SettlementReceivable을 찾을 수 없습니다: ${settlementReceivableId.value}")
