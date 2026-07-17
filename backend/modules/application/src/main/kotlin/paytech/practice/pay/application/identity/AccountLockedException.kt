package paytech.practice.pay.application.identity

import java.time.Instant

/**
 * 계정이 `LOCKED` 상태이고 잠금이 아직 풀리지 않았을 때 던진다.
 *
 * [InvalidCredentialsException]과 구분하는 이유: 로그인 실패를 반복해서 잠긴
 * 정당한 사용자에게는 "잠겼고 언제 풀리는지"를 알려주는 게 일반적인 UX라서다 —
 * 로그인 아이디 자체가 존재하지 않거나 계정이 초대/정지/종료 상태인 경우와는
 * 다르게 취급한다.
 */
class AccountLockedException(
	val lockedUntil: Instant,
) : RuntimeException("계정이 잠겼습니다. $lockedUntil 이후에 다시 시도하세요.")
