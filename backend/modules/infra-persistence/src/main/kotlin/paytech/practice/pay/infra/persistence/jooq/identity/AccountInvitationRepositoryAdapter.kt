package paytech.practice.pay.infra.persistence.jooq.identity

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import paytech.practice.pay.application.port.outbound.AccountInvitationRepository
import paytech.practice.pay.dbcore.jooq.tables.AccountInvitation.Companion.ACCOUNT_INVITATION
import paytech.practice.pay.dbcore.jooq.tables.records.AccountInvitationRecord
import paytech.practice.pay.domain.identity.AccountInvitation
import paytech.practice.pay.domain.identity.AccountInvitationId
import paytech.practice.pay.domain.identity.AccountInvitationStatus
import paytech.practice.pay.domain.identity.InvitationAccountType
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.infra.persistence.jooq.internalUserId
import paytech.practice.pay.infra.persistence.jooq.internalUserSeq
import paytech.practice.pay.infra.persistence.jooq.merchantUserId
import paytech.practice.pay.infra.persistence.jooq.merchantUserSeq
import paytech.practice.pay.infra.persistence.jooq.toUtcInstant
import paytech.practice.pay.infra.persistence.jooq.toUtcLocalDateTime
import java.time.Instant

/**
 * jOOQ로 [AccountInvitationRepository] Port를 구현한다.
 *
 * `account_invitation`은 `version` 컬럼이 없다(`AccountInvitation`의 KDoc 참고) —
 * [paytech.practice.pay.infra.persistence.jooq.identity.InternalUserRepositoryAdapter]와
 * 달리 낙관적 잠금 없이 단순 UPDATE로 상태 전이(`accept`/`expire`/`revoke`)를 반영한다.
 * 지금은 발급(`PENDING` 생성) Use Case만 이 Port를 쓰지만, 수락/만료/폐기 Use Case가
 * 나중에 같은 `save`를 재사용할 수 있도록 UPDATE 경로도 함께 구현해 둔다.
 */
@Repository
class AccountInvitationRepositoryAdapter(
	private val dsl: DSLContext,
) : AccountInvitationRepository {
	override fun save(accountInvitation: AccountInvitation) {
		val existing =
			dsl
				.selectFrom(ACCOUNT_INVITATION)
				.where(ACCOUNT_INVITATION.ACCOUNT_INVITATION_ID.eq(accountInvitation.id.value))
				.fetchOne()

		if (existing == null) {
			dsl
				.newRecord(ACCOUNT_INVITATION)
				.apply {
					accountInvitationId = accountInvitation.id.value
					accountType = accountInvitation.accountType.name
					internalUserSeq = accountInvitation.internalUserId?.let { dsl.internalUserSeq(it) }
					merchantUserSeq = accountInvitation.merchantUserId?.let { dsl.merchantUserSeq(it) }
					tokenHash = accountInvitation.tokenHash
					invitationStatus = accountInvitation.status.name
					expiresAt = accountInvitation.expiresAt.toUtcLocalDateTime()
					acceptedAt = accountInvitation.acceptedAt?.toUtcLocalDateTime()
					createdAt = accountInvitation.createdAt.toUtcLocalDateTime()
				}.insert()
		} else {
			dsl
				.update(ACCOUNT_INVITATION)
				.set(ACCOUNT_INVITATION.INVITATION_STATUS, accountInvitation.status.name)
				.set(ACCOUNT_INVITATION.ACCEPTED_AT, accountInvitation.acceptedAt?.toUtcLocalDateTime())
				.where(ACCOUNT_INVITATION.ACCOUNT_INVITATION_SEQ.eq(existing.accountInvitationSeq))
				.execute()
				.also { updatedRows ->
					check(updatedRows == 1) {
						"AccountInvitation(${accountInvitation.id.value}) 저장에 실패했습니다."
					}
				}
		}
	}

	override fun findByTokenHash(tokenHash: String): AccountInvitation? =
		dsl
			.selectFrom(ACCOUNT_INVITATION)
			.where(ACCOUNT_INVITATION.TOKEN_HASH.eq(tokenHash))
			.fetchOne()
			?.toDomain()

	/**
	 * `PENDING`이 사용자당 하나라는 것은 우리 로직의 규약이지 DB 제약이 아니므로
	 * (Port의 KDoc 참고), 둘 이상이 있어도 터지지 않게 **가장 최근 것 하나**를 돌려준다.
	 */
	override fun findPendingByMerchantUserId(merchantUserId: MerchantUserId): AccountInvitation? =
		dsl
			.selectFrom(ACCOUNT_INVITATION)
			.where(ACCOUNT_INVITATION.MERCHANT_USER_SEQ.eq(dsl.merchantUserSeq(merchantUserId)))
			.and(ACCOUNT_INVITATION.INVITATION_STATUS.eq(AccountInvitationStatus.PENDING.name))
			.orderBy(ACCOUNT_INVITATION.CREATED_AT.desc())
			.limit(1)
			.fetchOne()
			?.toDomain()

	override fun findById(accountInvitationId: AccountInvitationId): AccountInvitation? =
		dsl
			.selectFrom(ACCOUNT_INVITATION)
			.where(ACCOUNT_INVITATION.ACCOUNT_INVITATION_ID.eq(accountInvitationId.value))
			.fetchOne()
			?.toDomain()

	/** 만료 Sweep용 — `PENDING`이고 `expires_at < now`인 초대(Port의 KDoc 참고). */
	override fun findExpirablePending(now: Instant): List<AccountInvitation> =
		dsl
			.selectFrom(ACCOUNT_INVITATION)
			.where(ACCOUNT_INVITATION.INVITATION_STATUS.eq(AccountInvitationStatus.PENDING.name))
			.and(ACCOUNT_INVITATION.EXPIRES_AT.lessThan(now.toUtcLocalDateTime()))
			.fetch()
			.map { it.toDomain() }

	private fun AccountInvitationRecord.toDomain(): AccountInvitation =
		AccountInvitation.reconstitute(
			id = AccountInvitationId(accountInvitationId!!),
			accountType = InvitationAccountType.valueOf(accountType!!),
			internalUserId = internalUserSeq?.let { dsl.internalUserId(it) },
			merchantUserId = merchantUserSeq?.let { dsl.merchantUserId(it) },
			tokenHash = tokenHash!!,
			expiresAt = expiresAt!!.toUtcInstant(),
			createdAt = createdAt!!.toUtcInstant(),
			status = AccountInvitationStatus.valueOf(invitationStatus!!),
			acceptedAt = acceptedAt?.toUtcInstant(),
		)
}
