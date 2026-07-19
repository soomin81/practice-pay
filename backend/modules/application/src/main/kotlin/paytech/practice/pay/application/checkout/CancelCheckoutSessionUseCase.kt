package paytech.practice.pay.application.checkout

import paytech.practice.pay.application.port.outbound.CheckoutSessionRepository
import paytech.practice.pay.domain.checkout.CheckoutSessionStatus
import java.time.Clock

/**
 * 고객이 결제를 포기하고 체크아웃을 취소하는 Use Case다
 * (`docs/architecture/checkout-api.md`의 4.5).
 *
 * `CheckoutSession.cancel()`은 이전부터 도메인에 있었지만 호출부가 없었다 — 이
 * Use Case가 그 첫 자리다.
 *
 * **`Payment`는 건드리지 않는다.** 도메인에 `Payment`를 고객 취소로 종료시키는
 * 전이가 없고(`CREATED`/`READY → EXPIRED`는 만료 전용이다), `docs/`도 체크아웃 취소가
 * 결제까지 끝낸다고 말하지 않는다 — 문서에 없는 전이를 새로 만들지 않는다. 결과적으로
 * 취소된 세션의 `Payment`는 만료 시각까지 `READY`로 남았다가 만료 Worker(아직 없음,
 * 알려진 gap)가 정리하게 된다.
 *
 * 단일 Aggregate만 저장하므로 `TransactionManager`가 필요 없다
 * (`ConnectCheckoutWalletUseCase`와 같은 이유).
 */
class CancelCheckoutSessionUseCase(
	private val checkoutSessionRepository: CheckoutSessionRepository,
	private val clock: Clock,
) {
	fun execute(command: CancelCheckoutSessionCommand): CancelCheckoutSessionResult {
		val checkoutSession =
			checkoutSessionRepository.findById(command.checkoutSessionId)
				?: throw CheckoutSessionNotFoundException(command.checkoutSessionId)

		val now = clock.instant()
		if (now.isAfter(checkoutSession.expiresAt)) {
			throw CheckoutSessionExpiredException(command.checkoutSessionId)
		}
		if (!checkoutSession.status.isCancellable()) {
			throw CheckoutSessionNotCancellableException(command.checkoutSessionId, checkoutSession.status)
		}

		checkoutSession.cancel(now)
		checkoutSessionRepository.save(checkoutSession)

		return CancelCheckoutSessionResult(
			checkoutSessionId = checkoutSession.id,
			checkoutSessionStatus = checkoutSession.status,
			cancelUrl = checkoutSession.cancelUrl,
		)
	}
}

/**
 * `CheckoutSession.isBeforePaymentSubmitted()`가 `private`이라 Use Case에서 부를 수
 * 없어서, 같은 경계를 상태 값만으로 다시 표현한다.
 *
 * 도메인의 `cancel()`이 여전히 최종 방어선이다 — 여기서 통과시켜도 전이 규칙에
 * 어긋나면 `cancel()`이 `IllegalStateException`으로 막는다. 이 확인은 그 예외가
 * 매핑 없이 500으로 새지 않게 앞에서 걸러내는 용도다.
 */
private fun CheckoutSessionStatus.isCancellable(): Boolean =
	this == CheckoutSessionStatus.CREATED ||
		this == CheckoutSessionStatus.OPEN ||
		this == CheckoutSessionStatus.WALLET_CONNECTED
