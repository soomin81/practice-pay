package paytech.practice.pay.infra.persistence.jooq.settlement

import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import paytech.practice.pay.application.port.outbound.SettlementReceivableListEntry
import paytech.practice.pay.application.port.outbound.SettlementReceivableListPage
import paytech.practice.pay.application.port.outbound.SettlementReceivableListProjection
import paytech.practice.pay.application.port.outbound.SettlementReceivableListQuery
import paytech.practice.pay.dbcore.jooq.tables.Merchant.Companion.MERCHANT
import paytech.practice.pay.dbcore.jooq.tables.Payment.Companion.PAYMENT
import paytech.practice.pay.dbcore.jooq.tables.SettlementReceivable.Companion.SETTLEMENT_RECEIVABLE
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.settlement.SettlementReceivableId
import paytech.practice.pay.domain.settlement.SettlementReceivableStatus
import paytech.practice.pay.infra.persistence.jooq.toUtcInstant

/**
 * jOOQ로 [SettlementReceivableListProjection] Port를 구현한다
 * (`PaymentListProjectionAdapter`의 정산판 — 같은 구조).
 *
 * `settlement_receivable`을 기준으로 `merchant`와 `payment`를 둘 다 `INNER JOIN`한다 —
 * 두 FK 모두 `NOT NULL`이라 항상 있고, 없으면 데이터가 깨진 것이다.
 *
 * 합계(`totalCount`/`totalNetAmount`)는 같은 조건으로 한 번 더 조회한다. 결제 목록과 같은
 * 이유다 — 마지막 페이지를 넘어선 요청처럼 **행이 0건이어도 합계는 나와야 한다.**
 */
