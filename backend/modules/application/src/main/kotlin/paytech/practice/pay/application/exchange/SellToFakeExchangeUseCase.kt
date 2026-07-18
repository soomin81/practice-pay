package paytech.practice.pay.application.exchange

import paytech.practice.pay.application.port.outbound.ExchangeOrderRepository
import paytech.practice.pay.application.port.outbound.ExchangeRateProvider
import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.OutboxEventRepository
import paytech.practice.pay.application.port.outbound.PaymentRepository
import paytech.practice.pay.application.port.outbound.SettlementReceivableRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.domain.exchange.ClientOrderId
import paytech.practice.pay.domain.exchange.ExchangeOrder
import paytech.practice.pay.domain.exchange.ExchangeOrderId
import paytech.practice.pay.domain.exchange.OrderSide
import paytech.practice.pay.domain.outbox.OutboxEvent
import paytech.practice.pay.domain.payment.Payment
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus
import paytech.practice.pay.domain.settlement.SettlementReceivable
import paytech.practice.pay.domain.settlement.SettlementReceivableId
import paytech.practice.pay.domain.shared.EventId
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.SignedMoney
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate

/**
 * "Fake Exchange 매도" Use Case다. `docs/architecture/mvp-scope.md`의 전체 흐름 중
 * `Payment SUCCEEDED → 결제 완료 페이지와 Webhook → Fake Exchange 매도 →
 * SettlementReceivable READY` 구간과, `docs/architecture/persistence-jooq.md`가
 * 정의한 "환전 완료" 트랜잭션 경계(`ExchangeOrder COMPLETED + SettlementReceivable
 * READY + OutboxEvent`)를 구현한다.
 *
 * 이미 `SUCCEEDED`인 Payment 하나를 대상으로 한 매도 시도 한 번이다 —
 * `ConfirmBlockchainTransactionUseCase`/`PublishOutboxEventUseCase`와 같은 모양이다.
 * 이 Use Case 자체는 스스로 반복하지 않는다 — `PaymentRepository.
 * findPendingExchangeSettlement()`가 암시하는 대로, `apps:batch`의 Worker가 대상
 * 목록을 뽑아 하나씩 호출하는 것을 전제로 설계했다.
 *
 * **Fake Exchange는 `ExchangeOrder.create()` 직후 곧바로 `complete()`를 호출해
 * `SUBMITTED`/`PROCESSING`을 건너뛴다**(`ExchangeOrder.complete`의 KDoc, ADR-004).
 * `clientOrderId`는 `paymentId`로부터 결정론적으로 만든다 — 같은 Payment로 이
 * Use Case가 재호출돼도 항상 같은 값이라 `uk_exchange_client_order_id` Unique
 * 제약과 충돌하지 않는다.
 *
 * **Gross/Fee/Adjustment 금액 계산을 이 Use Case에 인라인했다.**
 * `docs/domain/domain-model.md`는 이 계산을 `SettlementAmountCalculator`라는
 * 별도 Domain Service로 분류하지만, 그 옆에 나열된 `PaymentAmountCalculator`
 * (KRW→USDC 변환)도 실제로는 별도 파일 없이 `CreatePaymentUseCase`에 인라인돼
 * 있다 — 그 기존 선례를 그대로 따라 새 파일을 만들지 않았다. `grossAmount`는
 * 정산 기준 금액이라 정의상 매도 시점이 아니라 원래 주문 시점 KRW 금액
 * (`Payment.orderAmount`)을 그대로 쓴다 — 시장 환율이 결제 시점과 매도 시점
 * 사이에 움직인 차이는 `SettlementReceivable.exchangeProfitLossAmount`(매도로
 * 실제 확보한 KRW − grossAmount)에 담긴다.
 *
 * [SETTLEMENT_FEE_RATE]도 `docs/`에 값이 없어 이 Use Case가 상수로 고정했다 —
 * `CreatePaymentUseCase`의 `SPREAD_RATE`와 같은 성격이다.
 */
