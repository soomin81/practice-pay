package paytech.practice.pay.domain.settlement

import paytech.practice.pay.domain.exchange.ExchangeOrderId
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.SignedMoney
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * 정산 대상(SettlementReceivable) Aggregate Root다.
 *
 * 결제 단위 정산 기준 금액, 수수료, 조정 금액, 정산 예정 금액과 정산 가능 상태를
 * 관리한다. 향후 정산 배치가 소비할 결제 단위 정산 원천 데이터이며, MVP 최종
 * 상태는 `READY`다(`docs/domain/glossary.md`, ADR-005). 상태는 이 클래스의
 * 메서드를 통해서만 변경되고, 전이 전 현재 상태를 검증한다. `Payment`, `Merchant`,
 * `ExchangeOrder`는 모두 ID로만 참조한다.
 *
 * `ASSIGNED`/`SETTLED`는 가맹점 단위 집계 정산(`Settlement`, 향후 Aggregate)이
 * 생겨야 의미가 있는 상태라 이 Aggregate에는 그 상태로 가는 전이 메서드가 없다
 * (ADR-005: "가맹점 단위 집계, 정산 배치... 후속 단계로 미룬다").
 *
 * 인스턴스는 [create]로 새로 만들거나 [reconstitute]로 저장된 값을 복원해서 얻는다.
 *
 * @see docs/domain/domain-model.md
 * @see docs/domain/state-transitions.md
 */
