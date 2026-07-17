package paytech.practice.pay.domain.exchange

import java.time.Instant
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.ExchangeRate
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.TokenAmount

/**
 * 거래소 주문(ExchangeOrder) Aggregate Root다.
 *
 * USDC 매도 요청, 거래소 주문번호, 체결 수량, 체결 환율, 확보 KRW를 관리한다.
 * 상태는 이 클래스의 메서드를 통해서만 변경되고, 전이 전 현재 상태를 검증하며,
 * 종료 상태(`COMPLETED`/`FAILED`/`CANCELLED`)는 재사용하지 않는다. `Payment`는
 * ID로만 참조한다.
 *
 * 결제에 적용된 환율(`PaymentQuote.appliedRate`, 향후)과 여기서 관리하는 실제
 * 체결 환율([averageExecutionRate])은 서로 다른 값이다 — 섞어 쓰지 않는다
 * (`docs/domain/glossary.md`).
 *
 * 인스턴스는 [create]로 새로 만들거나 [reconstitute]로 저장된 값을 복원해서 얻는다.
 *
 * @see docs/domain/domain-model.md
 * @see docs/domain/state-transitions.md
 */
class ExchangeOrder private constructor(
	val id: ExchangeOrderId,
	val paymentId: PaymentId,
	val exchangeProviderCode: String,
	val clientOrderId: ClientOrderId,
	val orderSide: OrderSide,
	val baseAsset: Asset,
	val requestedAmount: TokenAmount,
	val requestedAt: Instant,
	providerOrderId: String?,
	status: ExchangeOrderStatus,
	executedAmount: TokenAmount?,
	averageExecutionRate: ExchangeRate?,
	receivedAmount: Money?,
	exchangeFeeAmount: Money?,
	failureCode: String?,
	failureMessage: String?,
	submittedAt: Instant?,
	completedAt: Instant?,
	updatedAt: Instant,
) {

	/** 거래소가 부여한 주문번호. [submit] 전까지는 `null`일 수 있다. */
	var providerOrderId: String? = providerOrderId
		private set

	var status: ExchangeOrderStatus = status
		private set

	/** 실제 체결된 USDC 수량. `COMPLETED` 상태에서는 항상 값이 있다. */
	var executedAmount: TokenAmount? = executedAmount
		private set

	/** 실제 체결 환율. 결제에 적용된 환율과는 별개의 값이다. `COMPLETED` 상태에서는 항상 값이 있다. */
	var averageExecutionRate: ExchangeRate? = averageExecutionRate
		private set

	/** 매도로 확보한 KRW 금액. `COMPLETED` 상태에서는 항상 값이 있다. */
	var receivedAmount: Money? = receivedAmount
		private set

	var exchangeFeeAmount: Money? = exchangeFeeAmount
		private set

	var failureCode: String? = failureCode
		private set

	var failureMessage: String? = failureMessage
		private set

	var submittedAt: Instant? = submittedAt
		private set

	/** 주문이 `COMPLETED`로 확정된 시각. `COMPLETED` 상태에서는 항상 값이 있다. */
	var completedAt: Instant? = completedAt
		private set

	var updatedAt: Instant = updatedAt
		private set

	init {
		require(exchangeProviderCode.isNotBlank()) { "exchangeProviderCode는 공백일 수 없습니다." }
		require(requestedAmount > TokenAmount.ZERO) {
			"requestedAmount는 0보다 커야 합니다: $requestedAmount"
		}
		require(
			status != ExchangeOrderStatus.COMPLETED ||
				(executedAmount != null && averageExecutionRate != null && receivedAmount != null && completedAt != null),
		) {
			"COMPLETED 상태는 executedAmount, averageExecutionRate, receivedAmount, completedAt이 모두 있어야 합니다."
		}
	}

	/** `REQUESTED` → `SUBMITTED`. 거래소에 주문을 제출하고 주문번호를 기록한다. */
	fun submit(providerOrderId: String?, submittedAt: Instant) {
		checkTransition(status == ExchangeOrderStatus.REQUESTED, ExchangeOrderStatus.SUBMITTED)
		this.providerOrderId = providerOrderId
		status = ExchangeOrderStatus.SUBMITTED
		this.submittedAt = submittedAt
		updatedAt = submittedAt
	}

	/** `SUBMITTED` → `PROCESSING`. */
	fun startProcessing(changedAt: Instant) {
		checkTransition(status == ExchangeOrderStatus.SUBMITTED, ExchangeOrderStatus.PROCESSING)
		status = ExchangeOrderStatus.PROCESSING
		updatedAt = changedAt
	}

	/**
	 * (`REQUESTED`, `SUBMITTED` 또는 `PROCESSING`) → `COMPLETED`.
	 *
	 * Fake Exchange MVP는 `REQUESTED`에서 곧바로 이 메서드를 호출해 `SUBMITTED`/
	 * `PROCESSING`을 건너뛴다(`docs/domain/state-transitions.md`).
	 */
	fun complete(
		executedAmount: TokenAmount,
		averageExecutionRate: ExchangeRate,
		receivedAmount: Money,
		exchangeFeeAmount: Money?,
		completedAt: Instant,
	) {
		checkTransition(isInFlight(), ExchangeOrderStatus.COMPLETED)
		status = ExchangeOrderStatus.COMPLETED
		this.executedAmount = executedAmount
		this.averageExecutionRate = averageExecutionRate
		this.receivedAmount = receivedAmount
		this.exchangeFeeAmount = exchangeFeeAmount
		this.completedAt = completedAt
		updatedAt = completedAt
	}

	/** (`REQUESTED`, `SUBMITTED` 또는 `PROCESSING`) → `FAILED`. */
	fun fail(failureCode: String?, failureMessage: String?, failedAt: Instant) {
		checkTransition(isInFlight(), ExchangeOrderStatus.FAILED)
		status = ExchangeOrderStatus.FAILED
		this.failureCode = failureCode
		this.failureMessage = failureMessage
		updatedAt = failedAt
	}

	/**
	 * (`REQUESTED`, `SUBMITTED` 또는 `PROCESSING`) → `CANCELLED`.
	 *
	 * `docs/domain/state-transitions.md`는 이 전이를 명시하지 않지만, DB 스키마의
	 * `exchange_order_status` CHECK 제약이 이미 `CANCELLED`를 나열해 두고 있어
	 * 다른 종료 전 상태와 동일한 조건으로 취급한다.
	 */
	fun cancel(cancelledAt: Instant) {
		checkTransition(isInFlight(), ExchangeOrderStatus.CANCELLED)
		status = ExchangeOrderStatus.CANCELLED
		updatedAt = cancelledAt
	}

	private fun isInFlight(): Boolean =
		status == ExchangeOrderStatus.REQUESTED ||
			status == ExchangeOrderStatus.SUBMITTED ||
			status == ExchangeOrderStatus.PROCESSING

	private fun checkTransition(allowed: Boolean, target: ExchangeOrderStatus) {
		check(allowed) { "ExchangeOrder 상태를 $status 에서 $target (으)로 전이할 수 없습니다." }
	}

	companion object {

		/** 새 거래소 주문을 `REQUESTED` 상태로 생성한다. */
		fun create(
			id: ExchangeOrderId,
			paymentId: PaymentId,
			exchangeProviderCode: String,
			clientOrderId: ClientOrderId,
			orderSide: OrderSide,
			baseAsset: Asset,
			requestedAmount: TokenAmount,
			requestedAt: Instant,
		): ExchangeOrder = ExchangeOrder(
			id = id,
			paymentId = paymentId,
			exchangeProviderCode = exchangeProviderCode,
			clientOrderId = clientOrderId,
			orderSide = orderSide,
			baseAsset = baseAsset,
			requestedAmount = requestedAmount,
			requestedAt = requestedAt,
			providerOrderId = null,
			status = ExchangeOrderStatus.REQUESTED,
			executedAmount = null,
			averageExecutionRate = null,
			receivedAmount = null,
			exchangeFeeAmount = null,
			failureCode = null,
			failureMessage = null,
			submittedAt = null,
			completedAt = null,
			updatedAt = requestedAt,
		)

		/** 영속 계층에 저장되어 있던 값으로 Aggregate를 복원한다. */
		fun reconstitute(
			id: ExchangeOrderId,
			paymentId: PaymentId,
			exchangeProviderCode: String,
			clientOrderId: ClientOrderId,
			orderSide: OrderSide,
			baseAsset: Asset,
			requestedAmount: TokenAmount,
			requestedAt: Instant,
			providerOrderId: String?,
			status: ExchangeOrderStatus,
			executedAmount: TokenAmount?,
			averageExecutionRate: ExchangeRate?,
			receivedAmount: Money?,
			exchangeFeeAmount: Money?,
			failureCode: String?,
			failureMessage: String?,
			submittedAt: Instant?,
			completedAt: Instant?,
			updatedAt: Instant,
		): ExchangeOrder = ExchangeOrder(
			id = id,
			paymentId = paymentId,
			exchangeProviderCode = exchangeProviderCode,
			clientOrderId = clientOrderId,
			orderSide = orderSide,
			baseAsset = baseAsset,
			requestedAmount = requestedAmount,
			requestedAt = requestedAt,
			providerOrderId = providerOrderId,
			status = status,
			executedAmount = executedAmount,
			averageExecutionRate = averageExecutionRate,
			receivedAmount = receivedAmount,
			exchangeFeeAmount = exchangeFeeAmount,
			failureCode = failureCode,
			failureMessage = failureMessage,
			submittedAt = submittedAt,
			completedAt = completedAt,
			updatedAt = updatedAt,
		)
	}
}
