package paytech.practice.pay.api.admin.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import paytech.practice.pay.application.customer.PaymentCustomerNotFoundException
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
import paytech.practice.pay.application.payment.BlockchainTransactionNotFoundException
import paytech.practice.pay.application.payment.PaymentNotFoundException
import paytech.practice.pay.application.payment.TransactionNotReorgeableException
import paytech.practice.pay.application.settlement.SettlementReceivableNotCancellableException
import paytech.practice.pay.application.settlement.SettlementReceivableNotFoundException
import paytech.practice.pay.application.settlement.SettlementReceivableNotReleasableException
import paytech.practice.pay.application.webhook.WebhookDeliveryNotFoundException
import paytech.practice.pay.application.webhook.WebhookDeliveryNotRedeliverableException

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

	/**
	 * 결제가 없는 경우와 **구분하지 않는다**(계약 4.8) — 나눠서 알려주면 "그 결제는 존재한다"가
	 * 응답으로 새어 나간다.
	 */
	@ExceptionHandler(PaymentCustomerNotFoundException::class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	fun handlePaymentCustomerNotFound(ex: PaymentCustomerNotFoundException): ErrorResponse = ErrorResponse(ex.message ?: "구매자 정보를 찾을 수 없습니다.")

	@ExceptionHandler(PaymentNotFoundException::class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	fun handlePaymentNotFound(ex: PaymentNotFoundException): ErrorResponse = ErrorResponse(ex.message ?: "결제를 찾을 수 없습니다.")

	@ExceptionHandler(TransactionNotReorgeableException::class)
	@ResponseStatus(HttpStatus.CONFLICT)
	fun handleTransactionNotReorgeable(ex: TransactionNotReorgeableException): ErrorResponse =
		ErrorResponse(ex.message ?: "확정된 거래만 체인 재구성으로 표시할 수 있습니다.")

	@ExceptionHandler(BlockchainTransactionNotFoundException::class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	fun handleBlockchainTransactionNotFound(ex: BlockchainTransactionNotFoundException): ErrorResponse =
		ErrorResponse(ex.message ?: "온체인 거래를 찾을 수 없습니다.")

	@ExceptionHandler(WebhookDeliveryNotFoundException::class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	fun handleWebhookDeliveryNotFound(ex: WebhookDeliveryNotFoundException): ErrorResponse =
		ErrorResponse(ex.message ?: "Webhook 전송을 찾을 수 없습니다.")

	@ExceptionHandler(SettlementReceivableNotFoundException::class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	fun handleSettlementReceivableNotFound(ex: SettlementReceivableNotFoundException): ErrorResponse =
		ErrorResponse(ex.message ?: "정산 채권을 찾을 수 없습니다.")

	@ExceptionHandler(SettlementReceivableNotReleasableException::class)
	@ResponseStatus(HttpStatus.CONFLICT)
	fun handleSettlementReceivableNotReleasable(ex: SettlementReceivableNotReleasableException): ErrorResponse =
		ErrorResponse(ex.message ?: "보류된 정산 채권만 해제할 수 있습니다.")

	@ExceptionHandler(SettlementReceivableNotCancellableException::class)
	@ResponseStatus(HttpStatus.CONFLICT)
	fun handleSettlementReceivableNotCancellable(ex: SettlementReceivableNotCancellableException): ErrorResponse =
		ErrorResponse(ex.message ?: "이미 취소된 정산 채권입니다.")

	/**
	 * `409`인 이유: 요청 자체는 올바른데 **대상의 현재 상태가 그 동작을 허용하지 않는다**
	 * (`400`은 요청이 잘못됐다는 뜻이라 맞지 않는다). 이미 성공한 전송을 다시 보내려는
	 * 경우가 대표적이다.
	 */
	@ExceptionHandler(WebhookDeliveryNotRedeliverableException::class)
	@ResponseStatus(HttpStatus.CONFLICT)
	fun handleWebhookDeliveryNotRedeliverable(ex: WebhookDeliveryNotRedeliverableException): ErrorResponse =
		ErrorResponse(ex.message ?: "실패한 전송만 다시 보낼 수 있습니다.")

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
