package paytech.practice.pay.api.payment.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import paytech.practice.pay.application.payment.MerchantCannotAcceptPaymentsException
import paytech.practice.pay.application.payment.MerchantNotFoundException

/**
 * `application`/`domain` 계층이 던지는 예외를 HTTP 상태 코드로 옮긴다 — 이 매핑
 * 자체가 inbound Adapter의 책임이라, Use Case나 Value Object는 HTTP를 전혀
 * 모른다.
 */
@RestControllerAdvice
class PaymentApiExceptionHandler {
	@ExceptionHandler(MerchantNotFoundException::class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	fun handleMerchantNotFound(ex: MerchantNotFoundException): ErrorResponse = ErrorResponse(ex.message ?: "Merchant를 찾을 수 없습니다.")

	@ExceptionHandler(MerchantCannotAcceptPaymentsException::class)
	@ResponseStatus(HttpStatus.CONFLICT)
	fun handleMerchantCannotAcceptPayments(ex: MerchantCannotAcceptPaymentsException): ErrorResponse =
		ErrorResponse(ex.message ?: "Merchant가 결제를 받을 수 없는 상태입니다.")

	/** [CreatePaymentRequest]의 `@Valid` 실패(`@NotBlank`/`@Positive` 등)를 처리한다. */
	@ExceptionHandler(MethodArgumentNotValidException::class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	fun handleInvalidRequest(ex: MethodArgumentNotValidException): ErrorResponse =
		ErrorResponse(
			ex.bindingResult.fieldErrors
				.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
				.ifBlank { "요청이 올바르지 않습니다." },
		)

	/** Value Object의 `init { require(...) }` 검증 실패(예: 지갑 주소 형식 오류)를 처리한다. */
	@ExceptionHandler(IllegalArgumentException::class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	fun handleIllegalArgument(ex: IllegalArgumentException): ErrorResponse = ErrorResponse(ex.message ?: "요청이 올바르지 않습니다.")
}
