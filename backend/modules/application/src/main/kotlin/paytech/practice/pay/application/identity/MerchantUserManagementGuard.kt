package paytech.practice.pay.application.identity

import paytech.practice.pay.application.port.outbound.MerchantUserRepository
import paytech.practice.pay.domain.identity.AccountStatus
import paytech.practice.pay.domain.identity.MerchantUser
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.identity.MerchantUserRole

/**
 * [ChangeMerchantUserStatusUseCase]와 [ChangeMerchantUserRoleUseCase]가 공유하는
 * 접근 판단이다. 두 Use Case의 규칙이 **완전히 같아서** 각자 복제하면 한쪽만 고쳐지는
 * 방식으로 갈릴 수 있어 한곳에 모았다.
 *
 * **Use Case가 다른 Use Case를 호출하지 않는다는 규칙(`ApplicationPurityTest`)을 지킨다** —
 * 이건 Use Case가 아니라 두 Use Case가 함께 쓰는 순수 함수 묶음이다(Port도 도메인
 * 객체도 바깥에서 받는다).
 */
internal object MerchantUserManagementGuard {
	/**
	 * 요청자를 다시 읽어 관리 권한(`ACTIVE` && `OWNER`/`ADMIN`)을 확인한다 —
	 * 세션의 역할 스냅샷만 믿지 않는다(세션이 살아 있는 동안 계정이 정지될 수 있다).
	 */
	fun loadAuthorizedRequester(
		merchantUserRepository: MerchantUserRepository,
		requestedByMerchantUserId: MerchantUserId,
	): MerchantUser {
		val requester =
			checkNotNull(merchantUserRepository.findById(requestedByMerchantUserId)) {
				"인증된 세션의 MerchantUser(${requestedByMerchantUserId.value})를 찾을 수 없습니다."
			}

		if (!requester.canInviteSubAccounts()) {
			throw MerchantUserCannotInviteSubAccountsException(
				"MerchantUser(${requester.id.value})는 가맹점 사용자를 관리할 권한이 없습니다" +
					"(role=${requester.role}, status=${requester.status}).",
			)
		}
		return requester
	}

	/**
	 * 대상을 읽고 관리 가능한지 판단한다.
	 *
	 * - 없거나 **다른 가맹점 소속**이면 [MerchantUserNotFoundException] — 403이 아니라
	 *   404다(남의 가맹점 사용자의 존재 여부를 알려주지 않는다).
	 * - **자기 자신**이면 [MerchantUserNotManageableException](그 예외의 KDoc 참고).
	 * - **`ADMIN`이 `OWNER`를 대상으로 삼으면** [MerchantUserNotManageableException].
	 */
	fun loadManageableTarget(
		merchantUserRepository: MerchantUserRepository,
		requester: MerchantUser,
		targetMerchantUserId: MerchantUserId,
	): MerchantUser {
		val target =
			merchantUserRepository
				.findById(targetMerchantUserId)
				?.takeIf { it.merchantId == requester.merchantId }
				?: throw MerchantUserNotFoundException("MerchantUser(${targetMerchantUserId.value})를 찾을 수 없습니다.")

		if (target.id == requester.id) {
			throw MerchantUserNotManageableException("자기 자신의 계정은 이 API로 변경할 수 없습니다.")
		}
		if (requester.role == MerchantUserRole.ADMIN && target.role == MerchantUserRole.OWNER) {
			throw MerchantUserNotManageableException("ADMIN은 OWNER 계정을 변경할 수 없습니다.")
		}
		return target
	}

	/**
	 * "최소 하나의 활성 OWNER를 유지한다"(`docs/domain/domain-model.md`)를 강제한다.
	 *
	 * 대상이 **활성 OWNER**일 때만 의미가 있다 — 그를 활성 OWNER 집합에서 빼는 연산
	 * (정지·종료·강등)이면 다른 활성 OWNER가 남는지 세어보고, 없으면 거부한다.
	 */
	fun requireAnotherActiveOwnerRemains(
		merchantUserRepository: MerchantUserRepository,
		target: MerchantUser,
	) {
		if (target.role != MerchantUserRole.OWNER || target.status != AccountStatus.ACTIVE) return

		if (merchantUserRepository.countActiveOwners(target.merchantId) <= 1) {
			throw LastActiveOwnerException(
				"가맹점(${target.merchantId.value})의 마지막 활성 OWNER라 변경할 수 없습니다.",
			)
		}
	}
}
