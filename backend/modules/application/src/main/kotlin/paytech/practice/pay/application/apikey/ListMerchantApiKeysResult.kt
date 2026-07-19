package paytech.practice.pay.application.apikey

import paytech.practice.pay.application.port.outbound.MerchantApiKeySummary

/** [ListMerchantApiKeysUseCase]의 결과다. */
data class ListMerchantApiKeysResult(
	val apiKeys: List<MerchantApiKeySummary>,
)
