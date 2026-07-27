package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.identity.InternalLoginAudit

/**
 * 내부 운영자 로그인 감사 기록을 남기는 Command Repository Outbound Port다.
 *
 * append-only라 `save`/`findBy...`가 아니라 [append]만 둔다 — 감사 기록은 한 번 남기면
 * 바뀌지 않는다. 조회는 전용 [InternalLoginAuditProjection]이 맡는다(Command/Query 분리).
 */
fun interface InternalLoginAuditRepository {
	/** 로그인 시도 감사 기록을 추가한다. */
	fun append(audit: InternalLoginAudit)
}
