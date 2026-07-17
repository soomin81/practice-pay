package paytech.practice.pay.application.identity

import paytech.practice.pay.application.port.outbound.InternalUserRepository
import paytech.practice.pay.application.port.outbound.PasswordEncoder
import paytech.practice.pay.domain.identity.AccountStatus
import paytech.practice.pay.domain.identity.InternalUser
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * "내부 운영자 로그인" Use Case다(`docs/architecture/identity-access-api-key.md`의
 * "3.4 로그인 경로").
 *
 * [MAX_FAILED_LOGIN_ATTEMPTS]/[LOCK_DURATION]은 `docs/`에 값이 정해져 있지 않아
 * 이 Use Case가 상수로 고정했다 — [paytech.practice.pay.application.payment.CreatePaymentUseCase]의
 * `SPREAD_RATE`/`PAYMENT_VALIDITY`와 같은 성격의 MVP 단순화다.
 *
 * 계정이 존재하지 않거나, `ACTIVE`가 아니거나(잠금이 아직 안 풀린 `LOCKED`는
 * [AccountLockedException]으로 구분), 비밀번호가 틀리면 [InvalidCredentialsException]을
 * 던진다 — 어느 경우인지 호출부(결국 클라이언트)에 드러내지 않는다.
 */
class AuthenticateInternalUserUseCase(
	private val internalUserRepository: InternalUserRepository,
	private val passwordEncoder: PasswordEncoder,
	private val clock: Clock,
) {
	fun execute(command: AuthenticateInternalUserCommand): AuthenticateInternalUserResult {
		val internalUser =
			internalUserRepository.findByLoginId(command.loginId)
				?: throw InvalidCredentialsException()

		val now = clock.instant()

		unlockIfLockExpired(internalUser, now)

		if (internalUser.status == AccountStatus.LOCKED) {
			throw AccountLockedException(requireNotNull(internalUser.lockedUntil))
		}
		if (internalUser.status != AccountStatus.ACTIVE) {
			throw InvalidCredentialsException()
		}

		val passwordHash = internalUser.passwordHash
		if (passwordHash == null || !passwordEncoder.matches(command.password, passwordHash)) {
			recordFailureAndMaybeLock(internalUser, now)
			throw InvalidCredentialsException()
		}

		internalUser.recordSuccessfulLogin(now)
		internalUserRepository.save(internalUser)

		return AuthenticateInternalUserResult(
			internalUserId = internalUser.id,
			loginId = internalUser.loginId,
			userName = internalUser.userName,
			role = internalUser.role,
		)
	}

	/** 잠금 시각이 지났으면 즉시 풀어서, 이번 요청의 비밀번호가 맞다면 바로 로그인할 수 있게 한다. */
	private fun unlockIfLockExpired(
		internalUser: InternalUser,
		now: Instant,
	) {
		val lockedUntil = internalUser.lockedUntil
		if (internalUser.status == AccountStatus.LOCKED && lockedUntil != null && !now.isBefore(lockedUntil)) {
			internalUser.unlock(now)
		}
	}

	private fun recordFailureAndMaybeLock(
		internalUser: InternalUser,
		now: Instant,
	) {
		internalUser.recordFailedLogin(now)
		if (internalUser.failedLoginCount >= MAX_FAILED_LOGIN_ATTEMPTS) {
			internalUser.lock(now.plus(LOCK_DURATION), now)
		}
		internalUserRepository.save(internalUser)
	}

	companion object {
		private const val MAX_FAILED_LOGIN_ATTEMPTS = 5
		private val LOCK_DURATION: Duration = Duration.ofMinutes(15)
	}
}
