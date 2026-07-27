package paytech.practice.pay.infra.persistence.jooq.identity

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import paytech.practice.pay.application.port.outbound.MerchantLoginAuditRepository
import paytech.practice.pay.dbcore.jooq.tables.MerchantLoginAudit.Companion.MERCHANT_LOGIN_AUDIT
import paytech.practice.pay.domain.identity.MerchantLoginAudit
import paytech.practice.pay.infra.persistence.jooq.merchantSeq
import paytech.practice.pay.infra.persistence.jooq.merchantUserSeq
import paytech.practice.pay.infra.persistence.jooq.toUtcLocalDateTime

/**
 * jOOQ로 [MerchantLoginAuditRepository] Port를 구현한다([InternalLoginAuditRepositoryAdapter]의
 * 가맹점판). append-only라 INSERT만 있다. `merchant_seq`/`merchant_user_seq`는 공개 ID에서
 * 해석하되, 없는 merchantCode/loginId 시도는 각각 `NULL`로 남긴다. `created_at`은 별도
 * 의미가 없어 `occurred_at`과 같은 값으로 채운다.
 */
@Repository
class MerchantLoginAuditRepositoryAdapter(
	private val dsl: DSLContext,
) : MerchantLoginAuditRepository {
	override fun append(audit: MerchantLoginAudit) {
		dsl
			.newRecord(MERCHANT_LOGIN_AUDIT)
			.apply {
				merchantLoginAuditId = audit.id.value
				merchantSeq = audit.merchantId?.let { dsl.merchantSeq(it) }
				merchantUserSeq = audit.merchantUserId?.let { dsl.merchantUserSeq(it) }
				attemptedMerchantCode = audit.attemptedMerchantCode
				attemptedLoginId = audit.attemptedLoginId.value
				loginOutcome = audit.outcome.name
				clientIp = audit.clientIp
				occurredAt = audit.occurredAt.toUtcLocalDateTime()
				createdAt = audit.occurredAt.toUtcLocalDateTime()
			}.insert()
	}
}
