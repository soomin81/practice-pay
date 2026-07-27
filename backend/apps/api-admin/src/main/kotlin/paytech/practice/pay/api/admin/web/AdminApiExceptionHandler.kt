package paytech.practice.pay.api.admin.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import paytech.practice.pay.application.identity.AccountLockedException
import paytech.practice.pay.application.identity.DuplicateInternalUserException
import paytech.practice.pay.application.identity.DuplicateMerchantException
import paytech.practice.pay.application.identity.InternalUserNotFoundException
import paytech.practice.pay.application.identity.InternalUserNotManageableException
import paytech.practice.pay.application.identity.InvalidCredentialsException
import paytech.practice.pay.application.identity.InvalidInternalUserTransitionException
import paytech.practice.pay.application.identity.InvalidInvitationException
import paytech.practice.pay.application.identity.InvalidMerchantUserTransitionException
import paytech.practice.pay.application.identity.LastActiveOwnerException
import paytech.practice.pay.application.identity.LastActiveSuperAdminException
import paytech.practice.pay.application.identity.MerchantUserNotFoundException

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

	@ExceptionHandler(DuplicateMerchantException::class)
	@ResponseStatus(HttpStatus.CONFLICT)
	fun handleDuplicateMerchant(ex: DuplicateMerchantException): ErrorResponse = ErrorResponse(ex.message ?: "이미 사용 중인 가맹점 코드입니다.")

	@ExceptionHandler(InvalidInvitationException::class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	fun handleInvalidInvitation(ex: InvalidInvitationException): ErrorResponse = ErrorResponse(ex.message ?: "초대가 유효하지 않거나 만료되었습니다.")

	@ExceptionHandler(InternalUserNotFoundException::class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	fun handleInternalUserNotFound(ex: InternalUserNotFoundException): ErrorResponse = ErrorResponse(ex.message ?: "내부 운영자를 찾을 수 없습니다.")

	@ExceptionHandler(InternalUserNotManageableException::class)
	@ResponseStatus(HttpStatus.FORBIDDEN)
	fun handleInternalUserNotManageable(ex: InternalUserNotManageableException): ErrorResponse =
		ErrorResponse(ex.message ?: "해당 계정을 변경할 수 없습니다.")

	@ExceptionHandler(LastActiveSuperAdminException::class)
	@ResponseStatus(HttpStatus.CONFLICT)
	fun handleLastActiveSuperAdmin(ex: LastActiveSuperAdminException): ErrorResponse =
		ErrorResponse(ex.message ?: "내부 운영자에는 최소 하나의 활성 SUPER_ADMIN이 있어야 합니다.")

	/** 도메인 애그리게이트의 `checkTransition` 실패(예: 종료된 계정을 재개하려는 시도)를 409로 옮긴다. */
	@ExceptionHandler(InvalidInternalUserTransitionException::class)
	@ResponseStatus(HttpStatus.CONFLICT)
	fun handleInvalidInternalUserTransition(ex: InvalidInternalUserTransitionException): ErrorResponse =
		ErrorResponse(ex.message ?: "허용되지 않는 상태 전이입니다.")

	@ExceptionHandler(MerchantUserNotFoundException::class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	fun handleMerchantUserNotFound(ex: MerchantUserNotFoundException): ErrorResponse = ErrorResponse(ex.message ?: "가맹점 사용자를 찾을 수 없습니다.")

	@ExceptionHandler(LastActiveOwnerException::class)
	@ResponseStatus(HttpStatus.CONFLICT)
	fun handleLastActiveOwner(ex: LastActiveOwnerException): ErrorResponse = ErrorResponse(ex.message ?: "가맹점에는 최소 한 명의 활성 OWNER가 있어야 합니다.")

	@ExceptionHandler(InvalidMerchantUserTransitionException::class)
	@ResponseStatus(HttpStatus.CONFLICT)
	fun handleInvalidMerchantUserTransition(ex: InvalidMerchantUserTransitionException): ErrorResponse =
		ErrorResponse(ex.message ?: "허용되지 않는 상태 전이입니다.")

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
