package paytech.practice.pay.infra.persistence.jooq.identity

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import paytech.practice.pay.application.port.outbound.InternalUserListProjection
import paytech.practice.pay.application.port.outbound.InternalUserSummary
import paytech.practice.pay.dbcore.jooq.tables.InternalUser.Companion.INTERNAL_USER
import paytech.practice.pay.domain.identity.AccountStatus
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.InternalUserRole
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.infra.persistence.jooq.toUtcInstant

/**
 * jOOQ로 [InternalUserListProjection] Port를 구현한다
 * ([MerchantUserListProjectionAdapter]와 같은 모양·같은 이유).
 *
 * `InternalUserRepositoryAdapter`와 같은 테이블을 보지만 별도 클래스로 둔다 —
 * `password_hash`는 애초에 SELECT 목록에 넣지 않는다(Port의 KDoc 참고). 가맹점
 * Projection들과 달리 범위를 좁히는 `merchant_seq`가 없다.
 */
@Repository
class InternalUserListProjectionAdapter(
	private val dsl: DSLContext,
) : InternalUserListProjection {
	override fun findAll(): List<InternalUserSummary> =
		dsl
			.select(
				INTERNAL_USER.INTERNAL_USER_ID,
				INTERNAL_USER.LOGIN_ID,
				INTERNAL_USER.EMAIL,
				INTERNAL_USER.USER_NAME,
				INTERNAL_USER.ROLE_CODE,
				INTERNAL_USER.USER_STATUS,
				INTERNAL_USER.LAST_LOGIN_AT,
				INTERNAL_USER.CREATED_AT,
			).from(INTERNAL_USER)
			.orderBy(INTERNAL_USER.CREATED_AT.desc())
			.fetch { record ->
				InternalUserSummary(
					internalUserId = InternalUserId(record.get(INTERNAL_USER.INTERNAL_USER_ID)!!),
					loginId = LoginId(record.get(INTERNAL_USER.LOGIN_ID)!!),
					email = Email(record.get(INTERNAL_USER.EMAIL)!!),
					userName = record.get(INTERNAL_USER.USER_NAME)!!,
					role = InternalUserRole.valueOf(record.get(INTERNAL_USER.ROLE_CODE)!!),
					status = AccountStatus.valueOf(record.get(INTERNAL_USER.USER_STATUS)!!),
					lastLoginAt = record.get(INTERNAL_USER.LAST_LOGIN_AT)?.toUtcInstant(),
					createdAt = record.get(INTERNAL_USER.CREATED_AT)!!.toUtcInstant(),
				)
			}
}
