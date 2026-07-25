package paytech.practice.pay.application.identity

import paytech.practice.pay.application.port.outbound.InternalUserRepository
import java.time.Clock

/**
 * "내부 운영자 역할 변경" Use Case다 — 가맹점 쪽 [ChangeMerchantUserRoleUseCase]와 같은
 * 모양이다.
 *
 * 접근 판단과 "최소 1 활성 SUPER_ADMIN" 불변식은 [ChangeInternalUserStatusUseCase]와
 * [InternalUserManagementGuard]로 공유하고, 여기에 역할 고유의 규칙 둘이 더해진다:
 *
 * - **`SUPER_ADMIN`으로 승격할 수 없다** — 도메인([InternalUser.changeRole][paytech.practice.pay.domain.identity.InternalUser.changeRole])이
 *   `IllegalArgumentException`으로 막으므로 여기서 다시 검사하지 않는다(400으로 매핑된다).
 * - **마지막 활성 SUPER_ADMIN은 강등할 수 없다** — 정지·종료와 같은 불변식이다. 강등은
 *   대상을 활성 SUPER_ADMIN 집합에서 빼는 연산이므로
 *   [InternalUserManagementGuard.requireAnotherActiveSuperAdminRemains]가 그대로 적용된다.
 */
class ChangeInternalUserRoleUseCase(
	private val internalUserRepository: InternalUserRepository,
	private val clock: Clock,
) {
	fun execute(command: ChangeInternalUserRoleCommand): ChangeInternalUserRoleResult {
		val target =
			InternalUserManagementGuard.loadManageableTarget(
				internalUserRepository,
				command.targetInternalUserId,
				command.requestedByInternalUserId,
			)

		// 대상이 활성 SUPER_ADMIN이면 강등이므로(승격은 도메인이 막는다) 불변식을 확인한다.
		InternalUserManagementGuard.requireAnotherActiveSuperAdminRemains(internalUserRepository, target)

		val now = clock.instant()
		// 도메인 전이 호출만 감싼다(종료된 계정의 역할 변경 시도 → 409). SUPER_ADMIN 승격
		// 시도는 IllegalArgumentException이라 여기 걸리지 않고 그대로 400이 된다.
		try {
			target.changeRole(command.newRole, now)
		} catch (ex: IllegalStateException) {
			throw InvalidInternalUserTransitionException(ex.message ?: "허용되지 않는 역할 변경입니다.", ex)
		}
		internalUserRepository.save(target)

		return ChangeInternalUserRoleResult(
			internalUserId = target.id,
			role = target.role,
			changedAt = now,
		)
	}
}
