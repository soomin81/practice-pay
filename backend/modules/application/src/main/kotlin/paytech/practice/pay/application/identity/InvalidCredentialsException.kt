package paytech.practice.pay.application.identity

/**
 * 로그인 아이디가 없거나, 계정이 `ACTIVE`가 아니거나(`LOCKED`이면서 잠금이 아직
 * 풀리지 않은 경우는 [AccountLockedException]로 구분한다), 비밀번호가 일치하지
 * 않을 때 던진다.
 *
 * 세 경우를 하나의 예외로 묶는다 — 로그인 아이디 존재 여부나 계정 상태를 호출부에
 * 노출하지 않기 위해서다(흔한 로그인 실패 처리 관례).
 */
class InvalidCredentialsException : RuntimeException("로그인 아이디 또는 비밀번호가 올바르지 않습니다.")
