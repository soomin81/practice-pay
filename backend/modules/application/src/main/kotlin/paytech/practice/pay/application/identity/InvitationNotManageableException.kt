package paytech.practice.pay.application.identity

/**
 * 초대를 재발송·취소할 수 없는 상태일 때 던진다 — inbound Adapter에서 `409 Conflict`로
 * 매핑한다(권한 문제가 아니라 **지금 상태에서 성립하지 않는 요청**이다).
 *
 * 두 경우를 덮는다:
 * - **대상이 `INVITED`가 아니다** — 이미 활성화됐거나 종료된 계정에 초대를 다시 보낼
 *   이유가 없다.
 * - **취소할 `PENDING` 초대가 없다** — 이미 취소됐거나 수락된 초대다.
 */
class InvitationNotManageableException(
	message: String,
) : RuntimeException(message)
