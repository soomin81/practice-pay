package paytech.practice.pay.application.identity

import paytech.practice.pay.application.port.outbound.MerchantUserRepository
import java.time.Clock

/**
 * 내부 운영자 콘솔에서 **임의 가맹점의** 사용자를 정지·재개·종료하는 Use Case다
 * (`docs/architecture/admin-console-api.md`의 4절: `POST /admin/merchants/{id}/users/{id}/...`).
 *
 * merchant-side [ChangeMerchantUserStatusUseCase]와 규칙의 뼈대는 같지만(정지·재개·종료를
 * 하나로 두고, "최소 1 활성 OWNER" 불변식이 정지·종료에만 걸리며, 상태 전이 유효성은
 * 도메인이 막는다), **요청자 기반 검사가 전부 없다** — 행위자가 `InternalUser`라
 * `MerchantUser` 요청자를 다시 읽어 권한·자기 자신·ADMIN→OWNER를 확인할 대상이 없다.
 * 인가는 `SecurityConfig`가 `SUPER_ADMIN`/`OPERATOR`로 정적으로 판단한다.
 *
 * **이 경로가 "최소 하나의 활성 OWNER를 유지한다" 불변식이 실제 HTTP로 트리거되는 첫
 * 지점이다** — merchant-side에서는 요청자 자기 자신 차단·ADMIN 제한 때문에 도달할 수 없어
 * 단위 테스트로만 검증되던 방어선이다(`backend/IMPLEMENTATION-NOTES.md`).
 */
class AdminChangeMerchantUserStatusUseCase(
	private val merchantUserRepository: MerchantUserRepository,
	private val clock: Clock,
) {
	fun execute(command: AdminChangeMerchantUserStatusCommand): ChangeMerchantUserStatusResult {
		val target =
			MerchantUserManagementGuard.loadTargetInMerchant(
				merchantUserRepository,
				command.merchantId,
				command.targetMerchantUserId,
			)

		// 재개는 활성 OWNER를 줄이지 않으므로 불변식 검사가 필요 없다.
		if (command.action != MerchantUserStatusAction.REACTIVATE) {
			MerchantUserManagementGuard.requireAnotherActiveOwnerRemains(merchantUserRepository, target)
		}

		val now = clock.instant()
		// 도메인 전이 호출만 감싼다 — 이 범위 밖의 IllegalStateException은 500으로 남아야 한다.
		try {
			when (command.action) {
				MerchantUserStatusAction.SUSPEND -> target.suspend(now)
				MerchantUserStatusAction.REACTIVATE -> target.reactivate(now)
				MerchantUserStatusAction.TERMINATE -> target.terminate(now)
			}
		} catch (ex: IllegalStateException) {
			throw InvalidMerchantUserTransitionException(ex.message ?: "허용되지 않는 상태 전이입니다.", ex)
		}

		merchantUserRepository.save(target)

		return ChangeMerchantUserStatusResult(
			merchantUserId = target.id,
			status = target.status,
			changedAt = now,
		)
	}
}
