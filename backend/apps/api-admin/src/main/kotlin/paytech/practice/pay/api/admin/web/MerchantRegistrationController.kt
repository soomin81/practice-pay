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
import paytech.practice.pay.application.identity.RegisterMerchantCommand
import paytech.practice.pay.application.identity.RegisterMerchantUseCase
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.merchant.MerchantCode
import paytech.practice.pay.domain.shared.HttpUrl

/**
 * 가맹점 등록 API(`docs/architecture/identity-access-api-key.md`의 "4.3 가맹점
 * 등록과 OWNER 생성")를 노출하는 inbound Adapter다.
 *
 * `SUPER_ADMIN`만 발급할 수 있는 `POST /admin/internal-users`와 달리, 이 경로는
 * `SUPER_ADMIN`과 `OPERATOR` 둘 다 호출할 수 있다(`SecurityConfig`) —
 * "3.2 MVP 역할"이 `OPERATOR`의 업무를 "가맹점·결제·운영 업무"로 정의해서다.
 * 등록자(`registeredByInternalUserId`)는 요청 본문이 아니라
 * `@AuthenticationPrincipal`로 주입받는 [InternalUserPrincipal]에서 가져온다
 * (`InternalUserIssuanceController`와 같은 이유).
 */
@RestController
@RequestMapping("/admin/merchants")
class MerchantRegistrationController(
	private val registerMerchantUseCase: RegisterMerchantUseCase,
) {
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	fun registerMerchant(
		@Valid @RequestBody request: RegisterMerchantRequest,
		@AuthenticationPrincipal principal: InternalUserPrincipal,
	): RegisterMerchantResponse {
		val command =
			RegisterMerchantCommand(
				merchantCode = MerchantCode(request.merchantCode),
				merchantName = request.merchantName,
				webhookUrl = request.webhookUrl?.let { HttpUrl(it) },
				ownerLoginId = LoginId(request.ownerLoginId),
				ownerEmail = Email(request.ownerEmail),
				ownerUserName = request.ownerUserName,
				registeredByInternalUserId = principal.internalUserId,
			)

		val result = registerMerchantUseCase.execute(command)

		return RegisterMerchantResponse(
			merchantId = result.merchantId.value,
			merchantCode = result.merchantCode.value,
			merchantName = result.merchantName,
			ownerMerchantUserId = result.ownerMerchantUserId.value,
			ownerLoginId = result.ownerLoginId.value,
			ownerEmail = result.ownerEmail.value,
			invitationToken = result.invitationToken,
			invitationExpiresAt = result.invitationExpiresAt,
		)
	}
}
