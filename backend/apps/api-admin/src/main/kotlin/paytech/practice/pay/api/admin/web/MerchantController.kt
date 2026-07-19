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
import paytech.practice.pay.application.identity.RegisterMerchantCommand
import paytech.practice.pay.application.identity.RegisterMerchantUseCase
import paytech.practice.pay.application.merchant.ListMerchantsUseCase
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.merchant.MerchantCode
import paytech.practice.pay.domain.shared.HttpUrl

/**
 * 가맹점 등록·목록 조회 API를 노출하는 inbound Adapter다
 * (`docs/architecture/identity-access-api-key.md`의 "4.3 가맹점 등록과 OWNER 생성").
 * `MerchantRegistrationController` → `MerchantController`로 이름을 바꿨다(등록
 * 전용이 아니게 됐다 — `AdminAuthExceptionHandler` → `AdminApiExceptionHandler`와
 * 같은 이유의 리네임).
 *
 * **등록(`POST`)과 목록 조회(`GET`)는 인가 수준이 다르다.** `SUPER_ADMIN`만
 * 발급할 수 있는 `POST /admin/internal-users`와 달리 등록은 `SUPER_ADMIN`과
 * `OPERATOR` 둘 다 호출할 수 있고("3.2 MVP 역할"이 `OPERATOR`의 업무를
 * "가맹점·결제·운영 업무"로 정의해서다), 목록 조회는 `VIEWER`까지 포함한 인증된
 * 내부 사용자 전원이 호출할 수 있다(`VIEWER` = "조회 전용"). `SecurityConfig`가
 * `POST /admin/merchants`에만 역할 제약을 걸고 `GET /admin/merchants`는 기본
 * `authenticated()` 규칙에 맡기는 이유다 — 이 구분을 `SecurityConfig`의 KDoc에
 * 자세히 남겼다.
 *
 * 등록자(`registeredByInternalUserId`)는 요청 본문이 아니라
 * `@AuthenticationPrincipal`로 주입받는 [InternalUserPrincipal]에서 가져온다
 * (`InternalUserIssuanceController`와 같은 이유) — 목록 조회는 감사 정보가
 * 필요 없어 `principal`을 받지 않는다.
 */
@RestController
@RequestMapping("/admin/merchants")
class MerchantController(
	private val registerMerchantUseCase: RegisterMerchantUseCase,
	private val listMerchantsUseCase: ListMerchantsUseCase,
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

	@GetMapping
	fun listMerchants(): ListMerchantsResponse {
		val result = listMerchantsUseCase.execute()

		return ListMerchantsResponse(
			merchants =
				result.merchants.map {
					MerchantSummaryResponse(
						merchantId = it.merchantId.value,
						merchantCode = it.merchantCode.value,
						merchantName = it.merchantName,
						status = it.status.name,
						createdAt = it.createdAt,
					)
				},
		)
	}
}
