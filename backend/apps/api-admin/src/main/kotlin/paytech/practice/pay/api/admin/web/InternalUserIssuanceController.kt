package paytech.practice.pay.api.admin.web

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import paytech.practice.pay.api.admin.security.InternalUserPrincipal
import paytech.practice.pay.application.identity.IssueInternalUserCommand
import paytech.practice.pay.application.identity.IssueInternalUserUseCase
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalUserRole
import paytech.practice.pay.domain.identity.LoginId

/**
 * 내부 운영자 발급 API(`docs/architecture/identity-access-api-key.md`의
 * "3.3 발급 정책": "내부 운영자 계정은 `SUPER_ADMIN`만 발급할 수 있다")를 노출하는
 * inbound Adapter다.
 *
 * 이 경로 자체를 `SUPER_ADMIN`만 호출할 수 있도록 막는 건 `SecurityConfig`의 책임이다
 * (`authorize("/admin/internal-users", hasRole("SUPER_ADMIN"))`) — 이 메서드가
 * 실행된다는 것 자체가 이미 `SUPER_ADMIN` 세션으로 인증됐다는 뜻이다. 발급자
 * (`issuedByInternalUserId`)는 요청 본문이 아니라 `@AuthenticationPrincipal`로
 * 주입받는 [InternalUserPrincipal]에서 가져온다(`PaymentController`의
 * `ApiKeyPrincipal` 사용과 같은 이유).
 */
@RestController
@RequestMapping("/admin/internal-users")
class InternalUserIssuanceController(
	private val issueInternalUserUseCase: IssueInternalUserUseCase,
) {
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	fun issueInternalUser(
		@Valid @RequestBody request: IssueInternalUserRequest,
		@AuthenticationPrincipal principal: InternalUserPrincipal,
	): IssueInternalUserResponse {
		val command =
			IssueInternalUserCommand(
				loginId = LoginId(request.loginId),
				email = Email(request.email),
				userName = request.userName,
				role = InternalUserRole.valueOf(request.role),
				issuedByInternalUserId = principal.internalUserId,
			)

		val result = issueInternalUserUseCase.execute(command)

		return IssueInternalUserResponse(
			internalUserId = result.internalUserId.value,
			loginId = result.loginId.value,
			email = result.email.value,
			userName = result.userName,
			role = result.role.name,
			invitationToken = result.invitationToken,
			invitationExpiresAt = result.invitationExpiresAt,
		)
	}
}
