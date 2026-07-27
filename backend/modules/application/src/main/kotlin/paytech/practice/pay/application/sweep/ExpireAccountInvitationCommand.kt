package paytech.practice.pay.application.sweep

import paytech.practice.pay.domain.identity.AccountInvitationId

/**
 * [ExpireAccountInvitationUseCase]의 입력이다. Sweep Worker가 만료 후보를 찾아 그 식별자를
 * 넘긴다 — Use Case가 식별자로 다시 읽어 상태를 재검증한다(그 KDoc 참고).
 */
data class ExpireAccountInvitationCommand(
	val accountInvitationId: AccountInvitationId,
)
