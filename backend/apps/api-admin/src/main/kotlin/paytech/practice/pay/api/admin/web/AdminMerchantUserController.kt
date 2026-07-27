package paytech.practice.pay.api.admin.web

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import paytech.practice.pay.application.identity.AdminChangeMerchantUserRoleCommand
import paytech.practice.pay.application.identity.AdminChangeMerchantUserRoleUseCase
import paytech.practice.pay.application.identity.AdminChangeMerchantUserStatusCommand
import paytech.practice.pay.application.identity.AdminChangeMerchantUserStatusUseCase
import paytech.practice.pay.application.identity.AdminListMerchantUsersUseCase
import paytech.practice.pay.application.identity.MerchantUserStatusAction
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.identity.MerchantUserRole
import paytech.practice.pay.domain.merchant.MerchantId

/**
 * 내부 운영자 콘솔이 **임의 가맹점의** 사용자를 조회·관리하는 inbound Adapter다
 * (`docs/architecture/admin-console-api.md`의 4절). 가맹점이 스스로 잠기거나(마지막 OWNER
 * 정지) 계정 사고가 났을 때 PG가 개입하는 경로다.
 *
 * **인가는 전적으로 `SecurityConfig`가 한다.** 조회(`GET`)는 인증된 내부 사용자 전원
 * (`VIEWER` 포함 — `GET /admin/merchants`와 같은 스코핑)에게 열려 있고, 관리 액션(`POST`)은
 * `/admin/merchants` 하위 경로 POST 와일드카드 규칙이 `SUPER_ADMIN`/`OPERATOR`로 좁힌다
 * (가맹점 등록 `POST /admin/merchants`와 같은 역할 집합). 그래서 Use Case는 요청자를 받지
 * 않고 테넌시도 경로가 지정한 `merchantId`로 잡는다(`AdminChangeMerchantUserStatusUseCase`의
 * KDoc 참고) — merchant-side가 요청자 `MerchantUser`를 다시 읽는 것과 다른 지점이다.
 *
 * 가맹점 콘솔의 `MerchantSubAccountController`와 같은 모양이되 **초대 재발송·취소는 없다**
 * (내부 운영자는 가맹점의 초대를 대신 관리하지 않는다 — 그건 가맹점 OWNER/ADMIN의 몫이다).
 */
@RestController
@RequestMapping("/admin/merchants/{merchantId}/users")
class AdminMerchantUserController(
	private val adminListMerchantUsersUseCase: AdminListMerchantUsersUseCase,
	private val adminChangeMerchantUserStatusUseCase: AdminChangeMerchantUserStatusUseCase,
	private val adminChangeMerchantUserRoleUseCase: AdminChangeMerchantUserRoleUseCase,
) {
	@GetMapping
	fun listMerchantUsers(
		@PathVariable merchantId: String,
	): AdminListMerchantUsersResponse {
		val result = adminListMerchantUsersUseCase.execute(MerchantId(merchantId))

		return AdminListMerchantUsersResponse(
			merchantUsers =
				result.merchantUsers.map { summary ->
					AdminMerchantUserSummaryResponse(
						merchantUserId = summary.merchantUserId.value,
						loginId = summary.loginId.value,
						email = summary.email.value,
						userName = summary.userName,
						role = summary.role.name,
						status = summary.status.name,
						lastLoginAt = summary.lastLoginAt,
						createdAt = summary.createdAt,
						pendingInvitationExpiresAt = summary.pendingInvitationExpiresAt,
					)
				},
		)
	}

	@PostMapping("/{merchantUserId}/suspend")
	fun suspend(
		@PathVariable merchantId: String,
		@PathVariable merchantUserId: String,
	): ChangeMerchantUserStatusResponse = changeStatus(merchantId, merchantUserId, MerchantUserStatusAction.SUSPEND)

	@PostMapping("/{merchantUserId}/reactivate")
	fun reactivate(
		@PathVariable merchantId: String,
		@PathVariable merchantUserId: String,
	): ChangeMerchantUserStatusResponse = changeStatus(merchantId, merchantUserId, MerchantUserStatusAction.REACTIVATE)

	@PostMapping("/{merchantUserId}/terminate")
	fun terminate(
		@PathVariable merchantId: String,
		@PathVariable merchantUserId: String,
	): ChangeMerchantUserStatusResponse = changeStatus(merchantId, merchantUserId, MerchantUserStatusAction.TERMINATE)

	@PostMapping("/{merchantUserId}/role")
	fun changeRole(
		@PathVariable merchantId: String,
		@PathVariable merchantUserId: String,
		@Valid @RequestBody request: ChangeMerchantUserRoleRequest,
	): ChangeMerchantUserRoleResponse {
		val result =
			adminChangeMerchantUserRoleUseCase.execute(
				AdminChangeMerchantUserRoleCommand(
					merchantId = MerchantId(merchantId),
					targetMerchantUserId = MerchantUserId(merchantUserId),
					newRole = MerchantUserRole.valueOf(request.role),
				),
			)

		return ChangeMerchantUserRoleResponse(
			merchantUserId = result.merchantUserId.value,
			role = result.role.name,
			changedAt = result.changedAt,
		)
	}

	/** 세 상태 액션이 공유하는 호출 — 어떤 전이인지만 다르다([MerchantUserStatusAction]). */
	private fun changeStatus(
		merchantId: String,
		merchantUserId: String,
		action: MerchantUserStatusAction,
	): ChangeMerchantUserStatusResponse {
		val result =
			adminChangeMerchantUserStatusUseCase.execute(
				AdminChangeMerchantUserStatusCommand(
					merchantId = MerchantId(merchantId),
					targetMerchantUserId = MerchantUserId(merchantUserId),
					action = action,
				),
			)

		return ChangeMerchantUserStatusResponse(
			merchantUserId = result.merchantUserId.value,
			status = result.status.name,
			changedAt = result.changedAt,
		)
	}
}
