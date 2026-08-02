package paytech.practice.pay.infra.persistence.jooq.settlement

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import paytech.practice.pay.application.port.outbound.SettlementHoldAuditEntry
import paytech.practice.pay.application.port.outbound.SettlementHoldAuditProjection
import paytech.practice.pay.dbcore.jooq.tables.InternalUser.Companion.INTERNAL_USER
import paytech.practice.pay.dbcore.jooq.tables.SettlementHoldAudit.Companion.SETTLEMENT_HOLD_AUDIT
import paytech.practice.pay.dbcore.jooq.tables.SettlementReceivable.Companion.SETTLEMENT_RECEIVABLE
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.settlement.SettlementHoldAction
import paytech.practice.pay.domain.settlement.SettlementHoldAuditId
import paytech.practice.pay.domain.settlement.SettlementReceivableId
import paytech.practice.pay.infra.persistence.jooq.toUtcInstant

/**
 * jOOQ로 [SettlementHoldAuditProjection] Port를 구현한다.
 *
 * `internal_user`를 **INNER JOIN**한다 — 로그인 감사와 달리 실행 주체가 `NOT NULL`이라
 * 매칭되지 않는 행이 존재할 수 없다. 매칭이 안 되면 그건 "알 수 없는 계정"이 아니라 데이터
 * 정합성이 깨진 것이고, 그때는 조용히 빠지는 편보다 드러나는 편이 낫다.
 */
@Repository
class SettlementHoldAuditProjectionAdapter(
	private val dsl: DSLContext,
) : SettlementHoldAuditProjection {
	override fun findByReceivableId(settlementReceivableId: SettlementReceivableId): List<SettlementHoldAuditEntry> =
		dsl
			.select(
				SETTLEMENT_HOLD_AUDIT.SETTLEMENT_HOLD_AUDIT_ID,
				INTERNAL_USER.INTERNAL_USER_ID,
				INTERNAL_USER.USER_NAME,
				SETTLEMENT_HOLD_AUDIT.HOLD_ACTION,
				SETTLEMENT_HOLD_AUDIT.REASON_CODE,
				SETTLEMENT_HOLD_AUDIT.NOTE,
				SETTLEMENT_HOLD_AUDIT.OCCURRED_AT,
			).from(SETTLEMENT_HOLD_AUDIT)
			.join(INTERNAL_USER)
			.on(SETTLEMENT_HOLD_AUDIT.INTERNAL_USER_SEQ.eq(INTERNAL_USER.INTERNAL_USER_SEQ))
			.join(SETTLEMENT_RECEIVABLE)
			.on(SETTLEMENT_HOLD_AUDIT.SETTLEMENT_RECEIVABLE_SEQ.eq(SETTLEMENT_RECEIVABLE.SETTLEMENT_RECEIVABLE_SEQ))
			.where(SETTLEMENT_RECEIVABLE.SETTLEMENT_RECEIVABLE_ID.eq(settlementReceivableId.value))
			.orderBy(SETTLEMENT_HOLD_AUDIT.OCCURRED_AT.desc(), SETTLEMENT_HOLD_AUDIT.SETTLEMENT_HOLD_AUDIT_SEQ.desc())
			.fetch { record ->
				SettlementHoldAuditEntry(
					auditId = SettlementHoldAuditId(record.get(SETTLEMENT_HOLD_AUDIT.SETTLEMENT_HOLD_AUDIT_ID)!!),
					internalUserId = InternalUserId(record.get(INTERNAL_USER.INTERNAL_USER_ID)!!),
					internalUserName = record.get(INTERNAL_USER.USER_NAME)!!,
					action = SettlementHoldAction.valueOf(record.get(SETTLEMENT_HOLD_AUDIT.HOLD_ACTION)!!),
					reasonCode = record.get(SETTLEMENT_HOLD_AUDIT.REASON_CODE),
					note = record.get(SETTLEMENT_HOLD_AUDIT.NOTE),
					occurredAt = record.get(SETTLEMENT_HOLD_AUDIT.OCCURRED_AT)!!.toUtcInstant(),
				)
			}
}
