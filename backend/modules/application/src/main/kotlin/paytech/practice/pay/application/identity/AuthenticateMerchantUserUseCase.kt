package paytech.practice.pay.application.identity

import paytech.practice.pay.application.port.outbound.MerchantRepository
import paytech.practice.pay.application.port.outbound.MerchantUserRepository
import paytech.practice.pay.application.port.outbound.PasswordEncoder
import paytech.practice.pay.domain.identity.AccountStatus
import paytech.practice.pay.domain.identity.MerchantUser
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * "가맹점 관리자 로그인" Use Case다(`docs/architecture/identity-access-api-key.md`의
 * "4.5 로그인 경로").
 *
 * [AuthenticateInternalUserUseCase]와 거의 같은 모양이다 — 계정 상태 확인, 잠금
 * 정책([MAX_FAILED_LOGIN_ATTEMPTS]/[LOCK_DURATION], 같은 값), 자격증명 실패를
 * 하나의 [InvalidCredentialsException]으로 묶는 것까지 동일하다. 다른 점은
 * `login_id`가 가맹점 안에서만 유일해서([MerchantUserRepository][paytech.practice.pay.application.port.outbound.MerchantUserRepository]의
 * KDoc 참고) 먼저 [MerchantCode][paytech.practice.pay.domain.merchant.MerchantCode]로
 * 가맹점을 확정해야 한다는 것뿐이다 — 가맹점을 못 찾아도 같은
 * [InvalidCredentialsException]을 던진다(가맹점 코드 존재 여부도 노출하지 않는다).
 *
 * 가맹점 자체의 상태(`Merchant.status`)는 로그인 가능 여부를 결정하지 않는다 —
 * 가맹점이 `SUSPENDED`여도 관리자는 이유를 확인하러 로그인할 수 있어야 한다는
 * 판단이다(문서에 명시된 규칙은 아니다).
 */
class AuthenticateMerchantUserUseCase(
	private val merchantRepository: MerchantRepository,
	private val merchantUserRepository: MerchantUserRepository,
	private val passwordEncoder: PasswordEncoder,
	private val clock: Clock,
) {
	fun execute(command: AuthenticateMerchantUserCommand): AuthenticateMerchantUserResult {
		val merchant =
			merchantRepository.findByCode(command.merchantCode)
				?: throw InvalidCredentialsException()

		val merchantUser =
			merchantUserRepository.findByMerchantIdAndLoginId(merchant.id, command.loginId)
				?: throw InvalidCredentialsException()

		val now = clock.instant()

		unlockIfLockExpired(merchantUser, now)

		if (merchantUser.status == AccountStatus.LOCKED) {
			throw AccountLockedException(requireNotNull(merchantUser.lockedUntil))
		}
		if (merchantUser.status != AccountStatus.ACTIVE) {
			throw InvalidCredentialsException()
		}

		val passwordHash = merchantUser.passwordHash
		if (passwordHash == null || !passwordEncoder.matches(command.password, passwordHash)) {
			recordFailureAndMaybeLock(merchantUser, now)
			throw InvalidCredentialsException()
		}

		merchantUser.recordSuccessfulLogin(now)
		merchantUserRepository.save(merchantUser)

		return AuthenticateMerchantUserResult(
			merchantUserId = merchantUser.id,
			merchantId = merchantUser.merchantId,
			loginId = merchantUser.loginId,
			userName = merchantUser.userName,
			role = merchantUser.role,
		)
	}

	private fun unlockIfLockExpired(
		merchantUser: MerchantUser,
		now: Instant,
	) {
		val lockedUntil = merchantUser.lockedUntil
		if (merchantUser.status == AccountStatus.LOCKED && lockedUntil != null && !now.isBefore(lockedUntil)) {
			merchantUser.unlock(now)
		}
	}

	private fun recordFailureAndMaybeLock(
		merchantUser: MerchantUser,
		now: Instant,
	) {
		merchantUser.recordFailedLogin(now)
		if (merchantUser.failedLoginCount >= MAX_FAILED_LOGIN_ATTEMPTS) {
			merchantUser.lock(now.plus(LOCK_DURATION), now)
		}
		merchantUserRepository.save(merchantUser)
	}

	companion object {
		private const val MAX_FAILED_LOGIN_ATTEMPTS = 5
		private val LOCK_DURATION: Duration = Duration.ofMinutes(15)
	}
}
