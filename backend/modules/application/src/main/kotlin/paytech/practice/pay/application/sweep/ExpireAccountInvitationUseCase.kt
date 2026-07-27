package paytech.practice.pay.application.sweep

import paytech.practice.pay.application.port.outbound.AccountInvitationRepository
import paytech.practice.pay.domain.identity.AccountInvitationStatus

/**
 * 만료 시각이 지난 `PENDING` 초대를 `EXPIRED`로 전이시키는 Sweep Use Case다
 * (`AccountInvitation.expire()`의 호출부 — 지금까지 도메인에 전이는 있지만 부르는 곳이
 * 없어 만료 판단을 화면이 `expiresAt` 비교로 대신했다).
 *
 * **후보 목록의 애그리게이트를 그대로 전이시키지 않고 식별자로 다시 읽는다.** 후보를 뽑은
 * 시점과 처리 시점 사이에 그 초대가 수락(`ACCEPTED`)·취소(`REVOKED`)됐을 수 있어서다 —
 * 다시 읽어 여전히 `PENDING`일 때만 만료시킨다(재검증이 동시성 안전장치다). 이미 다른
 * 상태면 조용히 지나간다(Sweep은 최선을 다하는 정리라 그게 정상 경로다).
 *
 * 단일 애그리게이트만 다루므로 트랜잭션 경계가 필요 없다. `expire()`는 다른 두 만료
 * 대상(`Payment`/`CheckoutSession`)과 달리 타임스탬프를 받지 않는다 — `account_invitation`
 * 테이블에 `updated_at`이 없어서다.
 */
class ExpireAccountInvitationUseCase(
	private val accountInvitationRepository: AccountInvitationRepository,
) {
	fun execute(command: ExpireAccountInvitationCommand) {
		val invitation = accountInvitationRepository.findById(command.accountInvitationId) ?: return
		if (invitation.status != AccountInvitationStatus.PENDING) return

		invitation.expire()
		accountInvitationRepository.save(invitation)
	}
}
