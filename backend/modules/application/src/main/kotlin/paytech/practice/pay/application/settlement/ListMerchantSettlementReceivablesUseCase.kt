package paytech.practice.pay.application.settlement

import paytech.practice.pay.application.port.outbound.SettlementReceivableListProjection
import paytech.practice.pay.application.port.outbound.SettlementReceivableListQuery
import paytech.practice.pay.domain.merchant.MerchantId

/**
 * 가맹점 콘솔이 **자기 가맹점의** 정산 채권만 조회하는 Use Case다
 * (`GET /merchant/settlement-receivables`).
 *
 * **`merchantId`를 별도 인자로 필수로 받고 Command의 같은 필드는 쳐다보지 않는다** —
 * `ListMerchantPaymentsUseCase`와 같은 규율이다. 호출부(컨트롤러)는 요청 파라미터가 아니라
 * **인증 주체에서** `merchantId`를 꺼내 넘겨야 한다.
 *
 * 정산은 결제보다 민감도가 한 단계 높다 — 새면 남의 **매출과 수취 예정 금액**이 드러난다.
 */
class ListMerchantSettlementReceivablesUseCase(
	private val settlementReceivableListProjection: SettlementReceivableListProjection,
) {
	fun execute(
		merchantId: MerchantId,
		command: ListSettlementReceivablesCommand,
	): ListSettlementReceivablesResult {
		val page = SettlementListPaging.normalizePage(command.page)
		val size = SettlementListPaging.normalizeSize(command.size)

		val result =
			settlementReceivableListProjection.find(
				SettlementReceivableListQuery(
					merchantId = merchantId,
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