class SettlementReceivable private constructor(
	val id: SettlementReceivableId,
	val paymentId: PaymentId,
	val merchantId: MerchantId,
	val grossAmount: Money,
	val feeRate: BigDecimal,
	val feeAmount: Money,
	val adjustmentAmount: SignedMoney,
	val netAmount: Money,
	val eligibleDate: LocalDate,
	val createdAt: Instant,
	exchangeOrderId: ExchangeOrderId?,
	exchangeReceivedAmount: Money?,
	exchangeProfitLossAmount: SignedMoney?,
	status: SettlementReceivableStatus,
	holdReasonCode: String?,
	updatedAt: Instant,
) {
	/** 이 정산 대상을 확정한 `ExchangeOrder`. [markReady] 전까지는 `null`이다. */
	var exchangeOrderId: ExchangeOrderId? = exchangeOrderId
		private set

	var exchangeReceivedAmount: Money? = exchangeReceivedAmount
		private set

	var exchangeProfitLossAmount: SignedMoney? = exchangeProfitLossAmount
		private set

	var status: SettlementReceivableStatus = status
		private set

	var holdReasonCode: String? = holdReasonCode
		private set

	var updatedAt: Instant = updatedAt
		private set

	init {
		require(grossAmount.amount > 0) { "grossAmount는 0보다 커야 합니다: $grossAmount" }
		require(feeRate >= BigDecimal.ZERO) { "feeRate는 음수일 수 없습니다: $feeRate" }
		require(netAmount.amount == grossAmount.amount - feeAmount.amount + adjustmentAmount.amount) {
			"net_amount 공식이 맞지 않습니다: netAmount=$netAmount, grossAmount=$grossAmount, " +
				"feeAmount=$feeAmount, adjustmentAmount=$adjustmentAmount"
		}
		require(status != SettlementReceivableStatus.READY || exchangeOrderId != null) {
			"READY 상태는 exchangeOrderId가 반드시 있어야 합니다."
		}
	}

	/**
	 * `PENDING` → `READY`. Fake Exchange 매도가 완료되어 정산이 확정되었다
	 * (`docs/architecture/persistence-jooq.md`의 "환전 완료" 트랜잭션 경계 참고).
	 */
	fun markReady(
		exchangeOrderId: ExchangeOrderId,
		exchangeReceivedAmount: Money,
		exchangeProfitLossAmount: SignedMoney?,
		changedAt: Instant,
	) {
		checkTransition(status == SettlementReceivableStatus.PENDING, SettlementReceivableStatus.READY)
		this.exchangeOrderId = exchangeOrderId
		this.exchangeReceivedAmount = exchangeReceivedAmount
		this.exchangeProfitLossAmount = exchangeProfitLossAmount
		status = SettlementReceivableStatus.READY
		updatedAt = changedAt
	}

	/**
	 * (`PENDING` 또는 `READY`) → `HELD`. 정산 보류 사유를 기록한다.
	 *
	 * `docs/domain/state-transitions.md`는 이 전이를 명시하지 않지만
	 * `hold_reason_code` 컬럼이 MVP 스키마에 이미 있어 지원한다.
	 */
	fun hold(
		reasonCode: String,
		changedAt: Instant,
	) {
		checkTransition(
			status == SettlementReceivableStatus.PENDING || status == SettlementReceivableStatus.READY,
			SettlementReceivableStatus.HELD,
		)
		require(reasonCode.isNotBlank()) { "reasonCode는 공백일 수 없습니다." }
		status = SettlementReceivableStatus.HELD
		holdReasonCode = reasonCode
		updatedAt = changedAt
	}

	/**
	 * (`PENDING`, `READY` 또는 `HELD`) → `CANCELLED`.
	 *
	 * `docs/domain/state-transitions.md`는 이 전이도 명시하지 않지만 스키마
	 * CHECK 제약이 이미 `CANCELLED`를 나열해 두고 있다.
	 */
	fun cancel(cancelledAt: Instant) {
		checkTransition(
			status == SettlementReceivableStatus.PENDING ||
				status == SettlementReceivableStatus.READY ||
				status == SettlementReceivableStatus.HELD,
			SettlementReceivableStatus.CANCELLED,
		)
		status = SettlementReceivableStatus.CANCELLED
		updatedAt = cancelledAt
	}

	private fun checkTransition(
		allowed: Boolean,
		target: SettlementReceivableStatus,
	) {
		check(allowed) { "SettlementReceivable 상태를 $status 에서 $target (으)로 전이할 수 없습니다." }
	}

	companion object {
		/**
		 * 새 정산 대상을 `PENDING` 상태로 생성한다.
		 *
		 * `netAmount`는 `grossAmount - feeAmount + adjustmentAmount` 공식으로 직접
		 * 계산한다 — 호출부가 잘못된 값을 넘길 여지를 없앤다. 실제 금액 계산 자체
		 * (Gross/Fee/Adjustment 산정)는 `SettlementAmountCalculator`(도메인 서비스,
		 * `docs/domain/domain-model.md`)의 책임이며 이 Aggregate의 범위 밖이다.
		 */
		fun create(
			id: SettlementReceivableId,
			paymentId: PaymentId,
			merchantId: MerchantId,
			grossAmount: Money,
			feeRate: BigDecimal,
			feeAmount: Money,
			adjustmentAmount: SignedMoney,
			eligibleDate: LocalDate,
			createdAt: Instant,
		): SettlementReceivable {
			val netAmount = Money(grossAmount.amount - feeAmount.amount + adjustmentAmount.amount)
			return SettlementReceivable(
				id = id,
				paymentId = paymentId,
				merchantId = merchantId,
				grossAmount = grossAmount,
				feeRate = feeRate,
				feeAmount = feeAmount,
				adjustmentAmount = adjustmentAmount,
				netAmount = netAmount,
				eligibleDate = eligibleDate,
				createdAt = createdAt,
				exchangeOrderId = null,
				exchangeReceivedAmount = null,
				exchangeProfitLossAmount = null,
				status = SettlementReceivableStatus.PENDING,
				holdReasonCode = null,
				updatedAt = createdAt,
			)
		}

		/** 영속 계층에 저장되어 있던 값으로 Aggregate를 복원한다. */
		fun reconstitute(
			id: SettlementReceivableId,
			paymentId: PaymentId,
			merchantId: MerchantId,
			grossAmount: Money,
			feeRate: BigDecimal,
			feeAmount: Money,
			adjustmentAmount: SignedMoney,
			netAmount: Money,
			eligibleDate: LocalDate,
			createdAt: Instant,
			exchangeOrderId: ExchangeOrderId?,
			exchangeReceivedAmount: Money?,
			exchangeProfitLossAmount: SignedMoney?,
			status: SettlementReceivableStatus,
			holdReasonCode: String?,
			updatedAt: Instant,
		): SettlementReceivable =
			SettlementReceivable(
				id = id,
				paymentId = paymentId,
				merchantId = merchantId,
				grossAmount = grossAmount,
				feeRate = feeRate,
				feeAmount = feeAmount,
				adjustmentAmount = adjustmentAmount,
				netAmount = netAmount,
				eligibleDate = eligibleDate,
				createdAt = createdAt,
				exchangeOrderId = exchangeOrderId,
				exchangeReceivedAmount = exchangeReceivedAmount,
				exchangeProfitLossAmount = exchangeProfitLossAmount,
				status = status,
				holdReasonCode = holdReasonCode,
				updatedAt = updatedAt,
			)
	}
}
