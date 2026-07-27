package paytech.practice.pay.infra.persistence.jooq.identity

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import paytech.practice.pay.application.port.outbound.InternalLoginAuditRepository
import paytech.practice.pay.dbcore.jooq.tables.InternalLoginAudit.Companion.INTERNAL_LOGIN_AUDIT
import paytech.practice.pay.domain.identity.InternalLoginAudit
import paytech.practice.pay.infra.persistence.jooq.internalUserSeq
import paytech.practice.pay.infra.persistence.jooq.toUtcLocalDateTime

/**
 * jOOQ로 [InternalLoginAuditRepository] Port를 구현한다.
 *
 * append-only라 INSERT만 있다(UPDATE 경로가 없다). `internal_user_seq`는 다른 어댑터와 같은
 * 방식으로 공개 ID에서 해석하되, 없는 `loginId`로의 시도는 `internalUserId`가 `null`이라
 * `NULL`로 남긴다. `created_at`은 별도 의미가 없어 `occurred_at`과 같은 값으로 채운다.
 */
@Repository
class InternalLoginAuditRepositoryAdapter(
	private val dsl: DSLContext,
) : InternalLoginAuditRepository {
	override fun append(audit: InternalLoginAudit) {
		dsl
			.newRecord(INTERNAL_LOGIN_AUDIT)
			.apply {
				internalLoginAuditId = audit.id.value
				internalUserSeq = audit.internalUserId?.let { dsl.internalUserSeq(it) }
				attemptedLoginId = audit.attemptedLoginId.value
				loginOutcome = audit.outcome.name
				clientIp = audit.clientIp
				occurredAt = audit.occurredAt.toUtcLocalDateTime()
				createdAt = audit.occurredAt.toUtcLocalDateTime()
			}.insert()
	}
}
