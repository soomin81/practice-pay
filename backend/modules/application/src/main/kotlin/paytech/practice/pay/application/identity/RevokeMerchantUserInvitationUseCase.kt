package paytech.practice.pay.application.identity

import paytech.practice.pay.application.port.outbound.AccountInvitationRepository
import paytech.practice.pay.application.port.outbound.MerchantUserRepository
import java.time.Clock

/**
 * "초대 취소" Use Case다. 대상의 `PENDING` 초대를 [AccountInvitation.revoke][paytech.practice.pay.domain.identity.AccountInvitation.revoke]로
 * 무효화해서 그 링크로는 더 이상 계정을 활성화할 수 없게 만든다.
 *
 * **계정 자체는 건드리지 않는다 — `INVITED`로 남는다.** 취소와 종료를 묶지 않은 이유는
 * 종료가 되돌릴 수 없는 동작인데 "초대 취소"라는 가벼운 이름 뒤에 숨으면 위험하기
 * 때문이다(계정을 없애려면 [ChangeMerchantUserStatusUseCase]의 `TERMINATE`를 쓴다).
 * 대신 명부의 `pendingInvitationExpiresAt`이 `null`이 되어 "유효한 초대 없음"이 드러나므로
 * 방치되지 않는다 — 이 판단은 `docs/architecture/merchant-console-api.md`에도 적어 뒀다.
 *
 * 접근 판단은 [ResendMerchantUserInvitationUseCase]와 같다([MerchantUserManagementGuard]).
 */
class RevokeMerchantUserInvitationUseCase(
	private val merchantUserRepository: MerchantUserRepository,
	private val accountInvitationRepository: AccountInvitationRepository,
	private val clock: Clock,
) {
	fun execute(command: RevokeMerchantUserInvitationCommand): RevokeMerchantUserInvitationResult {
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

		val invitation =
			accountInvitationRepository.findPendingByMerchantUserId(target.id)
				?: throw InvitationNotManageableException(
					"MerchantUser(${target.id.value})에게 취소할 수 있는 초대가 없습니다.",
				)

		invitation.revoke()
		accountInvitationRepository.save(invitation)

		return RevokeMerchantUserInvitationResult(merchantUserId = target.id, revokedAt = clock.instant())
	}
}
