package paytech.practice.pay.api.merchant.web

import java.time.Instant

/**
 * `GET /merchant/payments`의 응답이다(`docs/architecture/merchant-console-api.md`).
 *
 * 내부 운영자 콘솔의 같은 이름 응답과 **거의 같지만 `merchantName`이 없다** — 이 콘솔은
 * 언제나 자기 가맹점 하나만 보므로 행마다 가맹점 이름을 반복할 이유가 없다. 두 앱은
 * 서로를 모르는 독립 배포 단위라 DTO를 공유하지 않는다.
 *
 * @property totalCount 필터 전체에 걸린 건수(현재 페이지 건수가 아니다).
 * @property size 실제로 적용된 페이지 크기 — 상한에 걸리면 요청값과 다를 수 있다.
 */
data class ListPaymentsResponse(
	val payments: List<PaymentSummaryResponse>,
	val totalCount: Long,
	val page: Int,
	val size: Int,
)

/**
 * 결제 한 건의 목록용 요약이다.
 *
 * @property paymentAmount USDC **Minor Unit 정수를 문자열로** 준다 — 토큰 금액이
 * JavaScript `Number`의 안전 정수 범위를 넘을 수 있어서다
 * (`docs/architecture/checkout-api.md`와 같은 이유). `orderAmount`(KRW)는 숫자로 준다.
 * @property transactionHash 고객이 아직 제출하지 않았으면 `null`이다.
 */
data class PaymentSummaryResponse(
	val paymentId: String,
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
