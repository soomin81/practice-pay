package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.identity.InternalUser
import paytech.practice.pay.domain.identity.LoginId

/**
 * [InternalUser] Aggregate를 저장·복원하는 Command Repository Outbound Port다.
 */
interface InternalUserRepository {
	/** InternalUser를 저장한다(신규 생성·상태 변경 모두 이 메서드로 반영한다). */
	fun save(internalUser: InternalUser)

	/** `login_id`로 InternalUser를 찾는다. 없으면 `null`이다. */
	fun findByLoginId(loginId: LoginId): InternalUser?
}
