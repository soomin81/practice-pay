package paytech.practice.pay.application.identity

import paytech.practice.pay.application.port.outbound.InternalUserRepository
import java.time.Clock

/**
 * "내부 운영자 계정 상태 변경" Use Case다 — 정지(`SUSPEND`), 재개(`REACTIVATE`),
 * 종료(`TERMINATE`)를 [InternalUserStatusAction] 하나로 받는다.
 *
 * **세 동작을 Use Case 셋으로 쪼개지 않았다** — 가맹점 쪽 [ChangeMerchantUserStatusUseCase]와
 * 같은 이유다: 접근 판단과 "최소 1 활성 SUPER_ADMIN" 불변식이 완전히 동일하고 마지막에
 * 부르는 도메인 메서드 하나만 다르다. 특히 그 불변식이 **셋 중 둘**(정지·종료)에만 걸려서
 * 복제하면 빠뜨리기 쉬운 종류의 규칙이다.
 *
 * 상태 전이 자체의 유효성(예: `SUSPENDED`가 아닌 계정을 재개하려는 시도)은 도메인
 * 애그리게이트가 막는다 — 여기서 다시 검사하지 않고, 그 예외만
 * [InvalidInternalUserTransitionException]으로 바꾼다.
 */
class ChangeInternalUserStatusUseCase(
	private val internalUserRepository: InternalUserRepository,
	private val clock: Clock,
) {
	fun execute(command: ChangeInternalUserStatusCommand): ChangeInternalUserStatusResult {
		val target =
			InternalUserManagementGuard.loadManageableTarget(
				internalUserRepository,
				command.targetInternalUserId,
				command.requestedByInternalUserId,
			)

		// 재개는 활성 SUPER_ADMIN을 줄이지 않으므로 불변식 검사가 필요 없다.
		if (command.action != InternalUserStatusAction.REACTIVATE) {
			InternalUserManagementGuard.requireAnotherActiveSuperAdminRemains(internalUserRepository, target)
		}

		val now = clock.instant()
		// 도메인 전이 호출만 감싼다 — 이 범위 밖의 IllegalStateException은 500으로 남아야 한다.
		try {
			when (command.action) {
				InternalUserStatusAction.SUSPEND -> target.suspend(now)
				InternalUserStatusAction.REACTIVATE -> target.reactivate(now)
				InternalUserStatusAction.TERMINATE -> target.terminate(now)
			}
		} catch (ex: IllegalStateException) {
			throw InvalidInternalUserTransitionException(ex.message ?: "허용되지 않는 상태 전이입니다.", ex)
		}

		internalUserRepository.save(target)

		return ChangeInternalUserStatusResult(
			internalUserId = target.id,
			status = target.status,
			changedAt = now,
		)
	}
}
