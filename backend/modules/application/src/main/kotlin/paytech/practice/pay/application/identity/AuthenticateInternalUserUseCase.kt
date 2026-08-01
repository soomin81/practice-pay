package paytech.practice.pay.application.identity

import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.InternalLoginAuditRepository
import paytech.practice.pay.application.port.outbound.InternalUserRepository
import paytech.practice.pay.application.port.outbound.PasswordEncoder
import paytech.practice.pay.domain.identity.AccountStatus
import paytech.practice.pay.domain.identity.InternalLoginAudit
import paytech.practice.pay.domain.identity.InternalLoginAuditId
import paytech.practice.pay.domain.identity.InternalUser
import paytech.practice.pay.domain.identity.LoginOutcome
import java.time.Clock
import java.time.Instant

/**
 * "내부 운영자 로그인" Use Case다(`docs/architecture/identity-access-api-key.md`의
 * "3.4 로그인 경로").
 *
 * 실패 누적 잠금 기준(횟수·기간)은 [LoginLockoutPolicy]가 갖는다 —
 * [AuthenticateMerchantUserUseCase]와 **같은 값을 공유한다**(원래 두 Use Case가 각자
 * 복제하고 있었다).
 *
 * 계정이 존재하지 않거나, `ACTIVE`가 아니거나(잠금이 아직 안 풀린 `LOCKED`는
 * [AccountLockedException]으로 구분), 비밀번호가 틀리면 [InvalidCredentialsException]을
 * 던진다 — 어느 경우인지 호출부(결국 클라이언트)에 드러내지 않는다.
 *
 * **모든 종료 지점에서 감사 기록을 남긴다**([InternalLoginAuditRepository]) — 성공·실패·잠금,
 * 그리고 없는 `loginId`로의 시도(`internalUserId=null`)까지. 감사 write는 로그인 성공 저장과
 * 트랜잭션으로 묶지 않는다(별도 write) — 단일 DB 전제의 MVP 단순화이고, 만약 감사 write가
 * 실패하면 로그인 요청도 실패하는 것을 감수한다(감사 누락을 조용히 삼키지 않는다).
 */
class AuthenticateInternalUserUseCase(
	private val internalUserRepository: InternalUserRepository,
	private val passwordEncoder: PasswordEncoder,
	private val internalLoginAuditRepository: InternalLoginAuditRepository,
	private val idGenerator: IdGenerator,
	private val clock: Clock,
) {
	fun execute(command: AuthenticateInternalUserCommand): AuthenticateInternalUserResult {
		val now = clock.instant()

		val internalUser = internalUserRepository.findByLoginId(command.loginId)
		if (internalUser == null) {
			recordAudit(LoginOutcome.INVALID_CREDENTIALS, null, command, now)
			throw InvalidCredentialsException()
		}

		unlockIfLockExpired(internalUser, now)

		if (internalUser.status == AccountStatus.LOCKED) {
			recordAudit(LoginOutcome.LOCKED, internalUser, command, now)
			throw AccountLockedException(requireNotNull(internalUser.lockedUntil))
		}
		if (internalUser.status != AccountStatus.ACTIVE) {
			recordAudit(LoginOutcome.INVALID_CREDENTIALS, internalUser, command, now)
			throw InvalidCredentialsException()
		}

		val passwordHash = internalUser.passwordHash
		if (passwordHash == null || !passwordEncoder.matches(command.password, passwordHash)) {
			recordFailureAndMaybeLock(internalUser, now)
			recordAudit(LoginOutcome.INVALID_CREDENTIALS, internalUser, command, now)
			throw InvalidCredentialsException()
		}

		internalUser.recordSuccessfulLogin(now)
		internalUserRepository.save(internalUser)
		recordAudit(LoginOutcome.SUCCESS, internalUser, command, now)

		return AuthenticateInternalUserResult(
			internalUserId = internalUser.id,
			loginId = internalUser.loginId,
			userName = internalUser.userName,
			role = internalUser.role,
		)
	}

	/** 로그인 시도 하나를 감사 로그에 남긴다. 계정을 찾지 못한 시도는 [internalUser]가 `null`이다. */
	private fun recordAudit(
		outcome: LoginOutcome,
		internalUser: InternalUser?,
		command: AuthenticateInternalUserCommand,
		occurredAt: Instant,
	) {
		internalLoginAuditRepository.append(
			InternalLoginAudit(
				id = InternalLoginAuditId("ila_" + idGenerator.newId()),
				internalUserId = internalUser?.id,
				attemptedLoginId = command.loginId,
				outcome = outcome,
				clientIp = command.clientIp,
				occurredAt = occurredAt,
			),
		)
	}

	/** 잠금 시각이 지났으면 즉시 풀어서, 이번 요청의 비밀번호가 맞다면 바로 로그인할 수 있게 한다. */
	private fun unlockIfLockExpired(
		internalUser: InternalUser,
		now: Instant,
	) {
		if (LoginLockoutPolicy.isLockExpired(internalUser.status, internalUser.lockedUntil, now)) {
			internalUser.unlock(now)
		}
	}

	private fun recordFailureAndMaybeLock(
		internalUser: InternalUser,
		now: Instant,
	) {
		internalUser.recordFailedLogin(now)
		if (LoginLockoutPolicy.shouldLock(internalUser.failedLoginCount)) {
			internalUser.lock(LoginLockoutPolicy.lockUntil(now), now)
		}
		internalUserRepository.save(internalUser)
	}
}
