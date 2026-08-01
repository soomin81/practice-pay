package paytech.practice.pay.api.payment.web

import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import paytech.practice.pay.application.payment.MerchantCannotAcceptPaymentsException
import paytech.practice.pay.application.payment.MerchantNotFoundException
import paytech.practice.pay.application.payment.ReceivingWalletNotConfiguredException

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

	/**
	 * 요청한 네트워크의 PG 수취 지갑이 설정돼 있지 않은 경우다. 가맹점이 요청을 고쳐서
	 * 해결할 수 있는 것이 없으므로 4xx가 아니라 503으로 돌려준다 — 그 판단 근거는
	 * [ReceivingWalletNotConfiguredException]의 KDoc에 있다.
	 */
	@ExceptionHandler(ReceivingWalletNotConfiguredException::class)
	@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
	fun handleReceivingWalletNotConfigured(ex: ReceivingWalletNotConfiguredException): ErrorResponse =
		ErrorResponse("요청한 네트워크(${ex.network.code})로는 지금 결제를 받을 수 없습니다.")

	/** [CreatePaymentRequest]의 `@Valid` 실패(`@NotBlank`/`@Positive` 등)를 처리한다. */
	@ExceptionHandler(MethodArgumentNotValidException::class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	fun handleInvalidRequest(ex: MethodArgumentNotValidException): ErrorResponse =
		ErrorResponse(
			ex.bindingResult.fieldErrors
				.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
				.ifBlank { "요청이 올바르지 않습니다." },
		)

	/**
	 * 요청 본문 자체를 읽지 못한 경우(JSON 문법 오류, 필수 필드 누락으로 인한 역직렬화
	 * 실패, 잘못된 문자 인코딩 등)를 처리한다.
	 *
	 * 이 핸들러가 없으면 Spring의 기본 처리가 `response.sendError(400)`을 호출하고,
	 * 그러면 컨테이너가 `/error`로 ERROR 디스패치를 돌리면서 응답 형식이 이 API의
	 * `ErrorResponse`와 달라진다. 여기서 직접 응답을 쓰면 그 경로를 아예 타지 않는다
	 * (`/error`가 인증을 요구해 401로 뒤바뀌던 문제는 SecurityConfig에서 따로 막았다).
	 */
	@ExceptionHandler(HttpMessageNotReadableException::class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	fun handleUnreadableRequest(ex: HttpMessageNotReadableException): ErrorResponse = ErrorResponse("요청 본문을 읽을 수 없습니다.")

	/** Value Object의 `init { require(...) }` 검증 실패(예: 지갑 주소 형식 오류)를 처리한다. */
	@ExceptionHandler(IllegalArgumentException::class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	fun handleIllegalArgument(ex: IllegalArgumentException): ErrorResponse = ErrorResponse(ex.message ?: "요청이 올바르지 않습니다.")
}
