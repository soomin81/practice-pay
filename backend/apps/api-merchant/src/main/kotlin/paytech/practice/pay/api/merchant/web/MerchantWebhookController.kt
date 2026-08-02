package paytech.practice.pay.api.merchant.web

import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import paytech.practice.pay.api.merchant.security.MerchantUserPrincipal
import paytech.practice.pay.application.merchant.GetMerchantWebhookSettingsUseCase
import paytech.practice.pay.application.merchant.MerchantWebhookSettings
import paytech.practice.pay.application.merchant.RotateMerchantWebhookSecretUseCase
import paytech.practice.pay.application.merchant.UpdateMerchantWebhookUrlUseCase

/**
 * 가맹점 Webhook 설정(수신 URL, 서명 비밀) API를 노출하는 inbound Adapter다
 * (계약: `docs/architecture/webhook-api.md`).
 *
 * **`SecurityConfig`에서 `OWNER`/`ADMIN` 전용으로 막는다** — 응답에 **서명 비밀이
 * 들어 있어서** `VIEWER`가 읽으면 그 사람이 곧 Webhook을 위조할 수 있게 된다.
 * 조회(`GET`)까지 함께 막는 것이 핵심이다(`MerchantApiKeyController`가 `GET`도
 * `OWNER`/`ADMIN`으로 둔 것과 같은 판단이지만, 이쪽은 근거가 더 분명하다 —
 * 저쪽 `GET`은 Key 원문을 돌려주지 않는다).
 *
 * 모든 동작은 **인증 주체의 가맹점**에만 적용된다 — 요청 본문이나 경로로
 * `merchantId`를 받지 않으므로 다른 가맹점의 설정을 건드릴 방법이 애초에 없다.
 */
@RestController
@RequestMapping("/merchant/webhook")
class MerchantWebhookController(
	private val getMerchantWebhookSettingsUseCase: GetMerchantWebhookSettingsUseCase,
	private val updateMerchantWebhookUrlUseCase: UpdateMerchantWebhookUrlUseCase,
	private val rotateMerchantWebhookSecretUseCase: RotateMerchantWebhookSecretUseCase,
) {
	@GetMapping
	fun get(
		@AuthenticationPrincipal principal: MerchantUserPrincipal,
	): MerchantWebhookSettingsResponse = getMerchantWebhookSettingsUseCase.execute(principal.merchantId).toResponse()

	@PutMapping
	fun updateUrl(
		@Valid @RequestBody request: UpdateMerchantWebhookUrlRequest,
		@AuthenticationPrincipal principal: MerchantUserPrincipal,
	): MerchantWebhookSettingsResponse {
		updateMerchantWebhookUrlUseCase.execute(principal.merchantId, request.webhookUrl?.takeIf { it.isNotBlank() })
		return getMerchantWebhookSettingsUseCase.execute(principal.merchantId).toResponse()
	}

	/**
	 * 서명 비밀을 교체한다. **되돌릴 수 없다** — 자세한 영향은
	 * `RotateMerchantWebhookSecretUseCase`의 KDoc 참고.
	 */
	@PostMapping("/rotate-secret")
	fun rotateSecret(
		@AuthenticationPrincipal principal: MerchantUserPrincipal,
	): MerchantWebhookSettingsResponse = rotateMerchantWebhookSecretUseCase.execute(principal.merchantId).toResponse()

	private fun MerchantWebhookSettings.toResponse() =
		MerchantWebhookSettingsResponse(
			webhookUrl = webhookUrl,
			signingSecret = signingSecret,
			secretVersion = secretVersion,
		)
}
