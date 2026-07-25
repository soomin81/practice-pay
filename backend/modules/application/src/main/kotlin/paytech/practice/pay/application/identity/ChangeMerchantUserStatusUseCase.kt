package paytech.practice.pay.application.identity

import paytech.practice.pay.application.port.outbound.MerchantUserRepository
import java.time.Clock

/**
 * "가맹점 사용자 계정 상태 변경" Use Case다 — 정지(`SUSPEND`), 재개(`REACTIVATE`),
 * 종료(`TERMINATE`)를 [MerchantUserStatusAction] 하나로 받는다
 * (`docs/architecture/identity-access-api-key.md`의 "5. 계정 상태").
 *
 * **세 동작을 Use Case 셋으로 쪼개지 않았다 — 이 저장소의 "동작마다 Use Case 하나"
 * 관행에서 의도적으로 벗어난 지점이다.** 셋은 권한 확인·테넌시 확인·"최소 1 활성
 * OWNER" 불변식이 **완전히 동일**하고 마지막에 부르는 도메인 메서드 하나만 다르다
 * (`IssueMerchantApiKeyUseCase`/`RevokeMerchantApiKeyUseCase`가 별도인 것은 그 둘이
 * 입력도 규칙도 실제로 다른 연산이기 때문이다). 셋으로 복제하면 규칙이 한쪽만
 * 고쳐지는 방식으로 갈릴 위험만 생긴다 — 특히 위 불변식은 **셋 중 둘**(정지·종료)에
 * 걸려서, 복제된 코드에서 빠뜨리기 쉬운 종류의 규칙이다.
 *
 * 상태 전이 자체의 유효성(예: `SUSPENDED`가 아닌 계정을 재개하려는 시도)은 도메인
 * 애그리게이트가 `IllegalStateException`으로 막는다 — 여기서 다시 검사하지 않는다.
 *
 * 접근 판단(요청자 권한, 자기 자신 차단, ADMIN→OWNER 차단, 마지막 OWNER 보호)은
 * [MerchantUserManagementGuard]가 [ChangeMerchantUserRoleUseCase]와 공유한다.
 */
class ChangeMerchantUserStatusUseCase(
	private val merchantUserRepository: MerchantUserRepository,
	private val clock: Clock,
) {
	fun execute(command: ChangeMerchantUserStatusCommand): ChangeMerchantUserStatusResult {
		val requester =
			MerchantUserManagementGuard.loadAuthorizedRequester(
				merchantUserRepository,
				command.requestedByMerchantUserId,
			)
		val target =
			MerchantUserManagementGuard.loadManageableTarget(
				merchantUserRepository,
				requester,
				command.targetMerchantUserId,
			)

		// 재개는 활성 OWNER를 줄이지 않으므로 불변식 검사가 필요 없다.
		if (command.action != MerchantUserStatusAction.REACTIVATE) {
			MerchantUserManagementGuard.requireAnotherActiveOwnerRemains(merchantUserRepository, target)
		}

		val now = clock.instant()
		// 도메인 전이 호출만 감싼다 — 이 범위 밖의 IllegalStateException(checkNotNull 등)은
		// 여전히 500으로 남아야 한다(InvalidMerchantUserTransitionException의 KDoc 참고).
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