class SellToFakeExchangeUseCase(
	private val paymentRepository: PaymentRepository,
	private val exchangeOrderRepository: ExchangeOrderRepository,
	private val settlementReceivableRepository: SettlementReceivableRepository,
	private val outboxEventRepository: OutboxEventRepository,
	private val exchangeRateProvider: ExchangeRateProvider,
	private val idGenerator: IdGenerator,
	private val transactionManager: TransactionManager,
	private val clock: Clock,
) {
	fun execute(command: SellToFakeExchangeCommand): SellToFakeExchangeResult {
		val payment =
			paymentRepository.findById(command.paymentId)
				?: throw PaymentNotFoundException(command.paymentId)
		check(payment.status == PaymentStatus.SUCCEEDED) {
			"Payment(${payment.id.value})가 SUCCEEDED 상태가 아닙니다: ${payment.status}"
		}

		findExistingResult(payment.id)?.let { return it }

		val now = clock.instant()
		val marketQuote = exchangeRateProvider.currentRate()

		val exchangeOrder =
			ExchangeOrder.create(
				id = ExchangeOrderId("exo_" + idGenerator.newId()),
				paymentId = payment.id,
				exchangeProviderCode = marketQuote.providerCode,
				clientOrderId = ClientOrderId("sell_${payment.id.value}"),
				orderSide = OrderSide.SELL,
				baseAsset = payment.paymentAsset,
				requestedAmount = payment.paymentAmount,
				requestedAt = now,
			)
		val receivedAmount = toKrw(payment, marketQuote.rate.value)
		exchangeOrder.complete(
			executedAmount = payment.paymentAmount,
			averageExecutionRate = marketQuote.rate,
			receivedAmount = receivedAmount,
			exchangeFeeAmount = null,
			completedAt = now,
		)

		val grossAmount = payment.orderAmount
		val feeAmount =
			Money(
				grossAmount.amount
					.toBigDecimal()
					.multiply(SETTLEMENT_FEE_RATE)
					.setScale(0, RoundingMode.HALF_UP)
					.toLong(),
			)
		val settlementReceivable =
			SettlementReceivable.create(
				id = SettlementReceivableId("stl_" + idGenerator.newId()),
				paymentId = payment.id,
				merchantId = payment.merchantId,
				grossAmount = grossAmount,
				feeRate = SETTLEMENT_FEE_RATE,
				feeAmount = feeAmount,
				adjustmentAmount = SignedMoney.ZERO,
				eligibleDate = LocalDate.now(clock),
				createdAt = now,
			)
		settlementReceivable.markReady(
			exchangeOrderId = exchangeOrder.id,
			exchangeReceivedAmount = receivedAmount,
			exchangeProfitLossAmount = SignedMoney(receivedAmount.amount - grossAmount.amount),
			changedAt = now,
		)

		val outboxEvent =
			OutboxEvent.create(
				eventId = EventId("evt_" + idGenerator.newId()),
				aggregateType = "Payment",
				aggregateId = payment.id.value,
				eventType = PAYMENT_SETTLED_EVENT_TYPE,
				payload = paymentSettledPayload(payment, settlementReceivable),
				occurredAt = now,
				createdAt = now,
			)

		return transactionManager.runInTransaction {
			exchangeOrderRepository.save(exchangeOrder)
			settlementReceivableRepository.save(settlementReceivable)
			outboxEventRepository.save(outboxEvent)
			resultOf(payment.id, exchangeOrder, settlementReceivable)
		}
	}

	private fun findExistingResult(paymentId: PaymentId): SellToFakeExchangeResult? {
		val existingExchangeOrder = exchangeOrderRepository.findByPaymentId(paymentId) ?: return null
		val existingSettlement =
			settlementReceivableRepository.findByPaymentId(paymentId)
				?: error("Payment(${paymentId.value})에 딸린 SettlementReceivable이 없습니다 — 환전 완료 트랜잭션이 원자적이지 않았습니다.")
		return resultOf(paymentId, existingExchangeOrder, existingSettlement)
	}

	private fun toKrw(
		payment: Payment,
		rate: BigDecimal,
	): Money =
		Money(
			payment.paymentAmount.amountMinor
				.toBigDecimal()
				.movePointLeft(payment.tokenDecimals)
				.multiply(rate)
				.setScale(0, RoundingMode.HALF_UP)
				.toLong(),
		)

	private fun resultOf(
		paymentId: PaymentId,
		exchangeOrder: ExchangeOrder,
		settlementReceivable: SettlementReceivable,
	): SellToFakeExchangeResult =
		SellToFakeExchangeResult(
			paymentId = paymentId,
			exchangeOrderId = exchangeOrder.id,
			exchangeOrderStatus = exchangeOrder.status,
			settlementReceivableId = settlementReceivable.id,
			settlementReceivableStatus = settlementReceivable.status,
		)

	private fun paymentSettledPayload(
		payment: Payment,
		settlementReceivable: SettlementReceivable,
	): String =
		"""{"paymentId":"${payment.id.value}","merchantOrderId":"${payment.merchantOrderId.value}",""" +
			""""settlementReceivableId":"${settlementReceivable.id.value}","netAmount":${settlementReceivable.netAmount.amount}}"""

	companion object {
		/** MVP 정산 수수료율(1.5%). `docs/`에 값이 없어 고정한 MVP 상수다. */
		private val SETTLEMENT_FEE_RATE = BigDecimal("0.015")

		private const val PAYMENT_SETTLED_EVENT_TYPE = "payment.settled"
	}
}
