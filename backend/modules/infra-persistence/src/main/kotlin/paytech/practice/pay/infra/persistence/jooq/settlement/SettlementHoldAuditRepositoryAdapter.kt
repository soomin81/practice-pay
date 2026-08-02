package paytech.practice.pay.infra.persistence.jooq.settlement

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import paytech.practice.pay.application.port.outbound.SettlementHoldAuditRepository
import paytech.practice.pay.dbcore.jooq.tables.SettlementHoldAudit.Companion.SETTLEMENT_HOLD_AUDIT
import paytech.practice.pay.domain.settlement.SettlementHoldAudit
import paytech.practice.pay.infra.persistence.jooq.internalUserSeq
import paytech.practice.pay.infra.persistence.jooq.settlementReceivableSeq
import paytech.practice.pay.infra.persistence.jooq.toUtcLocalDateTime

/**
 * jOOQ로 [SettlementHoldAuditRepository] Port를 구현한다.
 *
 * append-only라 INSERT만 있다(`InternalLoginAuditRepositoryAdapter`와 같은 모양) —
 * `created_at`은 별도 의미가 없어 `occurred_at`과 같은 값으로 채운다.
 */
@Repository
class SettlementHoldAuditRepositoryAdapter(
	private val dsl: DSLContext,
) : SettlementHoldAuditRepository {
	override fun append(audit: SettlementHoldAudit) {
		dsl
			.newRecord(SETTLEMENT_HOLD_AUDIT)
			.apply {
				settlementHoldAuditId = audit.id.value
				settlementReceivableSeq = dsl.settlementReceivableSeq(audit.settlementReceivableId)
				internalUserSeq = dsl.internalUserSeq(audit.internalUserId)
				holdAction = audit.action.name
				reasonCode = audit.reasonCode
				note = audit.note
				occurredAt = audit.occurredAt.toUtcLocalDateTime()
				createdAt = audit.occurredAt.toUtcLocalDateTime()
			}.insert()
	}
}
