package paytech.practice.pay.api.admin.web

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import paytech.practice.pay.api.admin.security.InternalUserPrincipal
import paytech.practice.pay.application.identity.IssueInternalUserCommand
import paytech.practice.pay.application.identity.IssueInternalUserUseCase
import paytech.practice.pay.application.identity.ListInternalUsersUseCase
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalUserRole
import paytech.practice.pay.domain.identity.LoginId

/**
 * 내부 운영자 발급·목록 조회 API를 노출하는 inbound Adapter다
 * (`docs/architecture/identity-access-api-key.md`의 "3.3 발급 정책": "내부 운영자 계정은
 * `SUPER_ADMIN`만 발급할 수 있다").
 *
 * 원래 이름은 `InternalUserIssuanceController`였는데 목록 조회가 생기면서 바꿨다
 * (`MerchantController`가 등록·목록을 함께 갖는 것과 같은 모양 — "Issuance"라는 이름이
 * 목록까지 포함하면 어긋난다).
 *
 * **인가는 전적으로 `SecurityConfig`가 한다.** `authorize("/admin/internal-users",
 * hasRole("SUPER_ADMIN"))`이 `HttpMethod`로 좁혀져 있지 않아 **새로 추가한 `GET`도 함께
 * 덮는다** — `/admin/merchants`가 `POST`로 좁혀 `GET`을 `VIEWER`에게 연 것과 정반대
 * 상황이고, 여기서는 그게 맞다: 내부 직원 명부에는 직원 이메일·마지막 로그인·누가
 * `SUPER_ADMIN`인지가 담기고, 계정 관리 자체가 `SUPER_ADMIN`의 영역이다("3.3").
 * 그래서 `ListInternalUsersUseCase`도 요청자를 받지 않는다(그 KDoc 참고).
 *
 * 발급자(`issuedByInternalUserId`)는 요청 본문이 아니라 `@AuthenticationPrincipal`로
 * 주입받는 [InternalUserPrincipal]에서 가져온다.
 */
@RestController
@RequestMapping("/admin/internal-users")
class InternalUserController(
	private val issueInternalUserUseCase: IssueInternalUserUseCase,
	private val listInternalUsersUseCase: ListInternalUsersUseCase,
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

	@GetMapping
	fun listInternalUsers(): ListInternalUsersResponse {
		val result = listInternalUsersUseCase.execute()

		return ListInternalUsersResponse(
			internalUsers =
				result.internalUsers.map { summary ->
					InternalUserSummaryResponse(
						internalUserId = summary.internalUserId.value,
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
}
