package paytech.practice.pay.api.admin.web

import java.time.Instant

/**
 * `GET /admin/payments`의 응답이다(`docs/architecture/admin-console-api.md`).
 *
 * @property totalCount 필터 전체에 걸린 건수. 현재 페이지의 건수가 아니라 페이지 이동
 * UI를 그리는 데 쓰는 값이다.
 * @property succeededCount 그중 `SUCCEEDED`인 건수. [totalCount]와 함께 승인율의 재료다.
 * @property succeededAmount `SUCCEEDED` 결제의 주문 금액 합계(KRW 원 단위 정수).
 * **성공한 것만 더한다** — 전체를 더하면 만료·실패한 결제까지 매출처럼 보인다
 * (`PaymentListPage`의 KDoc 참고).
 * @property size 실제로 적용된 페이지 크기 — 요청값이 상한을 넘었으면 잘린 값이라
 * 요청과 다를 수 있다.
 */
data class ListPaymentsResponse(
	val payments: List<PaymentSummaryResponse>,
	val totalCount: Long,
	val succeededCount: Long,
	val succeededAmount: Long,
	val page: Int,
	val size: Int,
)

/**
 * 결제 한 건의 목록용 요약이다.
 *
 * @property paymentAmount USDC **Minor Unit 정수를 문자열로** 준다. 체크아웃 API와 같은
 * 이유다(`docs/architecture/checkout-api.md`) — 토큰 금액이 JavaScript `Number`의 안전
 * 정수 범위를 넘을 수 있다. `orderAmount`(KRW)는 원 단위라 숫자로 준다.
 * @property transactionHash 고객이 아직 제출하지 않았으면 `null`이다.
 * @property failureReason `FAILED`일 때만 값이 있다.
 * @property paidAt `SUCCEEDED`일 때만 값이 있다.
 */
data class PaymentSummaryResponse(
	val paymentId: String,
	val merchantId: String,
	val merchantName: String,
	val merchantOrderId: String,
	val orderName: String,
	val orderAmount: Long,
	val paymentAsset: String,
	val paymentAmount: String,
	val tokenDecimals: Int,
	val network: String,
	val status: String,
	val failureReason: String?,
	val transactionHash: String?,
	val paidAt: Instant?,
	val createdAt: Instant,
)
