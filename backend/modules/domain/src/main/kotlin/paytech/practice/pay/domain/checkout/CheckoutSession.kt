package paytech.practice.pay.domain.checkout

import java.time.Instant
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.shared.WalletAddress

/**
 * 체크아웃 세션(CheckoutSession) Aggregate Root다.
 *
 * 특정 Payment를 처리하기 위한 유효시간이 제한된 결제 세션이며, 세션 유효성,
 * 화면 진행 상태, 연결된 지갑, Redirect URL을 관리한다. 상태는 이 클래스의
 * 메서드를 통해서만 변경되고, 전이 전 현재 상태를 검증하며, 종료 상태
 * (`COMPLETED`/`EXPIRED`/`CANCELLED`)는 재사용하지 않는다. `Payment`는 ID로만
 * 참조한다.
 *
 * 인스턴스는 [create]로 새로 만들거나 [reconstitute]로 저장된 값을 복원해서 얻는다.
 *
 * @see docs/domain/domain-model.md
 * @see docs/domain/state-transitions.md
 */
class CheckoutSession private constructor(
	val id: CheckoutSessionId,
	val paymentId: PaymentId,
	val successUrl: RedirectUrl,
	val cancelUrl: RedirectUrl?,
	val expiresAt: Instant,
	val createdAt: Instant,
	connectedWallet: WalletAddress?,
	status: CheckoutSessionStatus,
	openedAt: Instant?,
	walletConnectedAt: Instant?,
	paymentSubmittedAt: Instant?,
	completedAt: Instant?,
	updatedAt: Instant,
) {

	/** 고객이 체크아웃에서 연결한 지갑. [connectWallet] 전까지는 `null`이다. */
	var connectedWallet: WalletAddress? = connectedWallet
		private set

	var status: CheckoutSessionStatus = status
		private set

	var openedAt: Instant? = openedAt
		private set

	var walletConnectedAt: Instant? = walletConnectedAt
		private set

	var paymentSubmittedAt: Instant? = paymentSubmittedAt
		private set

	/** 세션이 `COMPLETED`로 확정된 시각. `COMPLETED` 상태에서는 항상 값이 있다. */
	var completedAt: Instant? = completedAt
		private set

	var updatedAt: Instant = updatedAt
		private set

	init {
		require(createdAt.isBefore(expiresAt)) {
			"expiresAt은 createdAt 이후여야 합니다: createdAt=$createdAt, expiresAt=$expiresAt"
		}
		require(status != CheckoutSessionStatus.COMPLETED || completedAt != null) {
			"COMPLETED 상태는 completedAt이 반드시 있어야 합니다."
		}
	}

	/** `CREATED` → `OPEN`. 고객이 체크아웃 페이지를 열었다. */
	fun open(openedAt: Instant) {
		checkTransition(status == CheckoutSessionStatus.CREATED, CheckoutSessionStatus.OPEN)
		status = CheckoutSessionStatus.OPEN
		this.openedAt = openedAt
		updatedAt = openedAt
	}

	/** `OPEN` → `WALLET_CONNECTED`. 고객이 외부 지갑을 연결했다. */
	fun connectWallet(wallet: WalletAddress, connectedAt: Instant) {
		checkTransition(status == CheckoutSessionStatus.OPEN, CheckoutSessionStatus.WALLET_CONNECTED)
		connectedWallet = wallet
		status = CheckoutSessionStatus.WALLET_CONNECTED
		walletConnectedAt = connectedAt
		updatedAt = connectedAt
	}

	/** `WALLET_CONNECTED` → `PAYMENT_SUBMITTED`. 이후로는 고객 취소를 허용하지 않는다. */
	fun submitPayment(submittedAt: Instant) {
		checkTransition(status == CheckoutSessionStatus.WALLET_CONNECTED, CheckoutSessionStatus.PAYMENT_SUBMITTED)
		status = CheckoutSessionStatus.PAYMENT_SUBMITTED
		paymentSubmittedAt = submittedAt
		updatedAt = submittedAt
	}

	/** `PAYMENT_SUBMITTED` → `COMPLETED`. */
	fun complete(completedAt: Instant) {
		checkTransition(status == CheckoutSessionStatus.PAYMENT_SUBMITTED, CheckoutSessionStatus.COMPLETED)
		status = CheckoutSessionStatus.COMPLETED
		this.completedAt = completedAt
		updatedAt = completedAt
	}

	/**
	 * (`CREATED`, `OPEN` 또는 `WALLET_CONNECTED`) → `CANCELLED`.
	 *
	 * `PAYMENT_SUBMITTED` 이후에는 고객 취소를 허용하지 않는다(`docs/domain/state-transitions.md`).
	 */
	fun cancel(cancelledAt: Instant) {
		checkTransition(isBeforePaymentSubmitted(), CheckoutSessionStatus.CANCELLED)
		status = CheckoutSessionStatus.CANCELLED
		updatedAt = cancelledAt
	}

	/**
	 * (`CREATED`, `OPEN` 또는 `WALLET_CONNECTED`) → `EXPIRED`.
	 *
	 * `docs/domain/state-transitions.md`는 "PAYMENT_SUBMITTED 이후 고객 취소를
	 * 허용하지 않는다"만 명시하고 만료를 별도로 다루지 않는다 — 여기서는 취소와
	 * 동일하게 `PAYMENT_SUBMITTED` 이후에는 만료도 허용하지 않는 것으로 해석한다.
	 * 제출 이후의 완료 여부는 온체인 확인(Payment 쪽)이 결정하기 때문이다.
	 */
	fun expire(expiredAt: Instant) {
		checkTransition(isBeforePaymentSubmitted(), CheckoutSessionStatus.EXPIRED)
		status = CheckoutSessionStatus.EXPIRED
		updatedAt = expiredAt
	}

	private fun isBeforePaymentSubmitted(): Boolean =
		status == CheckoutSessionStatus.CREATED ||
			status == CheckoutSessionStatus.OPEN ||
			status == CheckoutSessionStatus.WALLET_CONNECTED

	private fun checkTransition(allowed: Boolean, target: CheckoutSessionStatus) {
		check(allowed) { "CheckoutSession 상태를 $status 에서 $target (으)로 전이할 수 없습니다." }
	}

	companion object {

		/** 새 체크아웃 세션을 `CREATED` 상태로 생성한다. */
		fun create(
			id: CheckoutSessionId,
			paymentId: PaymentId,
			successUrl: RedirectUrl,
			cancelUrl: RedirectUrl?,
			expiresAt: Instant,
			createdAt: Instant,
		): CheckoutSession = CheckoutSession(
			id = id,
			paymentId = paymentId,
			successUrl = successUrl,
			cancelUrl = cancelUrl,
			expiresAt = expiresAt,
			createdAt = createdAt,
			connectedWallet = null,
			status = CheckoutSessionStatus.CREATED,
			openedAt = null,
			walletConnectedAt = null,
			paymentSubmittedAt = null,
			completedAt = null,
			updatedAt = createdAt,
		)

		/** 영속 계층에 저장되어 있던 값으로 Aggregate를 복원한다. */
		fun reconstitute(
			id: CheckoutSessionId,
			paymentId: PaymentId,
			successUrl: RedirectUrl,
			cancelUrl: RedirectUrl?,
			expiresAt: Instant,
			createdAt: Instant,
			connectedWallet: WalletAddress?,
			status: CheckoutSessionStatus,
			openedAt: Instant?,
			walletConnectedAt: Instant?,
			paymentSubmittedAt: Instant?,
			completedAt: Instant?,
			updatedAt: Instant,
		): CheckoutSession = CheckoutSession(
			id = id,
			paymentId = paymentId,
			successUrl = successUrl,
			cancelUrl = cancelUrl,
			expiresAt = expiresAt,
			createdAt = createdAt,
			connectedWallet = connectedWallet,
			status = status,
			openedAt = openedAt,
			walletConnectedAt = walletConnectedAt,
			paymentSubmittedAt = paymentSubmittedAt,
			completedAt = completedAt,
			updatedAt = updatedAt,
		)
	}
}
