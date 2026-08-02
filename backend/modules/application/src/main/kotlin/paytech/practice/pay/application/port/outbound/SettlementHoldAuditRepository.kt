package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.settlement.SettlementHoldAudit

/**
 * 정산 채권의 보류·해제·취소 이력을 남기는 Command Repository Outbound Port다.
 *
 * append-only라 `save`/`findBy...`가 아니라 [append]만 둔다 — 감사 기록은 한 번 남기면
 * 바뀌지 않는다(`InternalLoginAuditRepository`와 같은 모양·같은 이유). 조회는 전용
 * [SettlementHoldAuditProjection]이 맡는다(Command/Query 분리).
 */
fun interface SettlementHoldAuditRepository {
	/**
	 * 이력을 추가한다.
	 *
	 * **채권 상태를 바꾸는 저장과 반드시 같은 트랜잭션 안에서 부른다** — 상태만 바뀌고
	 * 이력이 빠지면 "누가 풀었나"에 영영 답할 수 없고, 그건 이 테이블을 둔 이유가 사라지는
	 * 것이다.
	 */
	fun append(audit: SettlementHoldAudit)
}
