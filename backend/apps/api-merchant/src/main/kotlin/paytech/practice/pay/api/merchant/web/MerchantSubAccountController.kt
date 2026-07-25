package paytech.practice.pay.api.merchant.web

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import paytech.practice.pay.api.merchant.security.MerchantUserPrincipal
import paytech.practice.pay.application.identity.ChangeMerchantUserRoleCommand
import paytech.practice.pay.application.identity.ChangeMerchantUserRoleUseCase
import paytech.practice.pay.application.identity.ChangeMerchantUserStatusCommand
import paytech.practice.pay.application.identity.ChangeMerchantUserStatusUseCase
import paytech.practice.pay.application.identity.InviteMerchantSubAccountCommand
import paytech.practice.pay.application.identity.InviteMerchantSubAccountUseCase
import paytech.practice.pay.application.identity.ListMerchantUsersCommand
import paytech.practice.pay.application.identity.ListMerchantUsersUseCase
import paytech.practice.pay.application.identity.MerchantUserStatusAction
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.identity.MerchantUserRole

/**
 * 가맹점 하위 계정 발급 API(`docs/architecture/identity-access-api-key.md`의
 * "4.4 하위 계정 발급": "`OWNER`, `ADMIN`은 하위 계정을 발급할 수 있다")를 노출하는
 * inbound Adapter다.
 *
 * `SecurityConfig`가 `OWNER`/`ADMIN` 역할만 이 경로를 호출할 수 있게 정적으로
 * 걸러내지만, `ACTIVE` 상태 확인까지는 `InviteMerchantSubAccountUseCase`가 요청자의
 * `MerchantUser`를 다시 읽어서 한다(그 Use Case의 KDoc 참고) — 이 컨트롤러는 그
 * 판단에 필요한 `invitedByMerchantUserId`만 세션에서 그대로 넘긴다.
 *
 * **목록 조회(`GET`)도 같은 컨트롤러에 둔다**(`MerchantApiKeyController`가 발급·폐기·목록을
 * 함께 갖는 것과 같다). 목록에 **새 `SecurityConfig` 규칙은 필요 없었다** — 기존 규칙
 * `authorize("/merchant/merchant-users", hasAnyRole("OWNER","ADMIN"))`이 `HttpMethod`로
 * 메서드를 좁히지 않아 `GET`을 이미 덮는다(`api-keys` 와일드카드와 같은 상황이고,
 * `api-admin`의 `MerchantController`가 `GET`을 더할 때 메서드를 좁혀야 했던 것과는 반대다).
 * `VIEWER`를 막는 판단의 근거는 `ListMerchantUsersUseCase`의 KDoc에 있다.
 */
@RestController
@RequestMapping("/merchant/merchant-users")
class MerchantSubAccountController(
	private val inviteMerchantSubAccountUseCase: InviteMerchantSubAccountUseCase,
	private val listMerchantUsersUseCase: ListMerchantUsersUseCase,
	private val changeMerchantUserStatusUseCase: ChangeMerchantUserStatusUseCase,
	private val changeMerchantUserRoleUseCase: ChangeMerchantUserRoleUseCase,
) {
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	fun inviteSubAccount(
		@Valid @RequestBody request: InviteMerchantSubAccountRequest,
		@AuthenticationPrincipal principal: MerchantUserPrincipal,
	): InviteMerchantSubAccountResponse {
		val command =
			InviteMerchantSubAccountCommand(
				loginId = LoginId(request.loginId),
				email = Email(request.email),
				userName = request.userName,
				role = MerchantUserRole.valueOf(request.role),
				invitedByMerchantUserId = principal.merchantUserId,
			)

		val result = inviteMerchantSubAccountUseCase.execute(command)

		return InviteMerchantSubAccountResponse(
			merchantUserId = result.merchantUserId.value,
			loginId = result.loginId.value,
			email = result.email.value,
			userName = result.userName,
			role = result.role.name,
			invitationToken = result.invitationToken,
			invitationExpiresAt = result.invitationExpiresAt,
		)
	}

	@GetMapping
	fun listMerchantUsers(
		@AuthenticationPrincipal principal: MerchantUserPrincipal,
	): ListMerchantUsersResponse {
		val result = listMerchantUsersUseCase.execute(ListMerchantUsersCommand(queriedByMerchantUserId = principal.merchantUserId))

		return ListMerchantUsersResponse(
			merchantUsers =
				result.merchantUsers.map { summary ->
					MerchantUserSummaryResponse(
						merchantUserId = summary.merchantUserId.value,
						loginId = summary.loginId.value,
						email = summary.email.value,
						userName = summary.userName,
						role = summary.role.name,
						status = summary.status.name,
						lastLoginAt = summary.lastLoginAt,
						createdAt = summary.createdAt,
					)
				},
		)
	}

	@PostMapping("/{merchantUserId}/suspend")
	fun suspend(
		@PathVariable merchantUserId: String,
		@AuthenticationPrincipal principal: MerchantUserPrincipal,
	): ChangeMerchantUserStatusResponse = changeStatus(merchantUserId, MerchantUserStatusAction.SUSPEND, principal)

	@PostMapping("/{merchantUserId}/reactivate")
	fun reactivate(
		@PathVariable merchantUserId: String,
		@AuthenticationPrincipal principal: MerchantUserPrincipal,
	): ChangeMerchantUserStatusResponse = changeStatus(merchantUserId, MerchantUserStatusAction.REACTIVATE, principal)

	@PostMapping("/{merchantUserId}/terminate")
	fun terminate(
		@PathVariable merchantUserId: String,
		@AuthenticationPrincipal principal: MerchantUserPrincipal,
	): ChangeMerchantUserStatusResponse = changeStatus(merchantUserId, MerchantUserStatusAction.TERMINATE, principal)

	@PostMapping("/{merchantUserId}/role")
	fun changeRole(
		@PathVariable merchantUserId: String,
		@Valid @RequestBody request: ChangeMerchantUserRoleRequest,
		@AuthenticationPrincipal principal: MerchantUserPrincipal,
	): ChangeMerchantUserRoleResponse {
		val result =
			changeMerchantUserRoleUseCase.execute(
				ChangeMerchantUserRoleCommand(
					targetMerchantUserId = MerchantUserId(merchantUserId),
					newRole = MerchantUserRole.valueOf(request.role),
					requestedByMerchantUserId = principal.merchantUserId,
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
		merchantUserId: String,
		action: MerchantUserStatusAction,
		principal: MerchantUserPrincipal,
	): ChangeMerchantUserStatusResponse {
		val result =
			changeMerchantUserStatusUseCase.execute(
				ChangeMerchantUserStatusCommand(
					targetMerchantUserId = MerchantUserId(merchantUserId),
					action = action,
					requestedByMerchantUserId = principal.merchantUserId,
				),
			)

		return ChangeMerchantUserStatusResponse(
			merchantUserId = result.merchantUserId.value,
			status = result.status.name,
			changedAt = result.changedAt,
		)
	}
}
