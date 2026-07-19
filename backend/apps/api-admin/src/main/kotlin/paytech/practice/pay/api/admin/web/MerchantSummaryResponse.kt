package paytech.practice.pay.api.admin.web

import java.time.Instant

/** [ListMerchantsResponse]의 항목 하나다. */
data class MerchantSummaryResponse(
	val merchantId: String,
	val merchantCode: String,
	val merchantName: String,
	val status: String,
	val createdAt: Instant,
)
