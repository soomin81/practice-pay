package paytech.practice.pay.api.merchant.web

import java.time.Instant

/** `DELETE /merchant/api-keys/{merchantApiKeyId}`의 응답 본문이다. */
data class RevokeMerchantApiKeyResponse(
	val merchantApiKeyId: String,
	val revokedAt: Instant,
)
