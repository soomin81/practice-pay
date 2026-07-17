package paytech.practice.pay.infra.persistence.jooq.identity

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import paytech.practice.pay.application.port.outbound.MerchantUserRepository
import paytech.practice.pay.dbcore.jooq.tables.InternalUser.Companion.INTERNAL_USER
import paytech.practice.pay.dbcore.jooq.tables.Merchant.Companion.MERCHANT
import paytech.practice.pay.dbcore.jooq.tables.MerchantUser.Companion.MERCHANT_USER
import paytech.practice.pay.dbcore.jooq.tables.records.MerchantUserRecord
import paytech.practice.pay.domain.identity.AccountStatus
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.MerchantUser
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.identity.MerchantUserRole
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.infra.persistence.jooq.toUtcInstant
import paytech.practice.pay.infra.persistence.jooq.toUtcLocalDateTime

/**
 * jOOQ로 [MerchantUserRepository] Port를 구현한다.
 *
 * `save`의 낙관적 잠금 한계는 [paytech.practice.pay.infra.persistence.jooq.payment.PaymentRepositoryAdapter]와
 * 동일하다.
 */
@Repository
class MerchantUserRepositoryAdapter(
	private val dsl: DSLContext,
) : MerchantUserRepository {
	override fun save(merchantUser: MerchantUser) {
		val existing =
			dsl
				.selectFrom(MERCHANT_USER)
				.where(MERCHANT_USER.MERCHANT_USER_ID.eq(merchantUser.id.value))
				.fetchOne()

		if (existing == null) {
			dsl
				.newRecord(MERCHANT_USER)
				.apply {
					fillFrom(merchantUser)
					version = 0L
				}.insert()
		} else {
			dsl
				.update(MERCHANT_USER)
				.set(MERCHANT_USER.USER_STATUS, merchantUser.status.name)
				.set(MERCHANT_USER.PASSWORD_HASH, merchantUser.passwordHash)
				.set(MERCHANT_USER.FAILED_LOGIN_COUNT, merchantUser.failedLoginCount)
				.set(MERCHANT_USER.LOCKED_UNTIL, merchantUser.lockedUntil?.toUtcLocalDateTime())
				.set(MERCHANT_USER.PASSWORD_CHANGED_AT, merchantUser.passwordChangedAt?.toUtcLocalDateTime())
				.set(MERCHANT_USER.LAST_LOGIN_AT, merchantUser.lastLoginAt?.toUtcLocalDateTime())
				.set(MERCHANT_USER.ACTIVATED_AT, merchantUser.activatedAt?.toUtcLocalDateTime())
				.set(MERCHANT_USER.TERMINATED_AT, merchantUser.terminatedAt?.toUtcLocalDateTime())
				.set(MERCHANT_USER.UPDATED_AT, merchantUser.updatedAt.toUtcLocalDateTime())
				.set(MERCHANT_USER.VERSION, (existing.version ?: 0L) + 1)
				.where(MERCHANT_USER.MERCHANT_USER_SEQ.eq(existing.merchantUserSeq))
				.and(MERCHANT_USER.VERSION.eq(existing.version))
				.execute()
				.also { updatedRows ->
					check(updatedRows == 1) {
						"MerchantUser(${merchantUser.id.value}) 저장에 실패했습니다 — " +
							"동시에 변경된 것으로 보입니다(예상 version=${existing.version})."
					}
				}
		}
	}

	override fun findByMerchantIdAndLoginId(
		merchantId: MerchantId,
		loginId: LoginId,
	): MerchantUser? =
		dsl
			.selectFrom(MERCHANT_USER)
			.where(MERCHANT_USER.MERCHANT_SEQ.eq(resolveMerchantSeq(merchantId)))
			.and(MERCHANT_USER.LOGIN_ID.eq(loginId.value))
			.fetchOne()
			?.toDomain(merchantId)

	private fun resolveMerchantSeq(merchantId: MerchantId): Long =
		dsl
			.select(MERCHANT.MERCHANT_SEQ)
			.from(MERCHANT)
			.where(MERCHANT.MERCHANT_ID.eq(merchantId.value))
			.fetchOne(MERCHANT.MERCHANT_SEQ)
			?: error("Merchant(${merchantId.value})를 찾을 수 없습니다.")

	private fun resolveMerchantId(merchantSeq: Long): MerchantId =
		dsl
			.select(MERCHANT.MERCHANT_ID)
			.from(MERCHANT)
			.where(MERCHANT.MERCHANT_SEQ.eq(merchantSeq))
			.fetchOne(MERCHANT.MERCHANT_ID)
			?.let { MerchantId(it) }
			?: error("Merchant(seq=$merchantSeq)를 찾을 수 없습니다.")

	private fun resolveInternalUserSeq(internalUserId: InternalUserId): Long =
		dsl
			.select(INTERNAL_USER.INTERNAL_USER_SEQ)
			.from(INTERNAL_USER)
			.where(INTERNAL_USER.INTERNAL_USER_ID.eq(internalUserId.value))
			.fetchOne(INTERNAL_USER.INTERNAL_USER_SEQ)
			?: error("InternalUser(${internalUserId.value})를 찾을 수 없습니다.")

	private fun resolveInternalUserId(internalUserSeq: Long): InternalUserId =
		dsl
			.select(INTERNAL_USER.INTERNAL_USER_ID)
			.from(INTERNAL_USER)
			.where(INTERNAL_USER.INTERNAL_USER_SEQ.eq(internalUserSeq))
			.fetchOne(INTERNAL_USER.INTERNAL_USER_ID)
			?.let { InternalUserId(it) }
			?: error("InternalUser(seq=$internalUserSeq)를 찾을 수 없습니다.")

	private fun resolveMerchantUserSeq(merchantUserId: MerchantUserId): Long =
		dsl
			.select(MERCHANT_USER.MERCHANT_USER_SEQ)
			.from(MERCHANT_USER)
			.where(MERCHANT_USER.MERCHANT_USER_ID.eq(merchantUserId.value))
			.fetchOne(MERCHANT_USER.MERCHANT_USER_SEQ)
			?: error("MerchantUser(${merchantUserId.value})를 찾을 수 없습니다.")

	private fun resolveMerchantUserId(merchantUserSeq: Long): MerchantUserId =
		dsl
			.select(MERCHANT_USER.MERCHANT_USER_ID)
			.from(MERCHANT_USER)
			.where(MERCHANT_USER.MERCHANT_USER_SEQ.eq(merchantUserSeq))
			.fetchOne(MERCHANT_USER.MERCHANT_USER_ID)
			?.let { MerchantUserId(it) }
			?: error("MerchantUser(seq=$merchantUserSeq)를 찾을 수 없습니다.")

	private fun MerchantUserRecord.fillFrom(merchantUser: MerchantUser) {
		merchantUserId = merchantUser.id.value
		merchantSeq = resolveMerchantSeq(merchantUser.merchantId)
		loginId = merchantUser.loginId.value
		email = merchantUser.email.value
		userName = merchantUser.userName
		passwordHash = merchantUser.passwordHash
		userStatus = merchantUser.status.name
		roleCode = merchantUser.role.name
		failedLoginCount = merchantUser.failedLoginCount
		lockedUntil = merchantUser.lockedUntil?.toUtcLocalDateTime()
		passwordChangedAt = merchantUser.passwordChangedAt?.toUtcLocalDateTime()
		lastLoginAt = merchantUser.lastLoginAt?.toUtcLocalDateTime()
		invitedAt = merchantUser.invitedAt?.toUtcLocalDateTime()
		activatedAt = merchantUser.activatedAt?.toUtcLocalDateTime()
		terminatedAt = merchantUser.terminatedAt?.toUtcLocalDateTime()
		invitedByInternalUserSeq = merchantUser.invitedByInternalUserId?.let { resolveInternalUserSeq(it) }
		invitedByMerchantUserSeq = merchantUser.invitedByMerchantUserId?.let { resolveMerchantUserSeq(it) }
		createdAt = merchantUser.createdAt.toUtcLocalDateTime()
		updatedAt = merchantUser.updatedAt.toUtcLocalDateTime()
	}

	private fun MerchantUserRecord.toDomain(merchantId: MerchantId): MerchantUser =
		MerchantUser.reconstitute(
			id = MerchantUserId(merchantUserId!!),
			merchantId = merchantId,
			loginId = LoginId(loginId!!),
			email = Email(email!!),
			userName = userName!!,
			role = MerchantUserRole.valueOf(roleCode!!),
			invitedByInternalUserId = invitedByInternalUserSeq?.let { resolveInternalUserId(it) },
			invitedByMerchantUserId = invitedByMerchantUserSeq?.let { resolveMerchantUserId(it) },
			createdAt = createdAt!!.toUtcInstant(),
			status = AccountStatus.valueOf(userStatus!!),
			passwordHash = passwordHash,
			failedLoginCount = failedLoginCount!!,
			lockedUntil = lockedUntil?.toUtcInstant(),
			passwordChangedAt = passwordChangedAt?.toUtcInstant(),
			lastLoginAt = lastLoginAt?.toUtcInstant(),
			invitedAt = invitedAt?.toUtcInstant(),
			activatedAt = activatedAt?.toUtcInstant(),
			terminatedAt = terminatedAt?.toUtcInstant(),
			updatedAt = updatedAt!!.toUtcInstant(),
		)
}
