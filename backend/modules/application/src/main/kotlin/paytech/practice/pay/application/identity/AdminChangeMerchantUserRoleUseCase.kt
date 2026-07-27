package paytech.practice.pay.application.identity

import paytech.practice.pay.application.port.outbound.MerchantUserRepository
import java.time.Clock

/**
 * 내부 운영자 콘솔에서 **임의 가맹점의** 사용자 역할을 바꾸는 Use Case다.
 *
 * merchant-side [ChangeMerchantUserRoleUseCase]와 규칙의 뼈대는 같지만 요청자 기반 검사가
 * 없다([AdminChangeMerchantUserStatusUseCase]의 KDoc 참고). 역할 고유의 규칙 둘은 그대로다:
 *
 * - **`OWNER`로 승격할 수 없다** — 도메인([MerchantUser.changeRole][paytech.practice.pay.domain.identity.MerchantUser.changeRole])이
 *   `IllegalArgumentException`으로 막으므로 여기서 다시 검사하지 않는다(400으로 매핑된다).
 * - **마지막 활성 OWNER는 강등할 수 없다** — 강등은 대상을 활성 OWNER 집합에서 빼는
 *   연산이라 [MerchantUserManagementGuard.requireAnotherActiveOwnerRemains]가 그대로 적용된다.
 */
class AdminChangeMerchantUserRoleUseCase(
	private val merchantUserRepository: MerchantUserRepository,
	private val clock: Clock,
) {
	fun execute(command: AdminChangeMerchantUserRoleCommand): ChangeMerchantUserRoleResult {
		val target =
			MerchantUserManagementGuard.loadTargetInMerchant(
				merchantUserRepository,
				command.merchantId,
				command.targetMerchantUserId,
			)

		// 대상이 활성 OWNER면 강등이므로(승격은 도메인이 막는다) 불변식을 확인한다.
		MerchantUserManagementGuard.requireAnotherActiveOwnerRemains(merchantUserRepository, target)

		val now = clock.instant()
		// 도메인 전이 호출만 감싼다(종료된 계정의 역할 변경 → 409). OWNER 승격 시도는
		// IllegalArgumentException이라 여기 걸리지 않고 그대로 400이 된다.
		try {
			target.changeRole(command.newRole, now)
		} catch (ex: IllegalStateException) {
			throw InvalidMerchantUserTransitionException(ex.message ?: "허용되지 않는 역할 변경입니다.", ex)
		}
		merchantUserRepository.save(target)

		return ChangeMerchantUserRoleResult(
			merchantUserId = target.id,
			role = target.role,
			changedAt = now,
		)
	}
}
