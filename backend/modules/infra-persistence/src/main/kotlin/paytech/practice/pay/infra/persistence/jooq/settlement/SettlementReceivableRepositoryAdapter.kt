package paytech.practice.pay.infra.persistence.jooq.settlement

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import paytech.practice.pay.application.port.outbound.SettlementReceivableRepository
import paytech.practice.pay.dbcore.jooq.tables.ExchangeOrder.Companion.EXCHANGE_ORDER
import paytech.practice.pay.dbcore.jooq.tables.Merchant.Companion.MERCHANT
import paytech.practice.pay.dbcore.jooq.tables.Payment.Companion.PAYMENT
import paytech.practice.pay.dbcore.jooq.tables.SettlementReceivable.Companion.SETTLEMENT_RECEIVABLE
import paytech.practice.pay.dbcore.jooq.tables.records.SettlementReceivableRecord
import paytech.practice.pay.domain.exchange.ExchangeOrderId
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.settlement.SettlementReceivable
import paytech.practice.pay.domain.settlement.SettlementReceivableId
import paytech.practice.pay.domain.settlement.SettlementReceivableStatus
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.SignedMoney
import paytech.practice.pay.infra.persistence.jooq.toUtcInstant
import paytech.practice.pay.infra.persistence.jooq.toUtcLocalDateTime

/**
 * jOOQ로 [SettlementReceivableRepository] Port를 구현한다.
 *
 * `save`의 낙관적 잠금 한계는 [paytech.practice.pay.infra.persistence.jooq.payment.PaymentRepositoryAdapter]와
 * 동일하다. `settlement_currency` 컬럼은 도메인에 대응 값이 없어(MVP는 KRW 정산만
 * 지원) `"KRW"` 리터럴로 채운다.
 */
