package paytech.practice.pay.domain.identity

import java.time.Instant
import paytech.practice.pay.domain.merchant.MerchantId

/**
 * 가맹점 사용자(MerchantUser) Aggregate Root다.
 *
 * 특정 가맹점의 관리자 화면에 로그인하는 계정이며, 가맹점 관리자 로그인 식별,
 * 소속 [Merchant][MerchantId], 가맹점 역할과 계정 상태, 하위 계정 초대 및 발급,
 * API Key 발급·폐기 권한 판단을 관리한다(`docs/architecture/identity-access-api-key.md`).
 * `Merchant`는 ID로만 참조한다.
 *
 * 최초 `OWNER`는 가맹점 등록 트랜잭션에서 내부 운영자가 [inviteInitialOwner]로
 * 생성하고, 그 이후의 `ADMIN`/`VIEWER` 하위 계정은 같은 가맹점의 `OWNER` 또는
 * `ADMIN`이 [inviteSubAccount]로 발급한다 — 호출자가 실제로 그 권한이 있는지는
 * [canInviteSubAccounts]로 확인한 뒤 호출부(애플리케이션 서비스)가 판단한다.
 * 비밀번호 원문은 이 계층에서 다루지 않는다.
 *
 * 인스턴스는 [inviteInitialOwner]/[inviteSubAccount]로 새로 만들거나
 * [reconstitute]로 저장된 값을 복원해서 얻는다.
 *
 * @see docs/domain/domain-model.md
 * @see docs/domain/state-transitions.md
 */
