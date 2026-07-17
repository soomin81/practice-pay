package paytech.practice.pay.infra.persistence.jooq.identity

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import paytech.practice.pay.application.port.outbound.InternalUserRepository
import paytech.practice.pay.dbcore.jooq.tables.InternalUser.Companion.INTERNAL_USER
import paytech.practice.pay.dbcore.jooq.tables.records.InternalUserRecord
import paytech.practice.pay.domain.identity.AccountStatus
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalUser
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.InternalUserRole
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.infra.persistence.jooq.toUtcInstant
import paytech.practice.pay.infra.persistence.jooq.toUtcLocalDateTime

/**
 * jOOQ로 [InternalUserRepository] Port를 구현한다.
 *
 * `save`의 낙관적 잠금 한계는 [paytech.practice.pay.infra.persistence.jooq.payment.PaymentRepositoryAdapter]와
 * 동일하다 — 도메인 [InternalUser]가 자신의 `version`을 모르기 때문에, DB에서 방금
 * 읽은 version을 그대로 +1 해서 쓴다.
 */
@Repository
class InternalUserRepositoryAdapter(
	private val dsl: DSLContext,
) : InternalUserRepository {
	override fun save(internalUser: InternalUser) {
		val existing =
			dsl
				.selectFrom(INTERNAL_USER)
				.where(INTERNAL_USER.INTERNAL_USER_ID.eq(internalUser.id.value))
				.fetchOne()

		if (existing == null) {
			dsl
				.newRecord(INTERNAL_USER)
				.apply {
					fillFrom(internalUser)
					version = 0L
				}.insert()
		} else {
			dsl
				.update(INTERNAL_USER)
				.set(INTERNAL_USER.USER_STATUS, internalUser.status.name)
				.set(INTERNAL_USER.PASSWORD_HASH, internalUser.passwordHash)
				.set(INTERNAL_USER.FAILED_LOGIN_COUNT, internalUser.failedLoginCount)
				.set(INTERNAL_USER.LOCKED_UNTIL, internalUser.lockedUntil?.toUtcLocalDateTime())
				.set(INTERNAL_USER.PASSWORD_CHANGED_AT, internalUser.passwordChangedAt?.toUtcLocalDateTime())
				.set(INTERNAL_USER.LAST_LOGIN_AT, internalUser.lastLoginAt?.toUtcLocalDateTime())
				.set(INTERNAL_USER.ACTIVATED_AT, internalUser.activatedAt?.toUtcLocalDateTime())
				.set(INTERNAL_USER.TERMINATED_AT, internalUser.terminatedAt?.toUtcLocalDateTime())
				.set(INTERNAL_USER.UPDATED_AT, internalUser.updatedAt.toUtcLocalDateTime())
				.set(INTERNAL_USER.VERSION, (existing.version ?: 0L) + 1)
				.where(INTERNAL_USER.INTERNAL_USER_SEQ.eq(existing.internalUserSeq))
				.and(INTERNAL_USER.VERSION.eq(existing.version))
				.execute()
				.also { updatedRows ->
					check(updatedRows == 1) {
						"InternalUser(${internalUser.id.value}) 저장에 실패했습니다 — " +
							"동시에 변경된 것으로 보입니다(예상 version=${existing.version})."
					}
				}
		}
	}

	override fun findByLoginId(loginId: LoginId): InternalUser? =
		dsl
			.selectFrom(INTERNAL_USER)
			.where(INTERNAL_USER.LOGIN_ID.eq(loginId.value))
			.fetchOne()
			?.toDomain()

	override fun findByEmail(email: Email): InternalUser? =
		dsl
			.selectFrom(INTERNAL_USER)
			.where(INTERNAL_USER.EMAIL.eq(email.value))
			.fetchOne()
			?.toDomain()

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

	private fun InternalUserRecord.fillFrom(internalUser: InternalUser) {
		internalUserId = internalUser.id.value
		loginId = internalUser.loginId.value
		email = internalUser.email.value
		userName = internalUser.userName
		passwordHash = internalUser.passwordHash
		userStatus = internalUser.status.name
		roleCode = internalUser.role.name
		failedLoginCount = internalUser.failedLoginCount
		lockedUntil = internalUser.lockedUntil?.toUtcLocalDateTime()
		passwordChangedAt = internalUser.passwordChangedAt?.toUtcLocalDateTime()
		lastLoginAt = internalUser.lastLoginAt?.toUtcLocalDateTime()
		invitedAt = internalUser.invitedAt?.toUtcLocalDateTime()
		activatedAt = internalUser.activatedAt?.toUtcLocalDateTime()
		terminatedAt = internalUser.terminatedAt?.toUtcLocalDateTime()
		createdByInternalUserSeq = internalUser.createdByInternalUserId?.let { resolveInternalUserSeq(it) }
		createdAt = internalUser.createdAt.toUtcLocalDateTime()
		updatedAt = internalUser.updatedAt.toUtcLocalDateTime()
	}

	private fun InternalUserRecord.toDomain(): InternalUser =
		InternalUser.reconstitute(
			id = InternalUserId(internalUserId!!),
			loginId = LoginId(loginId!!),
			email = Email(email!!),
			userName = userName!!,
			role = InternalUserRole.valueOf(roleCode!!),
			createdByInternalUserId = createdByInternalUserSeq?.let { resolveInternalUserId(it) },
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
