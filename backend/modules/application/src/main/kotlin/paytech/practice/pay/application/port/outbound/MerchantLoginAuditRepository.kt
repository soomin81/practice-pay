package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.identity.MerchantLoginAudit

/**
 * 가맹점 관리자 로그인 감사 기록을 남기는 Command Repository Outbound Port다
 * ([InternalLoginAuditRepository]의 가맹점판). append-only라 [append]만 둔다 — 조회는 전용
 * [MerchantLoginAuditProjection]이 맡는다(Command/Query 분리).
 */
fun interface MerchantLoginAuditRepository {
	/** 로그인 시도 감사 기록을 추가한다. */
	fun append(audit: MerchantLoginAudit)
}
