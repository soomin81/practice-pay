package paytech.practice.pay.application.payment

import paytech.practice.pay.application.port.outbound.PaymentExportWriter
import paytech.practice.pay.application.port.outbound.PaymentListProjection
import paytech.practice.pay.domain.merchant.MerchantId

/**
 * 가맹점 콘솔이 **자기 가맹점의** 결제 내역만 스프레드시트로 내보내는 Use Case다
 * (`GET /merchant/payments/export`).
 *
 * [ListMerchantPaymentsUseCase]와 같은 규율이다 — `merchantId`를 별도 인자로 **필수로**
 * 받고 Command의 같은 필드는 쳐다보지 않는다. 내보내기에서는 이 구분이 조회보다 더
 * 중요하다: 범위가 새면 남의 가맹점 결제가 파일로 통째로 빠져나간다.
 */
class ExportMerchantPaymentsUseCase(
	private val paymentListProjection: PaymentListProjection,
	private val paymentExportWriter: PaymentExportWriter,
) {
	fun execute(
		merchantId: MerchantId,
		command: ListPaymentsCommand,
	): ExportPaymentsResult = exportPayments(paymentListProjection, paymentExportWriter, merchantId, command)
}
