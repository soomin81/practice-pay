package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.identity.AccountInvitation
import paytech.practice.pay.domain.identity.MerchantUserId

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

	/**
	 * 주어진 가맹점 사용자의 **`PENDING` 상태 초대**를 찾는다. 없으면 `null`이다 —
	 * 초대 재발송·취소가 대상 초대를 찾을 때 쓴다.
	 *
	 * **사용자당 `PENDING` 초대가 최대 하나라는 것은 DB 제약이 아니라 우리 로직이
	 * 지키는 규약이다** — 재발송이 항상 기존 `PENDING`을 `REVOKED`로 만든 뒤 새로
	 * 만들기 때문이다(`ResendMerchantUserInvitationUseCase`, 두 쓰기가 한 트랜잭션).
	 * `account_invitation`에는 `(merchant_user_seq, invitation_status)` UNIQUE가 없다.
	 * 그래서 구현체는 둘 이상이 있어도 터지지 않게 **가장 최근 것 하나**를 돌려준다.
	 */
	fun findPendingByMerchantUserId(merchantUserId: MerchantUserId): AccountInvitation?
}
