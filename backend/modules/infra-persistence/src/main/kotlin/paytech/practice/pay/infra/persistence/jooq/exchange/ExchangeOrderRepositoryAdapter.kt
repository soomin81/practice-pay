package paytech.practice.pay.infra.persistence.jooq.exchange

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import paytech.practice.pay.application.port.outbound.ExchangeOrderRepository
import paytech.practice.pay.dbcore.jooq.tables.ExchangeOrder.Companion.EXCHANGE_ORDER
import paytech.practice.pay.dbcore.jooq.tables.Payment.Companion.PAYMENT
import paytech.practice.pay.dbcore.jooq.tables.records.ExchangeOrderRecord
import paytech.practice.pay.domain.exchange.ClientOrderId
import paytech.practice.pay.domain.exchange.ExchangeOrder
import paytech.practice.pay.domain.exchange.ExchangeOrderId
import paytech.practice.pay.domain.exchange.ExchangeOrderStatus
import paytech.practice.pay.domain.exchange.OrderSide
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.ExchangeRate
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.TokenAmount
import paytech.practice.pay.infra.persistence.jooq.toUtcInstant
import paytech.practice.pay.infra.persistence.jooq.toUtcLocalDateTime

/**
 * jOOQ로 [ExchangeOrderRepository] Port를 구현한다.
 *
 * `save`의 낙관적 잠금 한계는 [paytech.practice.pay.infra.persistence.jooq.payment.PaymentRepositoryAdapter]와
 * 동일하다 — 도메인 [ExchangeOrder]가 자신의 `version`을 모르기 때문에, DB에서
 * 방금 읽은 version을 그대로 +1 해서 쓴다.
 *
 * `quote_currency` 컬럼은 도메인에 대응 값이 없어(MVP는 KRW→USDC 한 쌍만 지원)
 * `"KRW"` 리터럴로 채운다 — `PaymentRepositoryAdapter`의 `order_currency`
 * 하드코딩과 같은 이유다.
 */
