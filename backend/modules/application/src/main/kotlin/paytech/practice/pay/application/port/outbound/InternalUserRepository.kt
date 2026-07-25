package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalUser
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.LoginId

/**
 * [InternalUser] Aggregate를 저장·복원하는 Command Repository Outbound Port다.
 */
interface InternalUserRepository {
	/** InternalUser를 저장한다(신규 생성·상태 변경 모두 이 메서드로 반영한다). */
	fun save(internalUser: InternalUser)

	/** `login_id`로 InternalUser를 찾는다. 없으면 `null`이다. */
	fun findByLoginId(loginId: LoginId): InternalUser?

	/** `email`로 InternalUser를 찾는다. 없으면 `null`이다. */
	fun findByEmail(email: Email): InternalUser?

	/** `internal_user_id`로 InternalUser를 찾는다. 없으면 `null`이다. */
	fun findById(internalUserId: InternalUserId): InternalUser?

	/**
	 * **`ACTIVE` 상태인 `SUPER_ADMIN` 수**를 센다.
	 *
	 * "내부 운영자에는 최소 하나의 활성 `SUPER_ADMIN`이 항상 존재해야 한다"
	 * (`docs/architecture/identity-access-api-key.md`의 "3.3")를 강제하기 위한 조회다 —
	 * 마지막 활성 SUPER_ADMIN을 정지·종료·강등하려는 요청을 거부할 때 쓴다. 내부 계정
	 * 발급은 SUPER_ADMIN만 할 수 있어서, 그가 사라지면 아무도 계정을 만들 수 없는 상태로
	 * 굳는다(복구는 Bootstrap 같은 운영 절차뿐이다).
	 *
	 * [MerchantUserRepository.countActiveOwners]와 같은 성격의 **도메인 규칙 보조 조회**라
	 * Projection이 아니라 Command Repository에 둔다. 내부 운영자는 가맹점에 속하지 않으므로
	 * 범위 인자가 없다는 점만 다르다.
	 */
	fun countActiveSuperAdmins(): Int
}
