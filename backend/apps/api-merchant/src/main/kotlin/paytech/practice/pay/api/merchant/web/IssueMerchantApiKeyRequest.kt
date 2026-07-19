package paytech.practice.pay.api.merchant.web

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty

/**
 * `POST /merchant/api-keys`의 요청 본문이다(`docs/architecture/identity-access-api-key.md`의
 * "6.6 발급 권한"). [scopes]는 [paytech.practice.pay.domain.apikey.ApiKeyScope]의
 * 이름 목록이어야 한다 — MVP는 `PAYMENT_CREATE`/`PAYMENT_READ`만 허용하고, 그 밖의
 * 값이나 빈 배열은 [IssueMerchantApiKeyUseCase][paytech.practice.pay.application.apikey.IssueMerchantApiKeyUseCase]가
 * `IllegalArgumentException`을 던져 400으로 처리된다. `environment`는 받지
 * 않는다 — MVP는 항상 `TEST`만 발급한다(`docs/`의 "현재는 Base Sepolia만
 * 지원하므로 TEST Key만 발급한다").
 */
data class IssueMerchantApiKeyRequest(
	@field:NotBlank
	val keyName: String,
	@field:NotEmpty
	val scopes: List<String>,
)
