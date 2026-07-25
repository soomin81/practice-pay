package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.identity.Email
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

	/**
	 * `(merchant_seq, email)` 조합으로 MerchantUser를 찾는다. 없으면 `null`이다.
	 *
	 * `email`도 `login_id`와 마찬가지로 가맹점 안에서만 유일하다(`uk_merchant_user_email`,
	 * `backend/CLAUDE.md`의 "Idempotency keys") — [InviteMerchantSubAccountUseCase]의
	 * 하위 계정 이메일 중복 확인에 쓴다.
	 */
	fun findByMerchantIdAndEmail(
		merchantId: MerchantId,
		email: Email,
	): MerchantUser?

	/** `merchant_user_id`로 MerchantUser를 찾는다. 없으면 `null`이다. */
	fun findById(merchantUserId: MerchantUserId): MerchantUser?

	/**
	 * 주어진 가맹점의 **`ACTIVE` 상태인 `OWNER` 수**를 센다.
	 *
	 * `docs/domain/domain-model.md`의 "최소 하나의 활성 OWNER를 유지한다" 불변식을
	 * 강제하기 위한 조회다 — 마지막 활성 OWNER를 정지·종료·강등하려는 요청을 거부할
	 * 때 쓴다. 이 판단은 같은 가맹점의 *다른* 사용자를 봐야 알 수 있어서 애그리게이트가
	 * 혼자 할 수 없다(애그리게이트는 다른 애그리게이트를 모른다).
	 *
	 * 목록 화면용 복잡 조회가 아니라 **도메인 규칙 보조 조회**라 Projection이 아니라
	 * 여기(Command Repository)에 둔다 — [findByMerchantIdAndLoginId]/[findByMerchantIdAndEmail]이
	 * 중복 검사를 위해 이미 같은 자리에 있는 것과 같은 성격이다.
	 */
	fun countActiveOwners(merchantId: MerchantId): Int
}
