package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.identity.AccountInvitation

/**
 * [AccountInvitation] Aggregate를 저장·복원하는 Command Repository Outbound Port다.
 */
interface AccountInvitationRepository {
	/** AccountInvitation을 저장한다(신규 생성·상태 변경 모두 이 메서드로 반영한다). */
	fun save(accountInvitation: AccountInvitation)
}
