package paytech.practice.pay.api.merchant.web

/** `GET /merchant/api-keys`의 응답 본문이다. */
data class ListMerchantApiKeysResponse(
	val apiKeys: List<MerchantApiKeySummaryResponse>,
)
