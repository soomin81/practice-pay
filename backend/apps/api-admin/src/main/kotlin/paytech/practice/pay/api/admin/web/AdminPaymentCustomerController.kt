package paytech.practice.pay.api.admin.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import paytech.practice.pay.api.admin.security.InternalUserPrincipal
import paytech.practice.pay.application.customer.RevealPaymentCustomerCommand
import paytech.practice.pay.application.customer.RevealPaymentCustomerUseCase
import paytech.practice.pay.application.customer.SearchPaymentCustomersCommand
import paytech.practice.pay.application.customer.SearchPaymentCustomersUseCase
import paytech.practice.pay.domain.customer.CustomerEmail
import paytech.practice.pay.domain.customer.CustomerPhone
import paytech.practice.pay.domain.payment.PaymentId

/**
 * 내부 운영자 콘솔의 **구매자 정보 검색과 원본 열람** API를 노출하는 inbound Adapter다
 * (`docs/architecture/admin-console-api.md`의 4.7/4.8, ADR-008).
 *
 * ## 두 경로의 권한이 다르다
 *
 * 검색은 `SUPER_ADMIN`/`OPERATOR`이고 **원본 열람은 `SUPER_ADMIN` 전용**이다. 검색은 마스킹된
 * 값만 돌려주므로 새로 드러나는 원문이 없지만, 열람은 그 자체가 원문 노출이다. 인가는
 * `SecurityConfig`의 정적 규칙이 지므로 **여기서 다시 확인하지 않는다**(이 앱의 다른
 * 컨트롤러와 같은 방식).
 *
 * ## 열람이 `POST`인 이유
 *
 * 읽기처럼 보이지만 **감사 기록을 남기는 쓰기**다. `GET`이면 브라우저 프리페치·링크 미리보기·
 * 캐시 같은 것이 사람의 의도 없이 열람을 일으킬 수 있는데, 이 자료는 "봤다"는 사실 자체가
 * 사건이라 그런 경로가 있으면 안 된다.
 *
 * ## 응답에 원문이 실리는 유일한 컨트롤러다
 *
 * 다른 모든 경로는 마스킹된 값을 쓴다. 이 대비가 깨지지 않는지는
 * `AdminPaymentCustomerControllerTest`가 검증한다.
 */
@RestController
@RequestMapping("/admin/payment-customers")
class AdminPaymentCustomerController(
	private val searchPaymentCustomersUseCase: SearchPaymentCustomersUseCase,
	private val revealPaymentCustomerUseCase: RevealPaymentCustomerUseCase,
) {
	/**
	 * 이메일 **또는** 휴대전화로 결제를 찾는다. 둘 다 주거나 둘 다 없으면 `400`이다.
	 *
	 * 형식이 어긋나면(예: `email=abc`) 도메인 Value Object가 `IllegalArgumentException`을
	 * 던지고 그대로 `400`이 된다 — 검증 규칙을 여기 복제하지 않는다.
	 */
	@GetMapping
	fun search(
		@RequestParam(required = false) email: String?,
		@RequestParam(required = false) phone: String?,
	): PaymentCustomerSearchResponse {
		val command =
			SearchPaymentCustomersCommand(
				email = email?.let { CustomerEmail(it) },
				phone = phone?.let { CustomerPhone(it) },
			)

		val result = searchPaymentCustomersUseCase.execute(command)

		return PaymentCustomerSearchResponse(
			matches =
				result.matches.map { entry ->
					PaymentCustomerSearchEntryResponse(
						paymentId = entry.paymentId.value,
						merchantId = entry.merchantId.value,
						merchantName = entry.merchantName,
						merchantOrderId = entry.merchantOrderId.value,
						orderName = entry.orderName,
						orderAmount = entry.orderAmount.amount,
						status = entry.status.name,
						nameMasked = entry.nameMasked,
						emailMasked = entry.emailMasked,
						phoneMasked = entry.phoneMasked,
						paidAt = entry.paidAt,
						createdAt = entry.createdAt,
					)
				},
		)
	}

	/**
	 * 마스킹되지 않은 원본을 돌려주고, **누가·언제·왜·어느 IP에서 봤는지**를 함께 기록한다.
	 *
	 * 실행 주체는 요청 본문이 아니라 인증 주체에서 온다 — 본문에서 받으면 감사 기록이 자기
	 * 신고가 된다. IP는 `remoteAddr`를 그대로 쓴다(로그인 감사와 같은 방식) — 프록시 뒤에서는
	 * 프록시 주소가 남지만, **없다고 열람을 막지는 않는다.**
	 */
	@PostMapping("/{paymentId}/reveal")
	fun reveal(
		@PathVariable paymentId: String,
		@Valid @RequestBody request: RevealPaymentCustomerRequest,
		@AuthenticationPrincipal principal: InternalUserPrincipal,
		httpRequest: HttpServletRequest,
	): RevealPaymentCustomerResponse {
		val command =
			RevealPaymentCustomerCommand(
				paymentId = PaymentId(paymentId),
				actorInternalUserId = principal.internalUserId,
				reason = request.reason,
				clientIp = httpRequest.remoteAddr,
			)

		val result = revealPaymentCustomerUseCase.execute(command)

		return RevealPaymentCustomerResponse(
			paymentId = result.paymentId.value,
			name = result.name.value,
			email = result.email.value,
			phone = result.phone.value,
			revealedAt = result.revealedAt,
		)
	}
}
