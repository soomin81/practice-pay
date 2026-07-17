package paytech.practice.pay.domain.identity

/**
 * [AccountInvitation]의 상태를 표현한다.
 *
 * 정상 흐름: `PENDING → ACCEPTED`
 *
 * 예외: `PENDING → EXPIRED`, `PENDING → REVOKED`
 *
 * `ACCEPTED`, `EXPIRED`, `REVOKED`는 종료 상태다.
 *
 * @see docs/domain/state-transitions.md
 */
enum class AccountInvitationStatus {
	PENDING,
	ACCEPTED,
	EXPIRED,
	REVOKED,
}
