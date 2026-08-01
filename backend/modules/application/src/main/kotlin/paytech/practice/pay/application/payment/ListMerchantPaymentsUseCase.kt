package paytech.practice.pay.application.payment

import paytech.practice.pay.application.port.outbound.PaymentListProjection
import paytech.practice.pay.application.port.outbound.PaymentListQuery
import paytech.practice.pay.domain.merchant.MerchantId

/**
 * 가맹점 콘솔이 **자기 가맹점의** 결제 내역만 조회하는 Use Case다
 * (`GET /merchant/payments`).
 *
 * **`merchantId`를 별도 인자로 필수로 받고, Command의 같은 필드는 쳐다보지 않는다.** 이게
 * 이 클래스가 [ListPaymentsUseCase]와 따로 있는 이유다 — 범위를 좁히는 값이 Command의
 * nullable 필드에만 있으면 그 값이 비는 순간 다른 가맹점의 결제까지 조회된다. 호출부
 * (컨트롤러)는 요청 파라미터가 아니라 **인증 주체에서** `merchantId`를 꺼내 넘겨야 한다.
 *
 * 페이징 규칙은 [PaymentListPaging]으로 공유한다(Use Case가 다른 Use Case를 호출하지
 * 않는다는 규칙 때문에 위임하지 않는다).
 */
class ListMerchantPaymentsUseCase(
	private val paymentListProjection: PaymentListProjection,
) {
	fun execute(
		merchantId: MerchantId,
		command: ListPaymentsCommand,
	): ListPaymentsResult {
		val page = PaymentListPaging.normalizePage(command.page)
		val size = PaymentListPaging.normalizeSize(command.size)

		val result =
			paymentListProjection.find(
				PaymentListQuery(
					merchantId = merchantId,
					status = command.status,
					createdFrom = command.createdFrom,
					createdTo = command.createdTo,
					page = page,
					size = size,
				),
			)

		return ListPaymentsResult(
			entries = result.entries,
			totalCount = result.totalCount,
			page = page,
			size = size,
		)
	}
}
