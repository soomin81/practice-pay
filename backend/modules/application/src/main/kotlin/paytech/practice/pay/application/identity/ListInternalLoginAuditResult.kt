package paytech.practice.pay.application.identity

import paytech.practice.pay.application.port.outbound.InternalLoginAuditEntry

/** [ListInternalLoginAuditUseCase]의 출력이다. */
data class ListInternalLoginAuditResult(
	val entries: List<InternalLoginAuditEntry>,
)
