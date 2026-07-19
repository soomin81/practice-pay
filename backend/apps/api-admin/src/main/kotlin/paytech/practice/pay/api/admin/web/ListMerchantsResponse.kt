package paytech.practice.pay.api.admin.web

/** `GET /admin/merchants`의 응답 본문이다. */
data class ListMerchantsResponse(
	val merchants: List<MerchantSummaryResponse>,
)
