package paytech.practice.pay.infra.persistence.jooq.identity

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import paytech.practice.pay.application.port.outbound.MerchantLoginAuditEntry
import paytech.practice.pay.application.port.outbound.MerchantLoginAuditProjection
import paytech.practice.pay.dbcore.jooq.tables.Merchant.Companion.MERCHANT
import paytech.practice.pay.dbcore.jooq.tables.MerchantLoginAudit.Companion.MERCHANT_LOGIN_AUDIT
import paytech.practice.pay.dbcore.jooq.tables.MerchantUser.Companion.MERCHANT_USER
import paytech.practice.pay.domain.identity.MerchantLoginAuditId
import paytech.practice.pay.domain.identity.MerchantLoginOutcome
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.infra.persistence.jooq.toUtcInstant

/**
 * jOOQ로 [MerchantLoginAuditProjection] Port를 구현한다([InternalLoginAuditProjectionAdapter]의
 * 가맹점판).
 *
 * `merchant`와 `merchant_user`를 각각 **LEFT JOIN**한다 — 없는 merchantCode 시도는
 * `merchant_seq`가 NULL이라 매칭되지 않아 공개 `merchant_id`·`merchant_name`이 `null`로 나오고,
 * 없는 loginId 시도는 `user_name`이 `null`로 나온다(읽기 모델이 "알 수 없는 가맹점/계정"을
 * 구분해 보여줄 수 있다).
 */
@Repository
class MerchantLoginAuditProjectionAdapter(
	private val dsl: DSLContext,
) : MerchantLoginAuditProjection {
	override fun findRecent(limit: Int): List<MerchantLoginAuditEntry> =
		dsl
			.select(
				MERCHANT_LOGIN_AUDIT.MERCHANT_LOGIN_AUDIT_ID,
				MERCHANT.MERCHANT_ID,
				MERCHANT.MERCHANT_NAME,
				MERCHANT_LOGIN_AUDIT.ATTEMPTED_MERCHANT_CODE,
				MERCHANT_LOGIN_AUDIT.ATTEMPTED_LOGIN_ID,
				MERCHANT_USER.USER_NAME,
				MERCHANT_LOGIN_AUDIT.LOGIN_OUTCOME,
				MERCHANT_LOGIN_AUDIT.CLIENT_IP,
				MERCHANT_LOGIN_AUDIT.OCCURRED_AT,
			).from(MERCHANT_LOGIN_AUDIT)
			.leftJoin(MERCHANT)
			.on(MERCHANT_LOGIN_AUDIT.MERCHANT_SEQ.eq(MERCHANT.MERCHANT_SEQ))
			.leftJoin(MERCHANT_USER)
			.on(MERCHANT_LOGIN_AUDIT.MERCHANT_USER_SEQ.eq(MERCHANT_USER.MERCHANT_USER_SEQ))
			.orderBy(MERCHANT_LOGIN_AUDIT.OCCURRED_AT.desc())
			.limit(limit)
			.fetch { record ->
				MerchantLoginAuditEntry(
					auditId = MerchantLoginAuditId(record.get(MERCHANT_LOGIN_AUDIT.MERCHANT_LOGIN_AUDIT_ID)!!),
					merchantId = record.get(MERCHANT.MERCHANT_ID)?.let { MerchantId(it) },
					merchantName = record.get(MERCHANT.MERCHANT_NAME),
					attemptedMerchantCode = record.get(MERCHANT_LOGIN_AUDIT.ATTEMPTED_MERCHANT_CODE)!!,
					attemptedLoginId = record.get(MERCHANT_LOGIN_AUDIT.ATTEMPTED_LOGIN_ID)!!,
					userName = record.get(MERCHANT_USER.USER_NAME),
					outcome = MerchantLoginOutcome.valueOf(record.get(MERCHANT_LOGIN_AUDIT.LOGIN_OUTCOME)!!),
					clientIp = record.get(MERCHANT_LOGIN_AUDIT.CLIENT_IP),
					occurredAt = record.get(MERCHANT_LOGIN_AUDIT.OCCURRED_AT)!!.toUtcInstant(),
				)
			}
}
