package paytech.practice.pay.application.payment

import paytech.practice.pay.application.port.outbound.PaymentListProjection
import paytech.practice.pay.application.port.outbound.PaymentListQuery

/**
 * 내부 운영자 콘솔이 **전 가맹점의** 결제 내역을 조회하는 Use Case다
 * (`GET /admin/payments`).
 *
 * 요청자 검사가 없다 — 조회는 인증된 내부 사용자 전원(`VIEWER` 포함)에게 열려 있어
 * (`SecurityConfig`가 `GET`을 좁히지 않는다, `GET /admin/merchants`와 같은 스코핑)
 * 요청자 역할로 좁힐 것이 없다. `AdminListMerchantUsersUseCase`와 같은 판단이다.
 *
 * 가맹점 콘솔용은 [ListMerchantPaymentsUseCase]로 따로 있다 — 두 경로를 한 Use Case의
 * nullable 인자로 합치면, 가맹점 경로에서 그 인자가 `null`로 새는 순간 **전 가맹점
 * 결제가 통째로 노출된다.** 타입으로 갈라 두면 그 실수가 불가능하다.
 */
class ListPaymentsUseCase(
	private val paymentListProjection: PaymentListProjection,
) {
	fun execute(command: ListPaymentsCommand): ListPaymentsResult {
		val page = PaymentListPaging.normalizePage(command.page)
		val size = PaymentListPaging.normalizeSize(command.size)

		val result =
			paymentListProjection.find(
				PaymentListQuery(
					merchantId = command.merchantId,
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
