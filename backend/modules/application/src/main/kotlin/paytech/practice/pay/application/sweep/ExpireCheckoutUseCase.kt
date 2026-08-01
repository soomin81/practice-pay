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

			// 변경할 목적의 읽기라 행을 잠근다 — 고객의 결제 제출과 이 만료가 같은 결제를 동시에
			// 집을 수 있다. 잠금 순서는 **Payment → CheckoutSession**으로 통일한다(반대 순서로
			// 잠그는 경로가 생기면 교착이 난다).
			val payment = paymentRepository.findByIdForUpdate(command.paymentId) ?: return@runInTransaction
			if (payment.status == PaymentStatus.CREATED || payment.status == PaymentStatus.READY) {
				payment.expire(now)
				paymentRepository.save(payment)
			}

			// 세션은 `paymentId`로 찾으므로 식별자를 먼저 얻은 뒤 잠금 조회로 다시 읽는다
			// (`findByPaymentId`의 잠금 변형을 Port에 더하지 않기 위해서다). 고객의 지갑 연결·
			// 취소가 같은 세션을 동시에 바꿀 수 있어 이 잠금이 필요하다.
			val session =
				checkoutSessionRepository
					.findByPaymentId(command.paymentId)
					?.let { checkoutSessionRepository.findByIdForUpdate(it.id) }
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
