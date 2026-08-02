package paytech.practice.pay.application.settlement

import paytech.practice.pay.application.port.outbound.SettlementExportWriter
import paytech.practice.pay.application.port.outbound.SettlementReceivableListProjection
import paytech.practice.pay.application.port.outbound.SettlementReceivableListQuery
import paytech.practice.pay.domain.merchant.MerchantId

/**
 * 내부 운영자 콘솔이 **전 가맹점의** 정산 채권을 스프레드시트로 내보내는 Use Case다
 * (`GET /admin/settlement-receivables/export`).
 *
 * 조회([ListSettlementReceivablesUseCase])와 같은 필터를 쓰지만 **페이징 상한은 공유하지
 * 않는다** — 화면 페이징과 내보내기는 요구 조건이 다르다(결제 내보내기와 같은 판단).
 *
 * 가맹점 콘솔용은 [ExportMerchantSettlementReceivablesUseCase]로 따로 있다. 조회 쪽을 둘로
 * 나눈 것과 같은 이유이고, **정산에서는 결제보다 더 중요하다** — 새면 남의 가맹점 매출과
 * 수취 예정 금액이 통째로 파일로 빠져나간다.
 */
class ExportSettlementReceivablesUseCase(
	private val projection: SettlementReceivableListProjection,
	private val writer: SettlementExportWriter,
) {
	fun execute(command: ListSettlementReceivablesCommand): ExportSettlementReceivablesResult =
		exportSettlementReceivables(projection, writer, command.merchantId, command)
}

/**
 * 두 내보내기 Use Case가 공유하는 본문이다. Use Case가 다른 Use Case를 호출하지 않는다는
 * 규칙(`ApplicationPurityTest`) 때문에 위임 대신 함수로 뺐다(결제 쪽과 같은 방식).
 *
 * **상한보다 1건 더 조회해서 잘렸는지 판단한다** — 정확히 상한만큼 조회하면 "딱 맞게
 * 채워진 것"과 "넘쳐서 잘린 것"을 구분할 수 없다.
 */
internal fun exportSettlementReceivables(
	projection: SettlementReceivableListProjection,
	writer: SettlementExportWriter,
	merchantId: MerchantId?,
	command: ListSettlementReceivablesCommand,
): ExportSettlementReceivablesResult {
	val page =
		projection.find(
			SettlementReceivableListQuery(
				merchantId = merchantId,
				status = command.status,
				eligibleFrom = command.eligibleFrom,
				eligibleTo = command.eligibleTo,
				page = 0,
				size = SettlementExportPolicy.MAX_EXPORT_ROWS + 1,
			),
		)

	val truncated = page.entries.size > SettlementExportPolicy.MAX_EXPORT_ROWS
	val entries = if (truncated) page.entries.take(SettlementExportPolicy.MAX_EXPORT_ROWS) else page.entries

	return ExportSettlementReceivablesResult(
		spreadsheet = writer.writeSpreadsheet(entries),
		rowCount = entries.size,
		truncated = truncated,
	)
}
