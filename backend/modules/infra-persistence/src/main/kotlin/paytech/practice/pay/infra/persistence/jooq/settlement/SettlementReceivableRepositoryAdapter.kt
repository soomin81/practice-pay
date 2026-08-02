package paytech.practice.pay.infra.persistence.jooq.settlement

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import paytech.practice.pay.application.port.outbound.SettlementReceivableRepository
import paytech.practice.pay.dbcore.jooq.tables.SettlementReceivable.Companion.SETTLEMENT_RECEIVABLE
import paytech.practice.pay.dbcore.jooq.tables.records.SettlementReceivableRecord
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.settlement.SettlementReceivable
import paytech.practice.pay.domain.settlement.SettlementReceivableId
import paytech.practice.pay.domain.settlement.SettlementReceivableStatus
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.SignedMoney
import paytech.practice.pay.infra.persistence.jooq.exchangeOrderId
import paytech.practice.pay.infra.persistence.jooq.exchangeOrderSeq
import paytech.practice.pay.infra.persistence.jooq.merchantId
import paytech.practice.pay.infra.persistence.jooq.merchantSeq
import paytech.practice.pay.infra.persistence.jooq.paymentId
import paytech.practice.pay.infra.persistence.jooq.paymentSeq
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
				.set(SETTLEMENT_RECEIVABLE.EXCHANGE_ORDER_SEQ, settlementReceivable.exchangeOrderId?.let { dsl.exchangeOrderSeq(it) })
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
			.where(SETTLEMENT_RECEIVABLE.PAYMENT_SEQ.eq(dsl.paymentSeq(paymentId)))
			.fetchOne()
			?.toDomain(paymentId)

	override fun findById(settlementReceivableId: SettlementReceivableId): SettlementReceivable? =
		dsl
			.selectFrom(SETTLEMENT_RECEIVABLE)
			.where(SETTLEMENT_RECEIVABLE.SETTLEMENT_RECEIVABLE_ID.eq(settlementReceivableId.value))
			.fetchOne()
			// paymentId를 인자로 받는 쪽과 달리 여기서는 seq를 되돌려 해석해야 한다.
			?.let { it.toDomain(dsl.paymentId(it.paymentSeq!!)) }

	private fun SettlementReceivableRecord.fillFrom(settlementReceivable: SettlementReceivable) {
		settlementReceivableId = settlementReceivable.id.value
		paymentSeq = dsl.paymentSeq(settlementReceivable.paymentId)
		merchantSeq = dsl.merchantSeq(settlementReceivable.merchantId)
		exchangeOrderSeq = settlementReceivable.exchangeOrderId?.let { dsl.exchangeOrderSeq(it) }
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
			merchantId = dsl.merchantId(merchantSeq!!),
			grossAmount = Money(grossAmount!!),
			feeRate = feeRate!!,
			feeAmount = Money(feeAmount!!),
			adjustmentAmount = SignedMoney(adjustmentAmount!!),
			netAmount = Money(netAmount!!),
			eligibleDate = eligibleDate!!,
			createdAt = createdAt!!.toUtcInstant(),
			exchangeOrderId = exchangeOrderSeq?.let { dsl.exchangeOrderId(it) },
			exchangeReceivedAmount = exchangeReceivedAmount?.let { Money(it) },
			exchangeProfitLossAmount = exchangeProfitLossAmount?.let { SignedMoney(it) },
			status = SettlementReceivableStatus.valueOf(receivableStatus!!),
			holdReasonCode = holdReasonCode,
			updatedAt = updatedAt!!.toUtcInstant(),
		)
}
