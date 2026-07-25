package paytech.practice.pay.application.identity

import paytech.practice.pay.application.port.outbound.InternalUserRepository
import paytech.practice.pay.domain.identity.AccountStatus
import paytech.practice.pay.domain.identity.InternalUser
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.InternalUserRole

/**
 * [ChangeInternalUserStatusUseCase]와 [ChangeInternalUserRoleUseCase]가 공유하는 접근
 * 판단이다 — 가맹점 쪽 [MerchantUserManagementGuard]와 같은 이유로 한곳에 모았다(두
 * Use Case의 규칙이 완전히 같아서 복제하면 한쪽만 고쳐지는 방식으로 갈릴 수 있다).
 *
 * **요청자의 권한을 다시 확인하지 않는다 — 가맹점 쪽과 의도적으로 다른 지점이다.**
 * `apps:api-admin`은 인가를 `SecurityConfig`의 정적 규칙에 맡기는 앱이고
 * (`ListInternalUsersUseCase`/`IssueInternalUserCommand`의 KDoc), `/admin/internal-users`
 * 경로(하위 경로 와일드카드 포함)가 `SUPER_ADMIN` 전용이라 **이 코드가 실행된다는 것
 * 자체가 이미 SUPER_ADMIN 세션**이라는
 * 뜻이다. 가맹점 쪽이 `canInviteSubAccounts()`를 동적으로 확인한 이유(세션이 살아 있는
 * 동안 계정이 정지될 수 있다)는 여기에도 해당하지만, 그 판단을 앱 전체에서 일관되게
 * 바꾸는 것은 이 슬라이스의 범위를 넘는다 — 바꾼다면 `api-admin`의 모든 Use Case를 함께
 * 바꿔야 한다.
 *
 * 테넌시 확인도 없다 — 내부 운영자는 특정 가맹점에 속하지 않는다.
 */
internal object InternalUserManagementGuard {
	/**
	 * 대상을 읽고 관리 가능한지 판단한다.
	 *
	 * - 없으면 [InternalUserNotFoundException](404).
	 * - **자기 자신**이면 [InternalUserNotManageableException](403) — 그 예외의 KDoc 참고.
	 */
	fun loadManageableTarget(
		internalUserRepository: InternalUserRepository,
		targetInternalUserId: InternalUserId,
		requestedByInternalUserId: InternalUserId,
	): InternalUser {
		if (targetInternalUserId == requestedByInternalUserId) {
			throw InternalUserNotManageableException("자기 자신의 계정은 이 API로 변경할 수 없습니다.")
		}

		return internalUserRepository.findById(targetInternalUserId)
			?: throw InternalUserNotFoundException("InternalUser(${targetInternalUserId.value})를 찾을 수 없습니다.")
	}

	/**
	 * "최소 하나의 활성 SUPER_ADMIN을 유지한다"를 강제한다.
	 *
	 * 대상이 **활성 SUPER_ADMIN**일 때만 의미가 있다 — 그를 활성 SUPER_ADMIN 집합에서
	 * 빼는 연산(정지·종료·강등)이면 다른 활성 SUPER_ADMIN이 남는지 세어보고, 없으면 거부한다.
	 *
	 * **오늘의 API 조합으로는 이 거부가 실제로 발생하지 않는다** — 요청자는 항상 활성
	 * SUPER_ADMIN이고 자기 자신은 위에서 먼저 막히므로, 대상이 활성 SUPER_ADMIN이면 이미
	 * 둘이다. 그래도 두는 이유는 규칙이 `docs/`에 있고, 관리 권한 범위나 자기 자신 차단이
	 * 바뀌는 순간 곧바로 필요해지기 때문이다(가맹점 쪽 `requireAnotherActiveOwnerRemains`와
	 * 같은 상황이며 그 사실을 계약 문서에도 적어 뒀다).
	 */
	fun requireAnotherActiveSuperAdminRemains(
		internalUserRepository: InternalUserRepository,
		target: InternalUser,
	) {
		if (target.role != InternalUserRole.SUPER_ADMIN || target.status != AccountStatus.ACTIVE) return

		if (internalUserRepository.countActiveSuperAdmins() <= 1) {
			throw LastActiveSuperAdminException("마지막 활성 SUPER_ADMIN이라 변경할 수 없습니다.")
		}
	}
}
