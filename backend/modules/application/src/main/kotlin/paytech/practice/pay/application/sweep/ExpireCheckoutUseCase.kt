package paytech.practice.pay.application.sweep

import paytech.practice.pay.application.port.outbound.CheckoutSessionRepository
import paytech.practice.pay.application.port.outbound.PaymentRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.domain.checkout.CheckoutSessionStatus
import paytech.practice.pay.domain.payment.PaymentStatus
import java.time.Clock

/**
 * 만료 시각이 지난 체크아웃을 정리하는 Sweep Use Case다 — `Payment`와 (있으면) 1:1로 딸린
 * `CheckoutSession`을 함께 `EXPIRED`로 전이시킨다(`docs/architecture/checkout-api.md` 7절의
 * "Sweep Worker가 없다" gap을 메운다).
 *
 * **Payment와 CheckoutSession을 한 트랜잭션에서 전이시키되 각각 독립적으로 가드한다.**
 * Payment는 `CREATED`/`READY`, CheckoutSession은 `PAYMENT_SUBMITTED` 이전
 * (`CREATED`/`OPEN`/`WALLET_CONNECTED`)에서만 만료할 수 있다 — 한쪽이 이미 그 창을
 * 벗어났어도 다른 쪽은 만료될 수 있으므로 서로를 막지 않는다. `CheckoutSession`은 결제
 * 생성 직후엔 아직 없을 수 있어(Payment가 먼저 `CREATED`로 생긴다) `null`도 정상이다.
 *
 * **후보 애그리게이트를 그대로 쓰지 않고 식별자로 다시 읽는다** — 후보를 뽑은 뒤 결제가
 * 진행됐을 수 있어서다([ExpireAccountInvitationUseCase]와 같은 재검증 규율). 여러 애그리게이트에
 * 걸친 쓰기라 [TransactionManager]로 묶는다.
 */
class ExpireCheckoutUseCase(
	private val paymentRepository: PaymentRepository,
	private val checkoutSessionRepository: CheckoutSessionRepository,
	private val transactionManager: TransactionManager,
	private val clock: Clock,
) {
	fun execute(command: ExpireCheckoutCommand) {
		transactionManager.runInTransaction {
			val now = clock.instant()

			val payment = paymentRepository.findById(command.paymentId) ?: return@runInTransaction
			if (payment.status == PaymentStatus.CREATED || payment.status == PaymentStatus.READY) {
				payment.expire(now)
				paymentRepository.save(payment)
			}

			val session = checkoutSessionRepository.findByPaymentId(command.paymentId)
			if (session != null && session.status in EXPIRABLE_SESSION_STATUSES) {
				session.expire(now)
				checkoutSessionRepository.save(session)
			}
		}
	}

	private companion object {
		/** `PAYMENT_SUBMITTED` 이전 — `CheckoutSession.expire()`가 허용하는 상태들. */
		val EXPIRABLE_SESSION_STATUSES =
			setOf(
				CheckoutSessionStatus.CREATED,
				CheckoutSessionStatus.OPEN,
				CheckoutSessionStatus.WALLET_CONNECTED,
			)
	}
}
