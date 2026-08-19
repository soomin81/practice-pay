package paytech.practice.pay.api.admin.web

import java.time.Instant

/**
 * `GET /admin/payment-customers`의 응답이다(계약 4.7).
 *
 * **찾은 것이 없으면 빈 목록과 `200`이다.** "그런 사람이 없다"를 `404`로 알려주지 않는다 —
 * 검색은 없는 것이 정상적인 결과이고, 응답 코드로 존재 여부를 확인해 주지도 않는다.
 */
data class PaymentCustomerSearchResponse(
	val matches: List<PaymentCustomerSearchEntryResponse>,
)

/**
 * 검색 결과 한 줄 — **마스킹된 값과 그 결제를 식별할 만큼**만 담는다.
 *
 * 필드 이름은 결제 목록(`GET /admin/payments`)과 같은 것을 쓴다 — 같은 개념에 두 이름을
 * 만들지 않는다(`docs/domain/glossary.md`).
 */
data class PaymentCustomerSearchEntryResponse(
	val paymentId: String,
	val merchantId: String,
	val merchantName: String,
	val merchantOrderId: String,
	val orderName: String,
	// KRW는 숫자로 준다(토큰 Minor Unit만 문자열이다 — 계약 4.1.1).
	val orderAmount: Long,
	val status: String,
	val nameMasked: String,
	val emailMasked: String,
	val phoneMasked: String,
	val paidAt: Instant?,
	val createdAt: Instant,
)