@Repository
class SettlementReceivableListProjectionAdapter(
	private val dsl: DSLContext,
) : SettlementReceivableListProjection {
	override fun find(query: SettlementReceivableListQuery): SettlementReceivableListPage {
		val conditions = conditionsOf(query)

		val entries =
			dsl
				.select(
					SETTLEMENT_RECEIVABLE.SETTLEMENT_RECEIVABLE_ID,
					SETTLEMENT_RECEIVABLE.RECEIVABLE_STATUS,
					SETTLEMENT_RECEIVABLE.SETTLEMENT_CURRENCY,
					SETTLEMENT_RECEIVABLE.GROSS_AMOUNT,
					SETTLEMENT_RECEIVABLE.FEE_RATE,
					SETTLEMENT_RECEIVABLE.FEE_AMOUNT,
					SETTLEMENT_RECEIVABLE.ADJUSTMENT_AMOUNT,
					SETTLEMENT_RECEIVABLE.NET_AMOUNT,
					SETTLEMENT_RECEIVABLE.EXCHANGE_RECEIVED_AMOUNT,
					SETTLEMENT_RECEIVABLE.EXCHANGE_PROFIT_LOSS_AMOUNT,
					SETTLEMENT_RECEIVABLE.ELIGIBLE_DATE,
					SETTLEMENT_RECEIVABLE.HOLD_REASON_CODE,
					SETTLEMENT_RECEIVABLE.CREATED_AT,
					MERCHANT.MERCHANT_ID,
					MERCHANT.MERCHANT_NAME,
					PAYMENT.PAYMENT_ID,
					PAYMENT.MERCHANT_ORDER_ID,
				).from(SETTLEMENT_RECEIVABLE)
				.join(MERCHANT)
				.on(MERCHANT.MERCHANT_SEQ.eq(SETTLEMENT_RECEIVABLE.MERCHANT_SEQ))
				.join(PAYMENT)
				.on(PAYMENT.PAYMENT_SEQ.eq(SETTLEMENT_RECEIVABLE.PAYMENT_SEQ))
				.where(conditions)
				// 정산 예정일이 늦은 것부터. 같은 날짜가 흔하므로(하루에 여러 건) seq를 2차
				// 정렬로 둬서 페이지 경계에서 행이 겹치거나 빠지지 않게 한다.
				.orderBy(
					SETTLEMENT_RECEIVABLE.ELIGIBLE_DATE.desc(),
					SETTLEMENT_RECEIVABLE.SETTLEMENT_RECEIVABLE_SEQ.desc(),
				).limit(query.size)
				.offset(query.page.toLong() * query.size)
				.fetch { record ->
					SettlementReceivableListEntry(
						settlementReceivableId =
							SettlementReceivableId(record.get(SETTLEMENT_RECEIVABLE.SETTLEMENT_RECEIVABLE_ID)!!),
						merchantId = MerchantId(record.get(MERCHANT.MERCHANT_ID)!!),
						merchantName = record.get(MERCHANT.MERCHANT_NAME)!!,
						paymentId = PaymentId(record.get(PAYMENT.PAYMENT_ID)!!),
						merchantOrderId = MerchantOrderId(record.get(PAYMENT.MERCHANT_ORDER_ID)!!),
						status = SettlementReceivableStatus.valueOf(record.get(SETTLEMENT_RECEIVABLE.RECEIVABLE_STATUS)!!),
						settlementCurrency = record.get(SETTLEMENT_RECEIVABLE.SETTLEMENT_CURRENCY)!!,
						grossAmount = record.get(SETTLEMENT_RECEIVABLE.GROSS_AMOUNT)!!,
						feeRate = record.get(SETTLEMENT_RECEIVABLE.FEE_RATE)!!,
						feeAmount = record.get(SETTLEMENT_RECEIVABLE.FEE_AMOUNT)!!,
						adjustmentAmount = record.get(SETTLEMENT_RECEIVABLE.ADJUSTMENT_AMOUNT)!!,
						netAmount = record.get(SETTLEMENT_RECEIVABLE.NET_AMOUNT)!!,
						exchangeReceivedAmount = record.get(SETTLEMENT_RECEIVABLE.EXCHANGE_RECEIVED_AMOUNT),
						exchangeProfitLossAmount = record.get(SETTLEMENT_RECEIVABLE.EXCHANGE_PROFIT_LOSS_AMOUNT),
						eligibleDate = record.get(SETTLEMENT_RECEIVABLE.ELIGIBLE_DATE)!!,
						holdReasonCode = record.get(SETTLEMENT_RECEIVABLE.HOLD_REASON_CODE),
						createdAt = record.get(SETTLEMENT_RECEIVABLE.CREATED_AT)!!.toUtcInstant(),
					)
				}

		// 지급 경로에 살아 있는 것만 합계에 넣는다 — HELD는 막아 둔 돈이고 CANCELLED는 끝난
		// 돈이라, 더하면 "그래서 얼마를 받나"에 실제보다 큰 답을 하게 된다(ADR-007).
		val payable =
			SETTLEMENT_RECEIVABLE.RECEIVABLE_STATUS.`in`(
				SettlementReceivableStatus.PENDING.name,
				SettlementReceivableStatus.READY.name,
			)
		val held = SETTLEMENT_RECEIVABLE.RECEIVABLE_STATUS.eq(SettlementReceivableStatus.HELD.name)

		// 네 값을 한 번에 집계한다(`PaymentListProjectionAdapter`가 승인 건수·금액을 같은
		// 쿼리에 접은 것과 같은 방식) — 조회를 늘리지 않고 같은 조건을 두 번 쓰지 않는다.
		val totals =
			dsl
				.select(
					DSL.count(),
					DSL.coalesce(DSL.sum(SETTLEMENT_RECEIVABLE.NET_AMOUNT).filterWhere(payable), DSL.zero()),
					DSL.count().filterWhere(held),
					DSL.coalesce(DSL.sum(SETTLEMENT_RECEIVABLE.NET_AMOUNT).filterWhere(held), DSL.zero()),
				).from(SETTLEMENT_RECEIVABLE)
				.join(MERCHANT)
				.on(MERCHANT.MERCHANT_SEQ.eq(SETTLEMENT_RECEIVABLE.MERCHANT_SEQ))
				.where(conditions)
				.fetchOne()

		return SettlementReceivableListPage(
			entries = entries,
			totalCount = (totals?.value1() ?: 0).toLong(),
			totalNetAmount = totals?.value2()?.toLong() ?: 0L,
			heldCount = (totals?.value3() ?: 0).toLong(),
			heldNetAmount = totals?.value4()?.toLong() ?: 0L,
		)
	}

	private fun conditionsOf(query: SettlementReceivableListQuery): List<Condition> =
		buildList {
			query.merchantId?.let { add(MERCHANT.MERCHANT_ID.eq(it.value)) }
			query.status?.let { add(SETTLEMENT_RECEIVABLE.RECEIVABLE_STATUS.eq(it.name)) }
			query.eligibleFrom?.let { add(SETTLEMENT_RECEIVABLE.ELIGIBLE_DATE.ge(it)) }
			query.eligibleTo?.let { add(SETTLEMENT_RECEIVABLE.ELIGIBLE_DATE.le(it)) }
			if (isEmpty()) add(DSL.noCondition())
		}
}
