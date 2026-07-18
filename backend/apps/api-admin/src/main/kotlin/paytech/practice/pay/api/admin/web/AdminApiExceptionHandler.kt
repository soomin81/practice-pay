package paytech.practice.pay.api.admin.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import paytech.practice.pay.application.identity.AccountLockedException
import paytech.practice.pay.application.identity.DuplicateInternalUserException
import paytech.practice.pay.application.identity.InvalidCredentialsException
import paytech.practice.pay.application.identity.InvalidInvitationException

/**
 * `application`/`domain` 계층이 던지는 예외를 HTTP 상태 코드로 옮긴다 — 이 매핑
 * 자체가 inbound Adapter의 책임이라, Use Case나 Value Object는 HTTP를 전혀
 * 모른다(`apps:api-payment`의 `PaymentApiExceptionHandler`와 같은 패턴).
 */
@RestControllerAdvice
class AdminApiExceptionHandler {
	@ExceptionHandler(InvalidCredentialsException::class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	fun handleInvalidCredentials(ex: InvalidCredentialsException): ErrorResponse = ErrorResponse(ex.message ?: "로그인에 실패했습니다.")

	@ExceptionHandler(AccountLockedException::class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	fun handleAccountLocked(ex: AccountLockedException): ErrorResponse = ErrorResponse(ex.message ?: "계정이 잠겼습니다.")

	@ExceptionHandler(DuplicateInternalUserException::class)
	@ResponseStatus(HttpStatus.CONFLICT)
	fun handleDuplicateInternalUser(ex: DuplicateInternalUserException): ErrorResponse =
		ErrorResponse(ex.message ?: "이미 사용 중인 로그인 아이디 또는 이메일입니다.")

	@ExceptionHandler(InvalidInvitationException::class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	fun handleInvalidInvitation(ex: InvalidInvitationException): ErrorResponse = ErrorResponse(ex.message ?: "초대가 유효하지 않거나 만료되었습니다.")

	@ExceptionHandler(MethodArgumentNotValidException::class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	fun handleInvalidRequest(ex: MethodArgumentNotValidException): ErrorResponse =
		ErrorResponse(
			ex.bindingResult.fieldErrors
				.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
				.ifBlank { "요청이 올바르지 않습니다." },
		)

	/** Value Object의 `init { require(...) }` 검증 실패나 `InternalUserRole.valueOf`처럼 잘못된 열거형 문자열을 처리한다. */
	@ExceptionHandler(IllegalArgumentException::class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	fun handleIllegalArgument(ex: IllegalArgumentException): ErrorResponse = ErrorResponse(ex.message ?: "요청이 올바르지 않습니다.")
}
