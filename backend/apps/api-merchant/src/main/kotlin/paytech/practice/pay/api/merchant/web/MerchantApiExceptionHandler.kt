package paytech.practice.pay.api.merchant.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import paytech.practice.pay.application.apikey.MerchantApiKeyNotActiveException
import paytech.practice.pay.application.apikey.MerchantApiKeyNotFoundException
import paytech.practice.pay.application.apikey.MerchantUserCannotManageApiKeysException
import paytech.practice.pay.application.identity.AccountLockedException
import paytech.practice.pay.application.identity.DuplicateMerchantUserException
import paytech.practice.pay.application.identity.InvalidCredentialsException
import paytech.practice.pay.application.identity.InvalidInvitationException
import paytech.practice.pay.application.identity.InvalidMerchantUserTransitionException
import paytech.practice.pay.application.identity.LastActiveOwnerException
import paytech.practice.pay.application.identity.MerchantUserCannotInviteSubAccountsException
import paytech.practice.pay.application.identity.MerchantUserNotFoundException
import paytech.practice.pay.application.identity.MerchantUserNotManageableException

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

	@ExceptionHandler(MerchantUserCannotManageApiKeysException::class)
	@ResponseStatus(HttpStatus.FORBIDDEN)
	fun handleMerchantUserCannotManageApiKeys(ex: MerchantUserCannotManageApiKeysException): ErrorResponse =
		ErrorResponse(ex.message ?: "API Key를 관리할 권한이 없습니다.")

	@ExceptionHandler(MerchantApiKeyNotFoundException::class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	fun handleMerchantApiKeyNotFound(ex: MerchantApiKeyNotFoundException): ErrorResponse = ErrorResponse(ex.message ?: "API Key를 찾을 수 없습니다.")

	@ExceptionHandler(MerchantApiKeyNotActiveException::class)
	@ResponseStatus(HttpStatus.CONFLICT)
	fun handleMerchantApiKeyNotActive(ex: MerchantApiKeyNotActiveException): ErrorResponse =
		ErrorResponse(ex.message ?: "이미 폐기되었거나 만료된 API Key입니다.")

	@ExceptionHandler(MerchantUserNotFoundException::class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	fun handleMerchantUserNotFound(ex: MerchantUserNotFoundException): ErrorResponse = ErrorResponse(ex.message ?: "가맹점 사용자를 찾을 수 없습니다.")

	@ExceptionHandler(MerchantUserNotManageableException::class)
	@ResponseStatus(HttpStatus.FORBIDDEN)
	fun handleMerchantUserNotManageable(ex: MerchantUserNotManageableException): ErrorResponse =
		ErrorResponse(ex.message ?: "해당 계정을 변경할 권한이 없습니다.")

	/** "최소 하나의 활성 OWNER를 유지한다" 불변식 위반 — 권한 문제가 아니라 지금 상태에서 허용되지 않는 요청이라 409다. */
	@ExceptionHandler(LastActiveOwnerException::class)
	@ResponseStatus(HttpStatus.CONFLICT)
	fun handleLastActiveOwner(ex: LastActiveOwnerException): ErrorResponse = ErrorResponse(ex.message ?: "가맹점에는 최소 한 명의 활성 OWNER가 있어야 합니다.")

	/**
	 * 도메인 애그리게이트의 `checkTransition` 실패(예: 종료된 계정을 재개하려는 시도)를 409로 옮긴다.
	 *
	 * **`IllegalStateException`을 그대로 잡지 않는다** — 그러면 `checkNotNull`(세션이 가리키는
	 * 사용자가 DB에 없음)처럼 **500이 맞는 진짜 예상 못 한 오류까지 409로 가려진다**. Use Case가
	 * 도메인 전이 호출만 감싸 이 전용 예외로 바꾼다(`apps:api-payment`가 같은 이유로 그 매핑을
	 * 체크아웃 경로에만 좁힌 것과 같은 판단이고, 여기서는 경로 대신 예외 타입으로 좁혔다).
	 */
	@ExceptionHandler(InvalidMerchantUserTransitionException::class)
	@ResponseStatus(HttpStatus.CONFLICT)
	fun handleInvalidMerchantUserTransition(ex: InvalidMerchantUserTransitionException): ErrorResponse =
		ErrorResponse(ex.message ?: "지금 상태에서는 허용되지 않는 요청입니다.")

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
