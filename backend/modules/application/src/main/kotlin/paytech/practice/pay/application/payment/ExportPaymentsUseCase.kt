package paytech.practice.pay.application.payment

import paytech.practice.pay.application.port.outbound.PaymentExportWriter
import paytech.practice.pay.application.port.outbound.PaymentListProjection
import paytech.practice.pay.application.port.outbound.PaymentListQuery
import paytech.practice.pay.domain.merchant.MerchantId

/**
 * 내부 운영자 콘솔이 **전 가맹점의** 결제 내역을 스프레드시트로 내보내는 Use Case다
 * (`GET /admin/payments/export`).
 *
 * 조회(`ListPaymentsUseCase`)와 같은 필터를 쓰지만 **페이징 상한은 공유하지 않는다** —
 * 화면 페이징(최대 200)과 내보내기(최대 `PaymentExportPolicy.MAX_EXPORT_ROWS`)는 요구
 * 조건이 다르다.
 *
 * 가맹점 콘솔용은 [ExportMerchantPaymentsUseCase]로 따로 있다 — 조회 쪽을 둘로 나눈 것과
 * **같은 이유이고, 내보내기에서는 더 중요하다**: 범위가 새면 화면에 잠깐 보이는 정도가
 * 아니라 남의 가맹점 결제가 통째로 파일로 빠져나간다.
 */
class ExportPaymentsUseCase(
	private val paymentListProjection: PaymentListProjection,
	private val paymentExportWriter: PaymentExportWriter,
) {
	fun execute(command: ListPaymentsCommand): ExportPaymentsResult =
		exportPayments(paymentListProjection, paymentExportWriter, command.merchantId, command)
}

/**
 * 두 내보내기 Use Case가 공유하는 본문이다. Use Case가 다른 Use Case를 호출하지 않는다는
 * 규칙(`ApplicationPurityTest`) 때문에 위임 대신 함수로 뺐다([PaymentListPaging]과 같은 방식).
 *
 * **상한보다 1건 더 조회해서 잘렸는지 판단한다** — 정확히 상한만큼 조회하면 "딱 맞게
 * 채워진 것"과 "넘쳐서 잘린 것"을 구분할 수 없다. 별도로 `COUNT`를 한 번 더 돌리는 것보다
 * 싸다.
 */
internal fun exportPayments(
	projection: PaymentListProjection,
	writer: PaymentExportWriter,
	merchantId: MerchantId?,
	command: ListPaymentsCommand,
): ExportPaymentsResult {
	val page =
		projection.find(
			PaymentListQuery(
				merchantId = merchantId,
				status = command.status,
				createdFrom = command.createdFrom,
				createdTo = command.createdTo,
				page = 0,
				size = PaymentExportPolicy.MAX_EXPORT_ROWS + 1,
			),
		)

	val truncated = page.entries.size > PaymentExportPolicy.MAX_EXPORT_ROWS
	val entries = if (truncated) page.entries.take(PaymentExportPolicy.MAX_EXPORT_ROWS) else page.entries

	return ExportPaymentsResult(
		spreadsheet = writer.writeSpreadsheet(entries),
		rowCount = entries.size,
		truncated = truncated,
	)
}
