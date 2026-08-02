package paytech.practice.pay.application.settlement

import paytech.practice.pay.application.port.outbound.SettlementReceivableListEntry

/**
 * 정산 채권 조회 결과다. 두 콘솔이 같은 읽기 모델을 쓴다.
 *
 * @property totalCount 필터 전체에 걸린 건수(현재 페이지 건수가 아니다). **상태를 가리지
 * 않는다** — 목록에 몇 줄이 나오는지와 같은 수다.
 * @property totalNetAmount 필터 전체의 정산 예정 금액 합계. **이 화면의 핵심 숫자다** —
 * 가맹점이 묻는 질문이 "그래서 얼마를 받나"이고, 현재 페이지의 합으로는 답할 수 없다.
 * **지급 경로에 살아 있는 것만 더한다**(`PENDING`/`READY`) — 막아 두거나 끝낸 돈까지 더하면
 * 실제로 나갈 금액보다 큰 답을 하게 된다.
 * @property heldCount / @property heldNetAmount 그렇게 빠진 돈. 합계에서 빼기만 하면 숫자가
 * 달라진 이유를 찾을 수 없어서 함께 준다.
 * @property size 실제로 적용된 페이지 크기(상한에 걸리면 요청값과 다르다).
 */
data class ListSettlementReceivablesResult(
	val entries: List<SettlementReceivableListEntry>,
	val totalCount: Long,
	val totalNetAmount: Long,
	val heldCount: Long,
	val heldNetAmount: Long,
	val page: Int,
	val size: Int,
)
