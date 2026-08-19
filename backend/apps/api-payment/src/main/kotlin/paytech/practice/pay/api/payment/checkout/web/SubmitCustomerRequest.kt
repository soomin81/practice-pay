package paytech.practice.pay.api.payment.checkout.web

import jakarta.validation.constraints.NotBlank

/**
 * `POST /checkout/sessions/{checkoutSessionId}/customer`의 요청 본문이다.
 *
 * **`@NotBlank`만 건다** — 형식(이메일 모양, 국내 휴대전화 번호)은 도메인 Value Object가
 * 검증한다. 여기에 정규식을 한 벌 더 두면 규칙이 두 곳에 갈리고, 갈린 쪽이 느슨하면
 * 그 값이 그대로 저장된다.
 */
data class SubmitCustomerRequest(
	@field:NotBlank
	val name: String,
	@field:NotBlank
	val email: String,
	@field:NotBlank
	val phone: String,
)

/**
 * 구매자 정보 입력 결과다.
 *
 * **마스킹된 값만 담는다** — 방금 입력한 본인에게 되돌려 주는 응답이지만, "응답에 구매자
 * 원본을 싣지 않는다"를 예외 없는 규칙으로 둔다(ADR-008).
 */
data class SubmitCustomerResponse(
	val checkoutSessionId: String,
	val checkoutSessionStatus: String,
	val maskedName: String,
	val maskedEmail: String,
	val maskedPhone: String,
)
