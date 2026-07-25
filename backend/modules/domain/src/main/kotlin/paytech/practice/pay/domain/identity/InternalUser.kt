package paytech.practice.pay.domain.identity

import java.time.Instant

/**
 * 내부 운영자(InternalUser) Aggregate Root다.
 *
 * PG 내부 관리자 화면에 로그인하는 계정이며, 내부 운영자 로그인 식별, 내부 역할과
 * 계정 상태, 로그인 실패와 잠금, 내부 계정 발급자 감사 정보를 관리한다
 * (`docs/architecture/identity-access-api-key.md`).
 *
 * 최초 SUPER_ADMIN은 [bootstrap]으로, 그 이후 계정은 SUPER_ADMIN이 [invite]로
 * 발급한다 — 내부 운영자 계정은 `SUPER_ADMIN`만 발급할 수 있다는 규칙은 이
 * Aggregate가 아니라 호출부(애플리케이션 서비스가 호출자의 역할을 확인)의
 * 책임이다. 비밀번호 원문은 이 계층에서 다루지 않는다 — [bootstrap]/[activate]는
 * 이미 해시된 값(`passwordHash`)만 받는다.
 *
 * 인스턴스는 [bootstrap]/[invite]로 새로 만들거나 [reconstitute]로 저장된 값을
 * 복원해서 얻는다.
 *
 * @see docs/domain/domain-model.md
 * @see docs/domain/state-transitions.md
 */
class InternalUser private constructor(
	val id: InternalUserId,
	val loginId: LoginId,
	val email: Email,
	val userName: String,
	val role: InternalUserRole,
	val createdByInternalUserId: InternalUserId?,
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
	}

	/** `INVITED` → `ACTIVE`. 초대받은 계정이 비밀번호를 설정해 활성화된다. */
	fun activate(
		passwordHash: String,
		activatedAt: Instant,
	) {
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
	fun lock(
		lockedUntil: Instant,
		changedAt: Instant,
	) {
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

	private fun checkTransition(
		allowed: Boolean,
		target: AccountStatus,
	) {
		check(allowed) { "InternalUser 상태를 $status 에서 $target (으)로 전이할 수 없습니다." }
	}

	companion object {
		/**
		 * 최초 SUPER_ADMIN을 `ACTIVE` 상태로 직접 생성한다.
		 *
		 * 초대 절차를 거치지 않는다 — 배포 초기화 명령이나 별도 Bootstrap 과정에서
		 * 이미 정해진(또는 생성된) 비밀번호의 해시를 즉시 설정한다. 비밀번호 원문을
		 * DDL이나 저장소에 남기지 않는 것은 호출부의 책임이다.
		 */
		fun bootstrap(
			id: InternalUserId,
			loginId: LoginId,
			email: Email,
			userName: String,
			passwordHash: String,
			createdAt: Instant,
		): InternalUser =
			InternalUser(
				id = id,
				loginId = loginId,
				email = email,
				userName = userName,
				role = InternalUserRole.SUPER_ADMIN,
				createdByInternalUserId = null,
				createdAt = createdAt,
				status = AccountStatus.ACTIVE,
				passwordHash = passwordHash,
				failedLoginCount = 0,
				lockedUntil = null,
				passwordChangedAt = createdAt,
				lastLoginAt = null,
				invitedAt = null,
				activatedAt = createdAt,
				terminatedAt = null,
				updatedAt = createdAt,
			)

		/**
		 * SUPER_ADMIN이 새 내부 운영자를 `INVITED` 상태로 초대한다.
		 *
		 * **`SUPER_ADMIN`은 이 경로로 만들 수 없다** — `docs/architecture/identity-access-api-key.md`의
		 * "3.3 발급 정책"이 "최초 `SUPER_ADMIN`은 배포 초기화 명령, 안전한 운영 절차 또는
		 * 별도 Bootstrap 과정으로 생성한다"고 규정하기 때문이다. 그 경로는 [bootstrap]이다.
		 * [MerchantUser.inviteSubAccount]가 같은 이유로 `OWNER`를 막는 것과 같은 제약이다.
		 */
		fun invite(
			id: InternalUserId,
			loginId: LoginId,
			email: Email,
			userName: String,
			role: InternalUserRole,
			createdByInternalUserId: InternalUserId,
			createdAt: Instant,
		): InternalUser {
			require(role != InternalUserRole.SUPER_ADMIN) {
				"초대로는 SUPER_ADMIN을 만들 수 없습니다: bootstrap을 사용하세요."
			}
			return InternalUser(
				id = id,
				loginId = loginId,
				email = email,
				userName = userName,
				role = role,
				createdByInternalUserId = createdByInternalUserId,
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
			id: InternalUserId,
			loginId: LoginId,
			email: Email,
			userName: String,
			role: InternalUserRole,
			createdByInternalUserId: InternalUserId?,
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
		): InternalUser =
			InternalUser(
				id = id,
				loginId = loginId,
				email = email,
				userName = userName,
				role = role,
				createdByInternalUserId = createdByInternalUserId,
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
