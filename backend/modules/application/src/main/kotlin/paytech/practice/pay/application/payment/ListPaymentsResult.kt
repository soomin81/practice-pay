package paytech.practice.pay.application.payment

import paytech.practice.pay.application.port.outbound.PaymentListEntry
import paytech.practice.pay.domain.shared.Money

/**
 * 결제 내역 조회 결과다. 두 콘솔이 같은 읽기 모델을 쓴다
 * (`ListMerchantUsersResult`를 admin/merchant가 공유하는 것과 같은 방식).
 *
 * @property totalCount 필터 전체에 걸린 건수(현재 페이지 건수가 아니다).
 * @property succeededCount 그중 `SUCCEEDED`인 건수.
 * @property succeededAmount `SUCCEEDED` 결제의 주문 금액 합계(KRW) — **왜 성공한
 * 것만 더하는지는 `PaymentListPage`의 KDoc에 있다.**
 * @property page 실제로 조회한 페이지 번호.
 * @property size 실제로 적용된 페이지 크기 — 요청값이 상한을 넘었으면 잘린 값이라
 * 요청과 다를 수 있어서 응답에 함께 싣는다.
 */
data class ListPaymentsResult(
	val entries: List<PaymentListEntry>,
	val totalCount: Long,
	val succeededCount: Long,
	val succeededAmount: Money,
	val page: Int,
	val size: Int,
)
