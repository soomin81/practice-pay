package paytech.practice.pay.api.payment.checkout.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import paytech.practice.pay.api.payment.web.ErrorResponse
import paytech.practice.pay.application.checkout.CheckoutSessionExpiredException
import paytech.practice.pay.application.checkout.CheckoutSessionNotCancellableException
import paytech.practice.pay.application.checkout.CheckoutSessionNotFoundException
import paytech.practice.pay.application.payment.DuplicateTransactionHashException

/**
 * 체크아웃 경로에서만 나오는 예외를 HTTP 상태로 옮긴다
 * (`docs/architecture/checkout-api.md`의 5절).
 *
 * **`basePackages`로 이 패키지에만 적용한다.** `PaymentApiExceptionHandler`가 이미
 * 앱 전체(`@RestControllerAdvice`)에 걸려 있어서, 범위를 좁히지 않으면 두 Advice가
 * 같은 예외 타입(`IllegalArgumentException` 등)을 두고 충돌한다. 여기서 다루지 않는
 * 예외(검증 실패 400, 본문 파싱 실패 400)는 그대로 앱 전역 Advice가 처리한다.
 *
 * `IllegalStateException`을 `409`로 매핑하는 것이 이 Advice의 핵심이다 —
 * `CheckoutSession.connectWallet()`/`submitPayment()`의 `checkTransition`이 잘못된
 * 전이에 이 예외를 던지는데, 전역 Advice에는 매핑이 없어서 그대로 두면 raw 500으로
 * 샌다. 다만 이 매핑은 **체크아웃 경로에서만** 유효하다 — 다른 곳의
 * `IllegalStateException`은 여전히 예상 못 한 오류(500)로 남는 게 맞다.
 */
@RestControllerAdvice(basePackages = ["paytech.practice.pay.api.payment.checkout.web"])
class CheckoutApiExceptionHandler {
	@ExceptionHandler(CheckoutSessionNotFoundException::class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	fun handleNotFound(ex: CheckoutSessionNotFoundException): ErrorResponse = ErrorResponse(ex.message ?: "체크아웃 세션을 찾을 수 없습니다.")

	@ExceptionHandler(CheckoutSessionExpiredException::class)
	@ResponseStatus(HttpStatus.GONE)
	fun handleExpired(ex: CheckoutSessionExpiredException): ErrorResponse = ErrorResponse(ex.message ?: "체크아웃 세션의 유효 시간이 지났습니다.")

	@ExceptionHandler(CheckoutSessionNotCancellableException::class)
	@ResponseStatus(HttpStatus.CONFLICT)
	fun handleNotCancellable(ex: CheckoutSessionNotCancellableException): ErrorResponse = ErrorResponse(ex.message ?: "체크아웃 세션을 취소할 수 없습니다.")

	@ExceptionHandler(DuplicateTransactionHashException::class)
	@ResponseStatus(HttpStatus.CONFLICT)
	fun handleDuplicateTransactionHash(ex: DuplicateTransactionHashException): ErrorResponse =
		ErrorResponse(ex.message ?: "이미 다른 결제에 사용된 Transaction Hash입니다.")

	@ExceptionHandler(IllegalStateException::class)
	@ResponseStatus(HttpStatus.CONFLICT)
	fun handleInvalidTransition(ex: IllegalStateException): ErrorResponse = ErrorResponse(ex.message ?: "현재 상태에서 허용되지 않는 요청입니다.")
}
