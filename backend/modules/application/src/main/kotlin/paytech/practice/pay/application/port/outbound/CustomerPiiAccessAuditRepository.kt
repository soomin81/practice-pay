package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.customer.CustomerPiiAccessAudit

/**
 * 구매자 개인정보 원본 열람을 기록하는 Command Repository Outbound Port다.
 *
 * append-only라 `save`/`findBy...`가 아니라 [append]만 둔다 —
 * `SettlementHoldAuditRepository`/`InternalLoginAuditRepository`와 같은 모양·같은 이유다.
 * 조회는 나중에 전용 Projection이 맡는다(Command/Query 분리).
 */
fun interface CustomerPiiAccessAuditRepository {
	/**
	 * 열람 기록을 추가한다.
	 *
	 * **복호화와 반드시 같은 트랜잭션 안에서 부른다** — 기록이 실패하면 열람도 실패해야
	 * 한다. 원본은 이미 화면에 나갔는데 기록만 빠지면 "누가 봤나"에 영영 답할 수 없고,
	 * 그건 이 테이블을 둔 이유가 사라지는 것이다.
	 */
	fun append(audit: CustomerPiiAccessAudit)
}
