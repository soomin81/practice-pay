package paytech.practice.pay.application.identity

import paytech.practice.pay.application.port.outbound.MerchantUserListProjection
import paytech.practice.pay.application.port.outbound.MerchantUserRepository

/**
 * "가맹점 사용자 목록 조회" Use Case다 — 콘솔의 팀 계정 화면이 누가 소속돼 있고
 * 누가 아직 `INVITED`로 남아 있는지 보여주기 위해 쓴다.
 *
 * **권한 판단은 [ListMerchantApiKeysUseCase][paytech.practice.pay.application.apikey.ListMerchantApiKeysUseCase]와
 * 완전히 같은 모양이다**: 요청자를 다시 읽어 권한과 `ACTIVE` 상태를 동적으로 확인하고,
 * 조회 대상 가맹점도 그 조회에서 얻는다(요청이 준 값을 믿지 않는다 — 멀티테넌시 방어).
 *
 * **기존 [MerchantUser.canInviteSubAccounts][paytech.practice.pay.domain.identity.MerchantUser.canInviteSubAccounts]를
 * 조회에도 그대로 재사용한다 — 목록 조회 전용 도메인 메서드를 새로 만들지 않았다.**
 * 술어가 완전히 같고(`ACTIVE` && (`OWNER`||`ADMIN`)), `ListMerchantApiKeysUseCase`가
 * 목록 조회에 `canManageApiKeys()`를 그대로 쓴 선례가 이미 있다. `VIEWER`를 막는 것도
 * 그쪽과 같은 보수적 판단이다 — 명부에는 다른 사용자의 이메일과 마지막 로그인 시각이
 * 담긴다.
 *
 * 권한 없음도 새 예외를 만들지 않고 [MerchantUserCannotInviteSubAccountsException]을
 * 재사용한다(`MerchantApiExceptionHandler`의 403 매핑이 이미 있다).
 */
class ListMerchantUsersUseCase(
	private val merchantUserRepository: MerchantUserRepository,
	private val merchantUserListProjection: MerchantUserListProjection,
) {
	fun execute(command: ListMerchantUsersCommand): ListMerchantUsersResult {
		val querier =
			checkNotNull(merchantUserRepository.findById(command.queriedByMerchantUserId)) {
				"인증된 세션의 MerchantUser(${command.queriedByMerchantUserId.value})를 찾을 수 없습니다."
			}

		if (!querier.canInviteSubAccounts()) {
			throw MerchantUserCannotInviteSubAccountsException(
				"MerchantUser(${querier.id.value})는 가맹점 사용자 목록을 조회할 권한이 없습니다(role=${querier.role}, status=${querier.status}).",
			)
		}

		return ListMerchantUsersResult(merchantUsers = merchantUserListProjection.findByMerchantId(querier.merchantId))
	}
}
