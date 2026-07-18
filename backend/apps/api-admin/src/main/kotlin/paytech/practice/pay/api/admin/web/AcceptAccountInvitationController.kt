package paytech.practice.pay.api.admin.web

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import paytech.practice.pay.application.identity.AcceptAccountInvitationCommand
import paytech.practice.pay.application.identity.AcceptAccountInvitationUseCase
import paytech.practice.pay.domain.identity.InvitationAccountType

/**
 * 내부 운영자 초대 수락(활성화) API를 노출하는 inbound Adapter다
 * (`docs/domain/state-transitions.md`의 "활성화": `INVITED → ACTIVE`).
 *
 * 호출자는 아직 인증되지 않은 상태다(초대 Token만 갖고 있다) — `SecurityConfig`가
 * 이 경로를 `permitAll`로 둔다(`/admin/login`과 같은 이유).
 * `expectedAccountType`을 [InvitationAccountType.INTERNAL_USER]로 고정해서 호출한다 —
 * 가맹점 사용자 초대 Token이 이 엔드포인트로 잘못 제출돼도 거부된다
 * ([AcceptAccountInvitationUseCase]의 KDoc 참고).
 */
@RestController
@RequestMapping("/admin/account-invitations")
class AcceptAccountInvitationController(
	private val acceptAccountInvitationUseCase: AcceptAccountInvitationUseCase,
) {
	@PostMapping("/accept")
	fun acceptInvitation(
		@Valid @RequestBody request: AcceptAccountInvitationRequest,
	): AcceptAccountInvitationResponse {
		val result =
			acceptAccountInvitationUseCase.execute(
				AcceptAccountInvitationCommand(
					invitationToken = request.invitationToken,
					newPassword = request.newPassword,
					expectedAccountType = InvitationAccountType.INTERNAL_USER,
				),
			)

		return AcceptAccountInvitationResponse(
			loginId = result.loginId.value,
			activatedAt = result.activatedAt,
		)
	}
}