@Repository
class ExchangeOrderRepositoryAdapter(
	private val dsl: DSLContext,
) : ExchangeOrderRepository {
	override fun save(exchangeOrder: ExchangeOrder) {
		val existing =
			dsl
				.selectFrom(EXCHANGE_ORDER)
				.where(EXCHANGE_ORDER.EXCHANGE_ORDER_ID.eq(exchangeOrder.id.value))
				.fetchOne()

		if (existing == null) {
			dsl
				.newRecord(EXCHANGE_ORDER)
				.apply {
					fillFrom(exchangeOrder)
					version = 0L
				}.insert()
		} else {
			dsl
				.update(EXCHANGE_ORDER)
				.set(EXCHANGE_ORDER.PROVIDER_ORDER_ID, exchangeOrder.providerOrderId)
				.set(EXCHANGE_ORDER.EXECUTED_AMOUNT_MINOR, exchangeOrder.executedAmount?.amountMinor)
				.set(EXCHANGE_ORDER.AVERAGE_EXECUTION_RATE, exchangeOrder.averageExecutionRate?.value)
				.set(EXCHANGE_ORDER.RECEIVED_AMOUNT, exchangeOrder.receivedAmount?.amount)
				.set(EXCHANGE_ORDER.EXCHANGE_FEE_AMOUNT, exchangeOrder.exchangeFeeAmount?.amount)
				.set(EXCHANGE_ORDER.EXCHANGE_ORDER_STATUS, exchangeOrder.status.name)
				.set(EXCHANGE_ORDER.FAILURE_CODE, exchangeOrder.failureCode)
				.set(EXCHANGE_ORDER.FAILURE_MESSAGE, exchangeOrder.failureMessage)
				.set(EXCHANGE_ORDER.SUBMITTED_AT, exchangeOrder.submittedAt?.toUtcLocalDateTime())
				.set(EXCHANGE_ORDER.COMPLETED_AT, exchangeOrder.completedAt?.toUtcLocalDateTime())
				.set(EXCHANGE_ORDER.UPDATED_AT, exchangeOrder.updatedAt.toUtcLocalDateTime())
				.set(EXCHANGE_ORDER.VERSION, (existing.version ?: 0L) + 1)
				.where(EXCHANGE_ORDER.EXCHANGE_ORDER_SEQ.eq(existing.exchangeOrderSeq))
				.and(EXCHANGE_ORDER.VERSION.eq(existing.version))
				.execute()
				.also { updatedRows ->
					check(updatedRows == 1) {
						"ExchangeOrder(${exchangeOrder.id.value}) 저장에 실패했습니다 — " +
							"동시에 변경된 것으로 보입니다(예상 version=${existing.version})."
					}
				}
		}
	}

	override fun findByPaymentId(paymentId: PaymentId): ExchangeOrder? =
		dsl
			.selectFrom(EXCHANGE_ORDER)
			.where(EXCHANGE_ORDER.PAYMENT_SEQ.eq(resolvePaymentSeq(paymentId)))
			.fetchOne()
			?.toDomain(paymentId)

	private fun resolvePaymentSeq(paymentId: PaymentId): Long =
		dsl
			.select(PAYMENT.PAYMENT_SEQ)
			.from(PAYMENT)
			.where(PAYMENT.PAYMENT_ID.eq(paymentId.value))
			.fetchOne(PAYMENT.PAYMENT_SEQ)
			?: error("Payment(${paymentId.value})를 찾을 수 없습니다.")

	private fun ExchangeOrderRecord.fillFrom(exchangeOrder: ExchangeOrder) {
		exchangeOrderId = exchangeOrder.id.value
		paymentSeq = resolvePaymentSeq(exchangeOrder.paymentId)
		exchangeProviderCode = exchangeOrder.exchangeProviderCode
		clientOrderId = exchangeOrder.clientOrderId.value
		providerOrderId = exchangeOrder.providerOrderId
		orderSide = exchangeOrder.orderSide.name
		baseAssetCode = exchangeOrder.baseAsset.code
		quoteCurrency = "KRW"
		requestedAmountMinor = exchangeOrder.requestedAmount.amountMinor
		executedAmountMinor = exchangeOrder.executedAmount?.amountMinor
		averageExecutionRate = exchangeOrder.averageExecutionRate?.value
		receivedAmount = exchangeOrder.receivedAmount?.amount
		exchangeFeeAmount = exchangeOrder.exchangeFeeAmount?.amount
		exchangeOrderStatus = exchangeOrder.status.name
		failureCode = exchangeOrder.failureCode
		failureMessage = exchangeOrder.failureMessage
		requestedAt = exchangeOrder.requestedAt.toUtcLocalDateTime()
		submittedAt = exchangeOrder.submittedAt?.toUtcLocalDateTime()
		completedAt = exchangeOrder.completedAt?.toUtcLocalDateTime()
		createdAt = exchangeOrder.requestedAt.toUtcLocalDateTime()
		updatedAt = exchangeOrder.updatedAt.toUtcLocalDateTime()
	}

	private fun ExchangeOrderRecord.toDomain(paymentId: PaymentId): ExchangeOrder =
		ExchangeOrder.reconstitute(
			id = ExchangeOrderId(exchangeOrderId!!),
			paymentId = paymentId,
			exchangeProviderCode = exchangeProviderCode!!,
			clientOrderId = ClientOrderId(clientOrderId!!),
			orderSide = OrderSide.valueOf(orderSide!!),
			baseAsset = Asset(baseAssetCode!!),
			requestedAmount = TokenAmount(requestedAmountMinor!!),
			requestedAt = requestedAt!!.toUtcInstant(),
			providerOrderId = providerOrderId,
			status = ExchangeOrderStatus.valueOf(exchangeOrderStatus!!),
			executedAmount = executedAmountMinor?.let { TokenAmount(it) },
			averageExecutionRate = averageExecutionRate?.let { ExchangeRate(it) },
			receivedAmount = receivedAmount?.let { Money(it) },
			exchangeFeeAmount = exchangeFeeAmount?.let { Money(it) },
			failureCode = failureCode,
			failureMessage = failureMessage,
			submittedAt = submittedAt?.toUtcInstant(),
			completedAt = completedAt?.toUtcInstant(),
			updatedAt = updatedAt!!.toUtcInstant(),
		)
}
