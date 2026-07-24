package paytech.practice.pay.infra.persistence.jooq.identity

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import paytech.practice.pay.application.port.outbound.MerchantUserListProjection
import paytech.practice.pay.application.port.outbound.MerchantUserSummary
import paytech.practice.pay.dbcore.jooq.tables.Merchant.Companion.MERCHANT
import paytech.practice.pay.dbcore.jooq.tables.MerchantUser.Companion.MERCHANT_USER
import paytech.practice.pay.domain.identity.AccountStatus
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.identity.MerchantUserRole
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.infra.persistence.jooq.toUtcInstant

/**
 * jOOQ로 [MerchantUserListProjection] Port를 구현한다
 * ([MerchantApiKeyListProjectionAdapter][paytech.practice.pay.infra.persistence.jooq.apikey.MerchantApiKeyListProjectionAdapter]와
 * 같은 모양·같은 이유).
 *
 * `MerchantUserRepositoryAdapter`와 같은 테이블을 보지만 별도 클래스로 둔다 —
 * `password_hash`는 애초에 SELECT 목록에 넣지 않는다([MerchantUserListProjection]의
 * KDoc 참고).
 *
 * 도메인의 `MerchantId`는 `merchant.merchant_id`(외부 식별자)이고 `merchant_user`는
 * 내부 `merchant_seq`로 참조하므로, API Key Projection과 같은 방식으로 `merchant_seq`를
 * 먼저 찾아 조회한다 — 가맹점이 없으면 빈 목록이다.
 */
@Repository
class MerchantUserListProjectionAdapter(
	private val dsl: DSLContext,
) : MerchantUserListProjection {
	override fun findByMerchantId(merchantId: MerchantId): List<MerchantUserSummary> {
		val merchantSeq =
			dsl
				.select(MERCHANT.MERCHANT_SEQ)
				.from(MERCHANT)
				.where(MERCHANT.MERCHANT_ID.eq(merchantId.value))
				.fetchOne(MERCHANT.MERCHANT_SEQ)
				?: return emptyList()

		return dsl
			.select(
				MERCHANT_USER.MERCHANT_USER_ID,
				MERCHANT_USER.LOGIN_ID,
				MERCHANT_USER.EMAIL,
				MERCHANT_USER.USER_NAME,
				MERCHANT_USER.ROLE_CODE,
				MERCHANT_USER.USER_STATUS,
				MERCHANT_USER.LAST_LOGIN_AT,
				MERCHANT_USER.CREATED_AT,
			).from(MERCHANT_USER)
			.where(MERCHANT_USER.MERCHANT_SEQ.eq(merchantSeq))
			.orderBy(MERCHANT_USER.CREATED_AT.desc())
			.fetch { record ->
				MerchantUserSummary(
					merchantUserId = MerchantUserId(record.get(MERCHANT_USER.MERCHANT_USER_ID)!!),
					loginId = LoginId(record.get(MERCHANT_USER.LOGIN_ID)!!),
					email = Email(record.get(MERCHANT_USER.EMAIL)!!),
					userName = record.get(MERCHANT_USER.USER_NAME)!!,
					role = MerchantUserRole.valueOf(record.get(MERCHANT_USER.ROLE_CODE)!!),
					status = AccountStatus.valueOf(record.get(MERCHANT_USER.USER_STATUS)!!),
					lastLoginAt = record.get(MERCHANT_USER.LAST_LOGIN_AT)?.toUtcInstant(),
					createdAt = record.get(MERCHANT_USER.CREATED_AT)!!.toUtcInstant(),
				)
			}
	}
}
