package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.MerchantUser
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.merchant.MerchantId

/**
 * [MerchantUser] Aggregate를 저장·복원하는 Command Repository Outbound Port다.
 */
interface MerchantUserRepository {
	/** MerchantUser를 저장한다(신규 생성·상태 변경 모두 이 메서드로 반영한다). */
	fun save(merchantUser: MerchantUser)

	/**
	 * `(merchant_seq, login_id)` 조합으로 MerchantUser를 찾는다. 없으면 `null`이다.
	 *
	 * `login_id`는 가맹점 안에서만 유일하므로(`backend/CLAUDE.md`의 "Idempotency keys")
	 * `loginId`만으로는 찾을 수 없다 — 반드시 [MerchantId]와 함께 조회한다.
	 */
	fun findByMerchantIdAndLoginId(
		merchantId: MerchantId,
		loginId: LoginId,
	): MerchantUser?

	/** `merchant_user_id`로 MerchantUser를 찾는다. 없으면 `null`이다. */
	fun findById(merchantUserId: MerchantUserId): MerchantUser?
}
