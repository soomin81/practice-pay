package paytech.practice.pay.application.settlement

import paytech.practice.pay.application.port.outbound.SettlementExportWriter
import paytech.practice.pay.application.port.outbound.SettlementReceivableListProjection
import paytech.practice.pay.domain.merchant.MerchantId

/**
 * 가맹점 콘솔이 **자기 가맹점의** 정산 채권만 스프레드시트로 내보내는 Use Case다
 * (`GET /merchant/settlement-receivables/export`).
 *
 * [ListMerchantSettlementReceivablesUseCase]와 같은 규율이다 — `merchantId`를 별도 인자로
 * **필수로** 받고 Command의 같은 필드는 쳐다보지 않는다. 타입으로 갈라 두면 "인자를
 * 빠뜨려 전 가맹점이 나가는" 실수가 불가능하다.
 *
 * **정산은 결제보다 민감하다** — 새면 남의 가맹점 매출과 수취 예정 금액이 파일로 통째로
 * 빠져나간다.
 */
class ExportMerchantSettlementReceivablesUseCase(
	private val projection: SettlementReceivableListProjection,
	private val writer: SettlementExportWriter,
) {
	fun execute(
		merchantId: MerchantId,
		command: ListSettlementReceivablesCommand,
	): ExportSettlementReceivablesResult = exportSettlementReceivables(projection, writer, merchantId, command)
}
