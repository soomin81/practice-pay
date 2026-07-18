package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.identity.AccountInvitation

/**
 * [AccountInvitation] Aggregate를 저장·복원하는 Command Repository Outbound Port다.
 */
interface AccountInvitationRepository {
	/** AccountInvitation을 저장한다(신규 생성·상태 변경 모두 이 메서드로 반영한다). */
	fun save(accountInvitation: AccountInvitation)

	/**
	 * `token_hash`로 AccountInvitation을 찾는다. 없으면 `null`이다.
	 *
	 * `account_invitation.token_hash`가 이미 `UNIQUE` 인덱스라(`uk_account_invitation_token_hash`)
	 * `MerchantApiKey`의 Prefix→Hash 2단계 조회와 달리 곧바로 정확히 일치하는 값으로
	 * 조회한다 — 호출부(수락 Use Case)가 원문 Token을 [InvitationTokenHasher]로
	 * 해시한 값을 넘긴다.
	 */
	fun findByTokenHash(tokenHash: String): AccountInvitation?
}
