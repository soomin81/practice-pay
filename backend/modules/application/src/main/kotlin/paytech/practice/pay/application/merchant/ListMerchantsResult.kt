package paytech.practice.pay.application.merchant

import paytech.practice.pay.application.port.outbound.MerchantSummary

/** [ListMerchantsUseCase]의 결과다. */
data class ListMerchantsResult(
	val merchants: List<MerchantSummary>,
)
