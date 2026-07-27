package paytech.practice.pay.application.identity

import paytech.practice.pay.application.port.outbound.MerchantLoginAuditEntry

/** [ListMerchantLoginAuditUseCase]의 출력이다. */
data class ListMerchantLoginAuditResult(
	val entries: List<MerchantLoginAuditEntry>,
)
