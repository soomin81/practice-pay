package paytech.practice.pay.domain.payment

import java.time.Instant
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.TokenAmount
import paytech.practice.pay.domain.shared.WalletAddress

/**
 * 결제(Payment) Aggregate Root다.
 *
 * 가맹점 주문에 대한 PG 비즈니스 결제 단위이며, 주문 금액, USDC 결제 금액, 네트워크,
 * 수취 지갑, 상태, 만료와 완료를 관리한다. 상태는 이 클래스의 메서드를 통해서만
 * 변경되고, 전이 전 현재 상태를 검증하며, 종료 상태(`SUCCEEDED`/`EXPIRED`/`FAILED`)는
 * 재사용하지 않는다. 다른 Aggregate(Merchant 등)는 ID로만 참조한다.
 *
 * 인스턴스는 [create]로 새로 만들거나 [reconstitute]로 저장된 값을 복원해서 얻는다.
 *
 * @see docs/domain/domain-model.md
 * @see docs/domain/state-transitions.md
 */
class Payment private constructor(
	val id: PaymentId,
	val merchantId: MerchantId,
	val merchantOrderId: MerchantOrderId,
	val orderName: String,
	val orderAmount: Money,
	val paymentAsset: Asset,
	val paymentAmount: TokenAmount,
	val tokenDecimals: Int,
	val network: BlockchainNetwork,
	val receivingWallet: WalletAddress,
	val expiresAt: Instant,
	val createdAt: Instant,
	customerWallet: WalletAddress?,
	status: PaymentStatus,
	failureReason: PaymentFailureReason?,
	failureMessage: String?,
	paidAt: Instant?,
	updatedAt: Instant,
) {

	/** 고객이 체크아웃에서 연결한 지갑. [submit]으로 결제를 제출하기 전까지는 `null`이다. */
	var customerWallet: WalletAddress? = customerWallet
		private set

	var status: PaymentStatus = status
		private set

	var failureReason: PaymentFailureReason? = failureReason
		private set

	var failureMessage: String? = failureMessage
		private set

	/** 결제가 `SUCCEEDED`로 확정된 시각. `SUCCEEDED` 상태에서는 항상 값이 있다. */
	var paidAt: Instant? = paidAt
		private set

	var updatedAt: Instant = updatedAt
		private set

	init {
		require(orderName.isNotBlank()) { "orderName은 공백일 수 없습니다." }
		require(tokenDecimals >= 0) { "tokenDecimals는 음수일 수 없습니다: $tokenDecimals" }
		require(createdAt.isBefore(expiresAt)) {
			"expiresAt은 createdAt 이후여야 합니다: createdAt=$createdAt, expiresAt=$expiresAt"
		}
		require(status != PaymentStatus.SUCCEEDED || paidAt != null) {
			"SUCCEEDED 상태는 paidAt이 반드시 있어야 합니다."
		}
	}

	/** `CREATED` → `READY`. */
	fun ready(changedAt: Instant) {
		checkTransition(status == PaymentStatus.CREATED, PaymentStatus.READY)
		status = PaymentStatus.READY
		updatedAt = changedAt
	}

	/** `READY` → `PROCESSING`. 고객 지갑 연결과 결제 제출을 함께 기록한다. */
	fun submit(wallet: WalletAddress, submittedAt: Instant) {
		checkTransition(status == PaymentStatus.READY, PaymentStatus.PROCESSING)
		customerWallet = wallet
		status = PaymentStatus.PROCESSING
		updatedAt = submittedAt
	}

	/** `PROCESSING` → `CONFIRMING`. 온체인 거래가 감지되어 Confirm 대기 상태로 전이한다. */
	fun startConfirmation(changedAt: Instant) {
		checkTransition(status == PaymentStatus.PROCESSING, PaymentStatus.CONFIRMING)
		status = PaymentStatus.CONFIRMING
		updatedAt = changedAt
	}

	/**
	 * `CONFIRMING` → `SUCCEEDED`.
	 *
	 * 온체인 검증(Network/Contract/수취 지갑/금액/Receipt/Confirmation/중복 여부)은
	 * 이 메서드를 호출하기 전에 이미 끝나 있어야 한다(`PaymentTransactionValidator`
	 * 같은 도메인 서비스의 책임). Payment 자신은 그 결과를 다시 검증하지 않고
	 * 상태만 전이한다.
	 */
	fun succeed(paidAt: Instant) {
		checkTransition(status == PaymentStatus.CONFIRMING, PaymentStatus.SUCCEEDED)
		status = PaymentStatus.SUCCEEDED
		this.paidAt = paidAt
		updatedAt = paidAt
	}

	/** (`CREATED` 또는 `READY`) → `EXPIRED`. */
	fun expire(expiredAt: Instant) {
		checkTransition(
			status == PaymentStatus.CREATED || status == PaymentStatus.READY,
			PaymentStatus.EXPIRED,
		)
		status = PaymentStatus.EXPIRED
		updatedAt = expiredAt
	}

	/** (`PROCESSING` 또는 `CONFIRMING`) → `FAILED`. */
	fun fail(reason: PaymentFailureReason, failedAt: Instant) {
		checkTransition(
			status == PaymentStatus.PROCESSING || status == PaymentStatus.CONFIRMING,
			PaymentStatus.FAILED,
		)
		status = PaymentStatus.FAILED
		failureReason = reason
		updatedAt = failedAt
	}

	private fun checkTransition(allowed: Boolean, target: PaymentStatus) {
		check(allowed) { "Payment 상태를 $status 에서 $target (으)로 전이할 수 없습니다." }
	}

	companion object {

		/** 새 결제를 `CREATED` 상태로 생성한다. */
		fun create(
			id: PaymentId,
			merchantId: MerchantId,
			merchantOrderId: MerchantOrderId,
			orderName: String,
			orderAmount: Money,
			paymentAsset: Asset,
			paymentAmount: TokenAmount,
			tokenDecimals: Int,
			network: BlockchainNetwork,
			receivingWallet: WalletAddress,
			expiresAt: Instant,
			createdAt: Instant,
		): Payment = Payment(
			id = id,
			merchantId = merchantId,
			merchantOrderId = merchantOrderId,
			orderName = orderName,
			orderAmount = orderAmount,
			paymentAsset = paymentAsset,
			paymentAmount = paymentAmount,
			tokenDecimals = tokenDecimals,
			network = network,
			receivingWallet = receivingWallet,
			expiresAt = expiresAt,
			createdAt = createdAt,
			customerWallet = null,
			status = PaymentStatus.CREATED,
			failureReason = null,
			failureMessage = null,
			paidAt = null,
			updatedAt = createdAt,
		)

		/** 영속 계층에 저장되어 있던 값으로 Aggregate를 복원한다. */
		fun reconstitute(
			id: PaymentId,
			merchantId: MerchantId,
			merchantOrderId: MerchantOrderId,
			orderName: String,
			orderAmount: Money,
			paymentAsset: Asset,
			paymentAmount: TokenAmount,
			tokenDecimals: Int,
			network: BlockchainNetwork,
			receivingWallet: WalletAddress,
			expiresAt: Instant,
			createdAt: Instant,
			customerWallet: WalletAddress?,
			status: PaymentStatus,
			failureReason: PaymentFailureReason?,
			failureMessage: String?,
			paidAt: Instant?,
			updatedAt: Instant,
		): Payment = Payment(
			id = id,
			merchantId = merchantId,
			merchantOrderId = merchantOrderId,
			orderName = orderName,
			orderAmount = orderAmount,
			paymentAsset = paymentAsset,
			paymentAmount = paymentAmount,
			tokenDecimals = tokenDecimals,
			network = network,
			receivingWallet = receivingWallet,
			expiresAt = expiresAt,
			createdAt = createdAt,
			customerWallet = customerWallet,
			status = status,
			failureReason = failureReason,
			failureMessage = failureMessage,
			paidAt = paidAt,
			updatedAt = updatedAt,
		)
	}
}
