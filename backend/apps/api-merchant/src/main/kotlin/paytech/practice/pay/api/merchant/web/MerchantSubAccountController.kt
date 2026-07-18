package paytech.practice.pay.api.merchant.web

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import paytech.practice.pay.api.merchant.security.MerchantUserPrincipal
import paytech.practice.pay.application.identity.InviteMerchantSubAccountCommand
import paytech.practice.pay.application.identity.InviteMerchantSubAccountUseCase
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.LoginId
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
 */
@RestController
@RequestMapping("/merchant/merchant-users")
class MerchantSubAccountController(
	private val inviteMerchantSubAccountUseCase: InviteMerchantSubAccountUseCase,
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
}
