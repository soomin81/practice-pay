package paytech.practice.pay.infra.persistence.jooq.identity

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import paytech.practice.pay.application.port.outbound.InternalLoginAuditEntry
import paytech.practice.pay.application.port.outbound.InternalLoginAuditProjection
import paytech.practice.pay.dbcore.jooq.tables.InternalLoginAudit.Companion.INTERNAL_LOGIN_AUDIT
import paytech.practice.pay.dbcore.jooq.tables.InternalUser.Companion.INTERNAL_USER
import paytech.practice.pay.domain.identity.InternalLoginAuditId
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.LoginOutcome
import paytech.practice.pay.infra.persistence.jooq.toUtcInstant

/**
 * jOOQ로 [InternalLoginAuditProjection] Port를 구현한다.
 *
 * `internal_user`를 **LEFT JOIN**한다 — 없는 `loginId`로의 시도는 `internal_user_seq`가
 * `NULL`이라 매칭되지 않고, 그 경우 공개 `internal_user_id`와 `user_name`이 `null`로 나온다
 * (읽기 모델이 "알 수 없는 계정" 시도를 구분해 보여줄 수 있다).
 */
@Repository
class InternalLoginAuditProjectionAdapter(
	private val dsl: DSLContext,
) : InternalLoginAuditProjection {
	override fun findRecent(limit: Int): List<InternalLoginAuditEntry> =
		dsl
			.select(
				INTERNAL_LOGIN_AUDIT.INTERNAL_LOGIN_AUDIT_ID,
				INTERNAL_USER.INTERNAL_USER_ID,
				INTERNAL_LOGIN_AUDIT.ATTEMPTED_LOGIN_ID,
				INTERNAL_USER.USER_NAME,
				INTERNAL_LOGIN_AUDIT.LOGIN_OUTCOME,
				INTERNAL_LOGIN_AUDIT.CLIENT_IP,
				INTERNAL_LOGIN_AUDIT.OCCURRED_AT,
			).from(INTERNAL_LOGIN_AUDIT)
			.leftJoin(INTERNAL_USER)
			.on(INTERNAL_LOGIN_AUDIT.INTERNAL_USER_SEQ.eq(INTERNAL_USER.INTERNAL_USER_SEQ))
			.orderBy(INTERNAL_LOGIN_AUDIT.OCCURRED_AT.desc())
			.limit(limit)
			.fetch { record ->
				InternalLoginAuditEntry(
					auditId = InternalLoginAuditId(record.get(INTERNAL_LOGIN_AUDIT.INTERNAL_LOGIN_AUDIT_ID)!!),
					internalUserId = record.get(INTERNAL_USER.INTERNAL_USER_ID)?.let { InternalUserId(it) },
					attemptedLoginId = record.get(INTERNAL_LOGIN_AUDIT.ATTEMPTED_LOGIN_ID)!!,
					userName = record.get(INTERNAL_USER.USER_NAME),
					outcome = LoginOutcome.valueOf(record.get(INTERNAL_LOGIN_AUDIT.LOGIN_OUTCOME)!!),
					clientIp = record.get(INTERNAL_LOGIN_AUDIT.CLIENT_IP),
					occurredAt = record.get(INTERNAL_LOGIN_AUDIT.OCCURRED_AT)!!.toUtcInstant(),
				)
			}
}
