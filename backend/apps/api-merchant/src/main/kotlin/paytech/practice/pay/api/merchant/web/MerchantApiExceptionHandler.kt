package paytech.practice.pay.api.merchant.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import paytech.practice.pay.application.identity.AccountLockedException
import paytech.practice.pay.application.identity.DuplicateMerchantUserException
import paytech.practice.pay.application.identity.InvalidCredentialsException
import paytech.practice.pay.application.identity.InvalidInvitationException
import paytech.practice.pay.application.identity.MerchantUserCannotInviteSubAccountsException

/**
 * `application`/`domain` 계층이 던지는 예외를 HTTP 상태 코드로 옮긴다 — 이 매핑
 * 자체가 inbound Adapter의 책임이라, Use Case나 Value Object는 HTTP를 전혀
 * 모른다(`apps:api-payment`의 `PaymentApiExceptionHandler`와 같은 패턴).
 *
 * `MerchantAuthExceptionHandler` → `MerchantApiExceptionHandler`로 이름을 바꿨다
 * (`apps:api-admin`의 `AdminAuthExceptionHandler` → `AdminApiExceptionHandler`와
 * 같은 이유 — `InviteMerchantSubAccountUseCase`가 생기면서 로그인 전용이 아니게
 * 됐다).
 */
@RestControllerAdvice
class MerchantApiExceptionHandler {
	@ExceptionHandler(InvalidCredentialsException::class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	fun handleInvalidCredentials(ex: InvalidCredentialsException): ErrorResponse = ErrorResponse(ex.message ?: "로그인에 실패했습니다.")

	@ExceptionHandler(AccountLockedException::class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	fun handleAccountLocked(ex: AccountLockedException): ErrorResponse = ErrorResponse(ex.message ?: "계정이 잠겼습니다.")

	@ExceptionHandler(InvalidInvitationException::class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	fun handleInvalidInvitation(ex: InvalidInvitationException): ErrorResponse = ErrorResponse(ex.message ?: "초대가 유효하지 않거나 만료되었습니다.")

	@ExceptionHandler(DuplicateMerchantUserException::class)
	@ResponseStatus(HttpStatus.CONFLICT)
	fun handleDuplicateMerchantUser(ex: DuplicateMerchantUserException): ErrorResponse =
		ErrorResponse(ex.message ?: "이미 사용 중인 로그인 아이디 또는 이메일입니다.")

	@ExceptionHandler(MerchantUserCannotInviteSubAccountsException::class)
	@ResponseStatus(HttpStatus.FORBIDDEN)
	fun handleMerchantUserCannotInviteSubAccounts(ex: MerchantUserCannotInviteSubAccountsException): ErrorResponse =
		ErrorResponse(ex.message ?: "하위 계정을 발급할 권한이 없습니다.")

	@ExceptionHandler(MethodArgumentNotValidException::class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	fun handleInvalidRequest(ex: MethodArgumentNotValidException): ErrorResponse =
		ErrorResponse(
			ex.bindingResult.fieldErrors
				.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
				.ifBlank { "요청이 올바르지 않습니다." },
		)

	/** Value Object의 `init { require(...) }` 검증 실패나 `MerchantUserRole.valueOf`/`MerchantUser.inviteSubAccount`의 `require` 실패를 처리한다. */
	@ExceptionHandler(IllegalArgumentException::class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	fun handleIllegalArgument(ex: IllegalArgumentException): ErrorResponse = ErrorResponse(ex.message ?: "요청이 올바르지 않습니다.")
}
