package paytech.practice.pay.application.apikey

import paytech.practice.pay.domain.apikey.MerchantApiKeyId
import java.time.Instant

/** [RevokeMerchantApiKeyUseCase]의 결과다. */
data class RevokeMerchantApiKeyResult(
	val merchantApiKeyId: MerchantApiKeyId,
	val revokedAt: Instant,
)
