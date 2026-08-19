package paytech.practice.pay.infra.persistence.jooq.customer

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import paytech.practice.pay.application.port.outbound.CustomerPiiAccessAuditRepository
import paytech.practice.pay.dbcore.jooq.tables.CustomerPiiAccessAudit.Companion.CUSTOMER_PII_ACCESS_AUDIT
import paytech.practice.pay.domain.customer.CustomerPiiAccessAudit
import paytech.practice.pay.infra.persistence.jooq.internalUserSeq
import paytech.practice.pay.infra.persistence.jooq.paymentSeq
import paytech.practice.pay.infra.persistence.jooq.toUtcLocalDateTime

/**
 * jOOQ로 [CustomerPiiAccessAuditRepository] Port를 구현한다.
 *
 * append-only라 INSERT만 있다(`SettlementHoldAuditRepositoryAdapter`와 같은 모양) —
 * `created_at`은 별도 의미가 없어 `occurred_at`과 같은 값으로 채운다.
 */
@Repository
class CustomerPiiAccessAuditRepositoryAdapter(
	private val dsl: DSLContext,
) : CustomerPiiAccessAuditRepository {
	override fun append(audit: CustomerPiiAccessAudit) {
		dsl
			.newRecord(CUSTOMER_PII_ACCESS_AUDIT)
			.apply {
				customerPiiAccessAuditId = audit.id.value
				internalUserSeq = dsl.internalUserSeq(audit.internalUserId)
				paymentSeq = dsl.paymentSeq(audit.paymentId)
				reason = audit.reason
				clientIp = audit.clientIp
				occurredAt = audit.occurredAt.toUtcLocalDateTime()
				createdAt = audit.occurredAt.toUtcLocalDateTime()
			}.insert()
	}
}
