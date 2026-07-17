package paytech.practice.pay.domain.identity

import java.time.Instant

/**
 * 계정 초대(AccountInvitation) Aggregate Root다.
 *
 * 내부 운영자 또는 가맹점 사용자가 본인의 비밀번호를 설정하고 계정을 활성화하기
 * 위한 1회성 초대이며, 초대 대상 계정 연결, 1회성 Token Hash 관리, 만료와 수락
 * 상태 관리를 담당한다(`docs/domain/domain-model.md`). 초대 토큰 원문은 저장하지
 * 않고 Hash만 저장한다 — [tokenHash]는 이미 해시된 값이며, 해시 계산 자체는
 * 애플리케이션/어댑터 계층의 책임이다. [InternalUser]/[MerchantUser]는 ID로만
 * 참조한다.
 *
 * 다른 Aggregate와 달리 `updatedAt`/`version`이 없다 — DB의 `account_invitation`
 * 테이블 자체에 그 두 컬럼이 없다(`docs/database/database-design.md`). 상태
 * 전이 메서드들이 시각을 받지 않는 것([expire], [revoke])은 이 때문이다 —
 * 기록할 컬럼이 없다.
 *
 * 인스턴스는 [forInternalUser]/[forMerchantUser]로 새로 만들거나 [reconstitute]로
 * 저장된 값을 복원해서 얻는다.
 *
 * @see docs/domain/state-transitions.md
 */
class AccountInvitation private constructor(
	val id: AccountInvitationId,
	val accountType: InvitationAccountType,
	val internalUserId: InternalUserId?,
	val merchantUserId: MerchantUserId?,
	val tokenHash: String,
	val expiresAt: Instant,
	val createdAt: Instant,
	status: AccountInvitationStatus,
	acceptedAt: Instant?,
) {

	var status: AccountInvitationStatus = status
		private set

	/** 초대가 `ACCEPTED`로 확정된 시각. `ACCEPTED` 상태에서는 항상 값이 있다. */
	var acceptedAt: Instant? = acceptedAt
		private set

	init {
		require(tokenHash.isNotBlank()) { "tokenHash는 공백일 수 없습니다." }
		when (accountType) {
			InvitationAccountType.INTERNAL_USER -> require(internalUserId != null && merchantUserId == null) {
				"INTERNAL_USER 초대는 internalUserId만 있어야 합니다."
			}
			InvitationAccountType.MERCHANT_USER -> require(merchantUserId != null && internalUserId == null) {
				"MERCHANT_USER 초대는 merchantUserId만 있어야 합니다."
			}
		}
		require(status != AccountInvitationStatus.ACCEPTED || acceptedAt != null) {
			"ACCEPTED 상태는 acceptedAt이 반드시 있어야 합니다."
		}
	}

	/** `PENDING` → `ACCEPTED`. 대상 계정이 비밀번호를 설정해 초대를 수락했다. */
	fun accept(acceptedAt: Instant) {
		checkTransition(status == AccountInvitationStatus.PENDING, AccountInvitationStatus.ACCEPTED)
		status = AccountInvitationStatus.ACCEPTED
		this.acceptedAt = acceptedAt
	}

	/** `PENDING` → `EXPIRED`. */
	fun expire() {
		checkTransition(status == AccountInvitationStatus.PENDING, AccountInvitationStatus.EXPIRED)
		status = AccountInvitationStatus.EXPIRED
	}

	/** `PENDING` → `REVOKED`. */
	fun revoke() {
		checkTransition(status == AccountInvitationStatus.PENDING, AccountInvitationStatus.REVOKED)
		status = AccountInvitationStatus.REVOKED
	}

	private fun checkTransition(allowed: Boolean, target: AccountInvitationStatus) {
		check(allowed) { "AccountInvitation 상태를 $status 에서 $target (으)로 전이할 수 없습니다." }
	}

	companion object {

		/** 내부 운영자 계정을 활성화하기 위한 초대를 `PENDING` 상태로 생성한다. */
		fun forInternalUser(
			id: AccountInvitationId,
			internalUserId: InternalUserId,
			tokenHash: String,
			expiresAt: Instant,
			createdAt: Instant,
		): AccountInvitation = AccountInvitation(
			id = id,
			accountType = InvitationAccountType.INTERNAL_USER,
			internalUserId = internalUserId,
			merchantUserId = null,
			tokenHash = tokenHash,
			expiresAt = expiresAt,
			createdAt = createdAt,
			status = AccountInvitationStatus.PENDING,
			acceptedAt = null,
		)

		/** 가맹점 사용자 계정을 활성화하기 위한 초대를 `PENDING` 상태로 생성한다. */
		fun forMerchantUser(
			id: AccountInvitationId,
			merchantUserId: MerchantUserId,
			tokenHash: String,
			expiresAt: Instant,
			createdAt: Instant,
		): AccountInvitation = AccountInvitation(
			id = id,
			accountType = InvitationAccountType.MERCHANT_USER,
			internalUserId = null,
			merchantUserId = merchantUserId,
			tokenHash = tokenHash,
			expiresAt = expiresAt,
			createdAt = createdAt,
			status = AccountInvitationStatus.PENDING,
			acceptedAt = null,
		)

		/** 영속 계층에 저장되어 있던 값으로 Aggregate를 복원한다. */
		fun reconstitute(
			id: AccountInvitationId,
			accountType: InvitationAccountType,
			internalUserId: InternalUserId?,
			merchantUserId: MerchantUserId?,
			tokenHash: String,
			expiresAt: Instant,
			createdAt: Instant,
			status: AccountInvitationStatus,
			acceptedAt: Instant?,
		): AccountInvitation = AccountInvitation(
			id = id,
			accountType = accountType,
			internalUserId = internalUserId,
			merchantUserId = merchantUserId,
			tokenHash = tokenHash,
			expiresAt = expiresAt,
			createdAt = createdAt,
			status = status,
			acceptedAt = acceptedAt,
		)
	}
}
