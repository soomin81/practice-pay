package paytech.practice.pay.application.identity

import paytech.practice.pay.domain.identity.AccountStatus
import java.time.Duration
import java.time.Instant

/**
 * 로그인 실패 누적 잠금 정책이다 — [AuthenticateInternalUserUseCase]와
 * [AuthenticateMerchantUserUseCase]가 공유한다.
 *
 * 원래 두 Use Case가 각자 companion object에 **같은 상수와 같은 판단**을 복제하고 있었다.
 * 값이 갈리면(예: 한쪽만 10회로 바꾸면) 내부 운영자와 가맹점 관리자의 잠금 기준이 조용히
 * 달라지는데, 그건 이 프로젝트가 의도한 차이가 아니다 — 두 자격증명 영역은 생명주기가
 * 다를 뿐(ADR-006) 무차별 대입 방어 기준까지 다를 이유가 없다.
 *
 * **[MAX_FAILED_LOGIN_ATTEMPTS]/[LOCK_DURATION]은 `docs/`에 값이 정해져 있지 않아 여기서
 * 고정한 MVP 상수다**(`CreatePaymentUseCase`의 `SPREAD_RATE`/`PAYMENT_VALIDITY`와 같은 성격).
 *
 * Use Case가 아니라 두 Use Case가 함께 쓰는 순수 함수 묶음이라
 * `ApplicationPurityTest`의 "Use Case는 다른 Use Case를 호출하지 않는다"를 지킨다
 * ([MerchantUserManagementGuard]와 같은 자리·같은 성격).
 */
internal object LoginLockoutPolicy {
	/** 이 횟수만큼 연속 실패하면 계정을 잠근다. */
	const val MAX_FAILED_LOGIN_ATTEMPTS = 5

	/** 잠금 유지 기간. 이 시간이 지나면 다음 로그인 시도에서 자동으로 풀린다([isLockExpired]). */
	val LOCK_DURATION: Duration = Duration.ofMinutes(15)

	/**
	 * 잠긴 계정의 잠금 시각이 지났는지 판단한다 — `true`면 호출부가 애그리게이트를 즉시
	 * 풀어서(`unlock`) 이번 요청의 비밀번호가 맞다면 바로 로그인할 수 있게 한다.
	 *
	 * 애그리게이트 타입(`InternalUser`/`MerchantUser`)이 달라 상태 값만 받는다 —
	 * [AccountStatus]는 두 영역이 공유하는 값이라 이 판단은 하나로 둘 수 있다.
	 */
	fun isLockExpired(
		status: AccountStatus,
		lockedUntil: Instant?,
		now: Instant,
	): Boolean = status == AccountStatus.LOCKED && lockedUntil != null && !now.isBefore(lockedUntil)

	/** 누적 실패 횟수가 잠금 기준에 도달했는지 판단한다. */
	fun shouldLock(failedLoginCount: Int): Boolean = failedLoginCount >= MAX_FAILED_LOGIN_ATTEMPTS

	/** 지금 잠근다면 언제까지 잠글지 계산한다. */
	fun lockUntil(now: Instant): Instant = now.plus(LOCK_DURATION)
}