@Repository
class SettlementReceivableRepositoryAdapter(
	private val dsl: DSLContext,
) : SettlementReceivableRepository {
	override fun save(settlementReceivable: SettlementReceivable) {
		val existing =
			dsl
				.selectFrom(SETTLEMENT_RECEIVABLE)
				.where(SETTLEMENT_RECEIVABLE.SETTLEMENT_RECEIVABLE_ID.eq(settlementReceivable.id.value))
				.fetchOne()

		if (existing == null) {
			dsl
				.newRecord(SETTLEMENT_RECEIVABLE)
				.apply {
					fillFrom(settlementReceivable)
					version = 0L
				}.insert()
		} else {
			dsl
				.update(SETTLEMENT_RECEIVABLE)
				.set(SETTLEMENT_RECEIVABLE.EXCHANGE_ORDER_SEQ, settlementReceivable.exchangeOrderId?.let { resolveExchangeOrderSeq(it) })
				.set(SETTLEMENT_RECEIVABLE.EXCHANGE_RECEIVED_AMOUNT, settlementReceivable.exchangeReceivedAmount?.amount)
				.set(SETTLEMENT_RECEIVABLE.EXCHANGE_PROFIT_LOSS_AMOUNT, settlementReceivable.exchangeProfitLossAmount?.amount)
				.set(SETTLEMENT_RECEIVABLE.RECEIVABLE_STATUS, settlementReceivable.status.name)
				.set(SETTLEMENT_RECEIVABLE.HOLD_REASON_CODE, settlementReceivable.holdReasonCode)
				.set(SETTLEMENT_RECEIVABLE.UPDATED_AT, settlementReceivable.updatedAt.toUtcLocalDateTime())
				.set(SETTLEMENT_RECEIVABLE.VERSION, (existing.version ?: 0L) + 1)
				.where(SETTLEMENT_RECEIVABLE.SETTLEMENT_RECEIVABLE_SEQ.eq(existing.settlementReceivableSeq))
				.and(SETTLEMENT_RECEIVABLE.VERSION.eq(existing.version))
				.execute()
				.also { updatedRows ->
					check(updatedRows == 1) {
						"SettlementReceivable(${settlementReceivable.id.value}) 저장에 실패했습니다 — " +
							"동시에 변경된 것으로 보입니다(예상 version=${existing.version})."
					}
				}
		}
	}

	override fun findByPaymentId(paymentId: PaymentId): SettlementReceivable? =
		dsl
			.selectFrom(SETTLEMENT_RECEIVABLE)
			.where(SETTLEMENT_RECEIVABLE.PAYMENT_SEQ.eq(resolvePaymentSeq(paymentId)))
			.fetchOne()
			?.toDomain(paymentId)

	private fun resolvePaymentSeq(paymentId: PaymentId): Long =
		dsl
			.select(PAYMENT.PAYMENT_SEQ)
			.from(PAYMENT)
			.where(PAYMENT.PAYMENT_ID.eq(paymentId.value))
			.fetchOne(PAYMENT.PAYMENT_SEQ)
			?: error("Payment(${paymentId.value})를 찾을 수 없습니다.")

	private fun resolveMerchantSeq(merchantId: MerchantId): Long =
		dsl
			.select(MERCHANT.MERCHANT_SEQ)
			.from(MERCHANT)
			.where(MERCHANT.MERCHANT_ID.eq(merchantId.value))
			.fetchOne(MERCHANT.MERCHANT_SEQ)
			?: error("Merchant(${merchantId.value})를 찾을 수 없습니다.")

	private fun resolveMerchantId(merchantSeq: Long): MerchantId =
		dsl
			.select(MERCHANT.MERCHANT_ID)
			.from(MERCHANT)
			.where(MERCHANT.MERCHANT_SEQ.eq(merchantSeq))
			.fetchOne(MERCHANT.MERCHANT_ID)
			?.let { MerchantId(it) }
			?: error("Merchant(seq=$merchantSeq)를 찾을 수 없습니다.")

	private fun resolveExchangeOrderSeq(exchangeOrderId: ExchangeOrderId): Long =
		dsl
			.select(EXCHANGE_ORDER.EXCHANGE_ORDER_SEQ)
			.from(EXCHANGE_ORDER)
			.where(EXCHANGE_ORDER.EXCHANGE_ORDER_ID.eq(exchangeOrderId.value))
			.fetchOne(EXCHANGE_ORDER.EXCHANGE_ORDER_SEQ)
			?: error("ExchangeOrder(${exchangeOrderId.value})를 찾을 수 없습니다.")

	private fun resolveExchangeOrderId(exchangeOrderSeq: Long): ExchangeOrderId =
		dsl
			.select(EXCHANGE_ORDER.EXCHANGE_ORDER_ID)
			.from(EXCHANGE_ORDER)
			.where(EXCHANGE_ORDER.EXCHANGE_ORDER_SEQ.eq(exchangeOrderSeq))
			.fetchOne(EXCHANGE_ORDER.EXCHANGE_ORDER_ID)
			?.let { ExchangeOrderId(it) }
			?: error("ExchangeOrder(seq=$exchangeOrderSeq)를 찾을 수 없습니다.")

	private fun SettlementReceivableRecord.fillFrom(settlementReceivable: SettlementReceivable) {
		settlementReceivableId = settlementReceivable.id.value
		paymentSeq = resolvePaymentSeq(settlementReceivable.paymentId)
		merchantSeq = resolveMerchantSeq(settlementReceivable.merchantId)
		exchangeOrderSeq = settlementReceivable.exchangeOrderId?.let { resolveExchangeOrderSeq(it) }
		settlementCurrency = "KRW"
		grossAmount = settlementReceivable.grossAmount.amount
		feeRate = settlementReceivable.feeRate
		feeAmount = settlementReceivable.feeAmount.amount
		adjustmentAmount = settlementReceivable.adjustmentAmount.amount
		netAmount = settlementReceivable.netAmount.amount
		exchangeReceivedAmount = settlementReceivable.exchangeReceivedAmount?.amount
		exchangeProfitLossAmount = settlementReceivable.exchangeProfitLossAmount?.amount
		receivableStatus = settlementReceivable.status.name
		eligibleDate = settlementReceivable.eligibleDate
		holdReasonCode = settlementReceivable.holdReasonCode
		createdAt = settlementReceivable.createdAt.toUtcLocalDateTime()
		updatedAt = settlementReceivable.updatedAt.toUtcLocalDateTime()
	}

	private fun SettlementReceivableRecord.toDomain(paymentId: PaymentId): SettlementReceivable =
		SettlementReceivable.reconstitute(
			id = SettlementReceivableId(settlementReceivableId!!),
			paymentId = paymentId,
			merchantId = resolveMerchantId(merchantSeq!!),
			grossAmount = Money(grossAmount!!),
			feeRate = feeRate!!,
			feeAmount = Money(feeAmount!!),
			adjustmentAmount = SignedMoney(adjustmentAmount!!),
			netAmount = Money(netAmount!!),
			eligibleDate = eligibleDate!!,
			createdAt = createdAt!!.toUtcInstant(),
			exchangeOrderId = exchangeOrderSeq?.let { resolveExchangeOrderId(it) },
			exchangeReceivedAmount = exchangeReceivedAmount?.let { Money(it) },
			exchangeProfitLossAmount = exchangeProfitLossAmount?.let { SignedMoney(it) },
			status = SettlementReceivableStatus.valueOf(receivableStatus!!),
			holdReasonCode = holdReasonCode,
			updatedAt = updatedAt!!.toUtcInstant(),
		)
}
