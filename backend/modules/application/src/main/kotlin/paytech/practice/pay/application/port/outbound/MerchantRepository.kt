package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.merchant.Merchant
import paytech.practice.pay.domain.merchant.MerchantCode
import paytech.practice.pay.domain.merchant.MerchantId

/**
 * [Merchant] Aggregate를 저장·복원하는 Command Repository Outbound Port다.
 */
interface MerchantRepository {
	/** Merchant를 저장한다(신규 등록·상태 변경 모두 이 메서드로 반영한다). */
	fun save(merchant: Merchant)

	/** `merchant_id`로 Merchant를 복원한다. 없으면 `null`이다. */
	fun findById(merchantId: MerchantId): Merchant?

	/**
	 * `merchant_code`로 Merchant를 복원한다. 없으면 `null`이다.
	 *
	 * 가맹점 관리자 로그인(`AuthenticateMerchantUserUseCase`)이 쓴다 — `login_id`는
	 * 가맹점 안에서만 유일해서(`merchant_seq + login_id`, `backend/CLAUDE.md`의
	 * "Idempotency keys" 참고) 로그인 폼이 어느 가맹점인지부터 사람이 읽을 수 있는
	 * 코드로 밝혀야 한다.
	 */
	fun findByCode(merchantCode: MerchantCode): Merchant?
}