class MerchantUser private constructor(
	val id: MerchantUserId,
	val merchantId: MerchantId,
	val loginId: LoginId,
	val email: Email,
	val userName: String,
	val role: MerchantUserRole,
	val invitedByInternalUserId: InternalUserId?,
	val invitedByMerchantUserId: MerchantUserId?,
	val createdAt: Instant,
	status: AccountStatus,
	passwordHash: String?,
	failedLoginCount: Int,
	lockedUntil: Instant?,
	passwordChangedAt: Instant?,
	lastLoginAt: Instant?,
	invitedAt: Instant?,
	activatedAt: Instant?,
	terminatedAt: Instant?,
	updatedAt: Instant,
) {

	var status: AccountStatus = status
		private set

	/** 비밀번호 해시. 평문은 절대 보관하지 않는다. [status]가 `ACTIVE`면 항상 값이 있다. */
	var passwordHash: String? = passwordHash
		private set

	var failedLoginCount: Int = failedLoginCount
		private set

	var lockedUntil: Instant? = lockedUntil
		private set

	var passwordChangedAt: Instant? = passwordChangedAt
		private set

	var lastLoginAt: Instant? = lastLoginAt
		private set

	var invitedAt: Instant? = invitedAt
		private set

	var activatedAt: Instant? = activatedAt
		private set

	var terminatedAt: Instant? = terminatedAt
		private set

	var updatedAt: Instant = updatedAt
		private set

	init {
		require(userName.isNotBlank()) { "userName은 공백일 수 없습니다." }
		require(failedLoginCount >= 0) { "failedLoginCount는 음수일 수 없습니다: $failedLoginCount" }
		require(status != AccountStatus.ACTIVE || passwordHash != null) {
			"ACTIVE 상태는 passwordHash가 반드시 있어야 합니다."
		}
		require(invitedByInternalUserId == null || invitedByMerchantUserId == null) {
			"invitedByInternalUserId와 invitedByMerchantUserId를 동시에 설정할 수 없습니다."
		}
	}

	/** `ACTIVE` 상태의 `OWNER` 또는 `ADMIN`만 같은 가맹점의 하위 계정을 발급할 수 있다. */
	fun canInviteSubAccounts(): Boolean =
		status == AccountStatus.ACTIVE && (role == MerchantUserRole.OWNER || role == MerchantUserRole.ADMIN)

	/** `ACTIVE` 상태의 `OWNER` 또는 `ADMIN`만 API Key를 발급·폐기할 수 있다. */
	fun canManageApiKeys(): Boolean =
		status == AccountStatus.ACTIVE && (role == MerchantUserRole.OWNER || role == MerchantUserRole.ADMIN)

	/** `INVITED` → `ACTIVE`. 초대받은 계정이 비밀번호를 설정해 활성화된다. */
	fun activate(passwordHash: String, activatedAt: Instant) {
		checkTransition(status == AccountStatus.INVITED, AccountStatus.ACTIVE)
		status = AccountStatus.ACTIVE
		this.passwordHash = passwordHash
		this.passwordChangedAt = activatedAt
		this.activatedAt = activatedAt
		updatedAt = activatedAt
	}

	/** 로그인 실패를 기록한다. 몇 번째 실패에 잠글지는 호출부가 [lock]으로 결정한다. */
	fun recordFailedLogin(changedAt: Instant) {
		check(status == AccountStatus.ACTIVE) { "ACTIVE 상태가 아니면 로그인을 시도할 수 없습니다: 현재 상태=$status" }
		failedLoginCount += 1
		updatedAt = changedAt
	}

	/** 로그인 성공을 기록하고 실패 횟수를 초기화한다. */
	fun recordSuccessfulLogin(loginAt: Instant) {
		check(status == AccountStatus.ACTIVE) { "ACTIVE 상태가 아니면 로그인을 시도할 수 없습니다: 현재 상태=$status" }
		failedLoginCount = 0
		lastLoginAt = loginAt
		updatedAt = loginAt
	}

	/** `ACTIVE` → `LOCKED`. */
	fun lock(lockedUntil: Instant, changedAt: Instant) {
		checkTransition(status == AccountStatus.ACTIVE, AccountStatus.LOCKED)
		status = AccountStatus.LOCKED
		this.lockedUntil = lockedUntil
		updatedAt = changedAt
	}

	/** `LOCKED` → `ACTIVE`. 실패 횟수와 잠금 해제 시각을 초기화한다. */
	fun unlock(changedAt: Instant) {
		checkTransition(status == AccountStatus.LOCKED, AccountStatus.ACTIVE)
		status = AccountStatus.ACTIVE
		failedLoginCount = 0
		lockedUntil = null
		updatedAt = changedAt
	}

	/** `ACTIVE` → `SUSPENDED`. */
	fun suspend(changedAt: Instant) {
		checkTransition(status == AccountStatus.ACTIVE, AccountStatus.SUSPENDED)
		status = AccountStatus.SUSPENDED
		updatedAt = changedAt
	}

	/** `SUSPENDED` → `ACTIVE`. */
	fun reactivate(changedAt: Instant) {
		checkTransition(status == AccountStatus.SUSPENDED, AccountStatus.ACTIVE)
		status = AccountStatus.ACTIVE
		updatedAt = changedAt
	}

	/** (`ACTIVE` 또는 `SUSPENDED`) → `TERMINATED`. 종료 상태이며 되돌릴 수 없다. */
	fun terminate(terminatedAt: Instant) {
		checkTransition(
			status == AccountStatus.ACTIVE || status == AccountStatus.SUSPENDED,
			AccountStatus.TERMINATED,
		)
		status = AccountStatus.TERMINATED
		this.terminatedAt = terminatedAt
		updatedAt = terminatedAt
	}

	private fun checkTransition(allowed: Boolean, target: AccountStatus) {
		check(allowed) { "MerchantUser 상태를 $status 에서 $target (으)로 전이할 수 없습니다." }
	}

	companion object {

		/** 가맹점 등록 트랜잭션에서 내부 운영자가 최초 `OWNER`를 `INVITED` 상태로 생성한다. */
		fun inviteInitialOwner(
			id: MerchantUserId,
			merchantId: MerchantId,
			loginId: LoginId,
			email: Email,
			userName: String,
			invitedByInternalUserId: InternalUserId,
			createdAt: Instant,
		): MerchantUser = MerchantUser(
			id = id,
			merchantId = merchantId,
			loginId = loginId,
			email = email,
			userName = userName,
			role = MerchantUserRole.OWNER,
			invitedByInternalUserId = invitedByInternalUserId,
			invitedByMerchantUserId = null,
			createdAt = createdAt,
			status = AccountStatus.INVITED,
			passwordHash = null,
			failedLoginCount = 0,
			lockedUntil = null,
			passwordChangedAt = null,
			lastLoginAt = null,
			invitedAt = createdAt,
			activatedAt = null,
			terminatedAt = null,
			updatedAt = createdAt,
		)

		/**
		 * `OWNER` 또는 `ADMIN`이 같은 가맹점의 `ADMIN`/`VIEWER` 하위 계정을
		 * `INVITED` 상태로 발급한다.
		 *
		 * `OWNER`는 이 경로로 만들 수 없다 — [inviteInitialOwner]로만 생성된다.
		 */
		fun inviteSubAccount(
			id: MerchantUserId,
			merchantId: MerchantId,
			loginId: LoginId,
			email: Email,
			userName: String,
			role: MerchantUserRole,
			invitedByMerchantUserId: MerchantUserId,
			createdAt: Instant,
		): MerchantUser {
			require(role != MerchantUserRole.OWNER) {
				"하위 계정 발급으로는 OWNER를 만들 수 없습니다: inviteInitialOwner를 사용하세요."
			}
			return MerchantUser(
				id = id,
				merchantId = merchantId,
				loginId = loginId,
				email = email,
				userName = userName,
				role = role,
				invitedByInternalUserId = null,
				invitedByMerchantUserId = invitedByMerchantUserId,
				createdAt = createdAt,
				status = AccountStatus.INVITED,
				passwordHash = null,
				failedLoginCount = 0,
				lockedUntil = null,
				passwordChangedAt = null,
				lastLoginAt = null,
				invitedAt = createdAt,
				activatedAt = null,
				terminatedAt = null,
				updatedAt = createdAt,
			)
		}

		/** 영속 계층에 저장되어 있던 값으로 Aggregate를 복원한다. */
		fun reconstitute(
			id: MerchantUserId,
			merchantId: MerchantId,
			loginId: LoginId,
			email: Email,
			userName: String,
			role: MerchantUserRole,
			invitedByInternalUserId: InternalUserId?,
			invitedByMerchantUserId: MerchantUserId?,
			createdAt: Instant,
			status: AccountStatus,
			passwordHash: String?,
			failedLoginCount: Int,
			lockedUntil: Instant?,
			passwordChangedAt: Instant?,
			lastLoginAt: Instant?,
			invitedAt: Instant?,
			activatedAt: Instant?,
			terminatedAt: Instant?,
			updatedAt: Instant,
		): MerchantUser = MerchantUser(
			id = id,
			merchantId = merchantId,
			loginId = loginId,
			email = email,
			userName = userName,
			role = role,
			invitedByInternalUserId = invitedByInternalUserId,
			invitedByMerchantUserId = invitedByMerchantUserId,
			createdAt = createdAt,
			status = status,
			passwordHash = passwordHash,
			failedLoginCount = failedLoginCount,
			lockedUntil = lockedUntil,
			passwordChangedAt = passwordChangedAt,
			lastLoginAt = lastLoginAt,
			invitedAt = invitedAt,
			activatedAt = activatedAt,
			terminatedAt = terminatedAt,
			updatedAt = updatedAt,
		)
	}
}
