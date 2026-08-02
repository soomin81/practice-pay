package paytech.practice.pay.application.settlement

import paytech.practice.pay.application.port.outbound.SettlementReceivableListProjection
import paytech.practice.pay.application.port.outbound.SettlementReceivableListQuery

/**
 * 내부 운영자 콘솔이 **전 가맹점의** 정산 채권을 조회하는 Use Case다
 * (`GET /admin/settlement-receivables`).
 *
 * 요청자 검사가 없다 — 조회는 인증된 내부 사용자 전원(`VIEWER` 포함)에게 열려 있다
 * (`GET /admin/payments`와 같은 스코핑).
 *
 * 가맹점 콘솔용은 [ListMerchantSettlementReceivablesUseCase]로 따로 있다 — 결제 목록을
 * 둘로 나눈 것과 같은 이유다: 범위를 좁히는 값이 nullable 필드 하나뿐이면 그 값이 비는
 * 순간 **다른 가맹점이 받을 돈까지 노출된다.**
 */
class ListSettlementReceivablesUseCase(
	private val settlementReceivableListProjection: SettlementReceivableListProjection,
) {
	fun execute(command: ListSettlementReceivablesCommand): ListSettlementReceivablesResult {
		val page = SettlementListPaging.normalizePage(command.page)
		val size = SettlementListPaging.normalizeSize(command.size)

		val result =
			settlementReceivableListProjection.find(
				SettlementReceivableListQuery(
					merchantId = command.merchantId,
					status = command.status,
					eligibleFrom = command.eligibleFrom,
					eligibleTo = command.eligibleTo,
					page = page,
					size = size,
				),
			)

		return ListSettlementReceivablesResult(
			entries = result.entries,
			totalCount = result.totalCount,
			totalNetAmount = result.totalNetAmount,
			heldCount = result.heldCount,
			heldNetAmount = result.heldNetAmount,
			page = page,
			size = size,
		)
	}
}
