package paytech.practice.pay.infra.persistence.jooq.payment

import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import paytech.practice.pay.application.port.outbound.PaymentListEntry
import paytech.practice.pay.application.port.outbound.PaymentListPage
import paytech.practice.pay.application.port.outbound.PaymentListProjection
import paytech.practice.pay.application.port.outbound.PaymentListQuery
import paytech.practice.pay.dbcore.jooq.tables.BlockchainTransaction.Companion.BLOCKCHAIN_TRANSACTION
import paytech.practice.pay.dbcore.jooq.tables.Merchant.Companion.MERCHANT
import paytech.practice.pay.dbcore.jooq.tables.Payment.Companion.PAYMENT
import paytech.practice.pay.domain.blockchain.TransactionHash
import paytech.practice.pay.domain.blockchain.TransactionType
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.PaymentFailureReason
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.TokenAmount
import paytech.practice.pay.infra.persistence.jooq.toUtcInstant
import paytech.practice.pay.infra.persistence.jooq.toUtcLocalDateTime

/**
 * jOOQ로 [PaymentListProjection] Port를 구현한다.
 *
 * `payment`를 기준으로 `merchant`를 `INNER JOIN`(FK가 `NOT NULL`이라 항상 있다)하고,
 * `blockchain_transaction`은 **고객이 Hash를 제출하기 전에는 없으므로 `LEFT JOIN`**한다.
 * 그 조인은 `transaction_type = 'PAYMENT'`로 좁힌다 — 결제당 `PAYMENT` 거래는
 * `uk_blockchain_payment_type`이 UNIQUE로 걸어 최대 한 건이라 행이 늘어나지 않는다
 * (`CheckoutViewProjectionAdapter`가 같은 제약을 근거로 삼는다).
 *
 * 총 건수는 같은 조건으로 `COUNT(*)`를 한 번 더 조회해서 얻는다 — 윈도우 함수
 * (`COUNT(*) OVER ()`)를 쓰면 쿼리 하나로 끝나지만, 마지막 페이지를 넘어선 요청처럼
 * **행이 0건이면 총 건수도 못 얻는다.** 백오피스는 그 경우에도 "전체 N건"을 그려야 한다.
 */
@Repository
class PaymentListProjectionAdapter(
	private val dsl: DSLContext,
) : PaymentListProjection {
	override fun find(query: PaymentListQuery): PaymentListPage {
		val conditions = conditionsOf(query)

		val entries =
			dsl
				.select(
					PAYMENT.PAYMENT_ID,
					PAYMENT.MERCHANT_ORDER_ID,
					PAYMENT.ORDER_NAME,
					PAYMENT.ORDER_AMOUNT,
					PAYMENT.PAYMENT_ASSET_CODE,
					PAYMENT.PAYMENT_AMOUNT_MINOR,
					PAYMENT.TOKEN_DECIMALS,
					PAYMENT.NETWORK_CODE,
					PAYMENT.PAYMENT_STATUS,
					PAYMENT.FAILURE_CODE,
					PAYMENT.PAID_AT,
					PAYMENT.CREATED_AT,
					MERCHANT.MERCHANT_ID,
					MERCHANT.MERCHANT_NAME,
					BLOCKCHAIN_TRANSACTION.TRANSACTION_HASH,
				).from(PAYMENT)
				.join(MERCHANT)
				.on(MERCHANT.MERCHANT_SEQ.eq(PAYMENT.MERCHANT_SEQ))
				.leftJoin(BLOCKCHAIN_TRANSACTION)
				.on(
					BLOCKCHAIN_TRANSACTION.PAYMENT_SEQ
						.eq(PAYMENT.PAYMENT_SEQ)
						.and(BLOCKCHAIN_TRANSACTION.TRANSACTION_TYPE.eq(TransactionType.PAYMENT.name)),
				).where(conditions)
				// 최신순이 백오피스의 기본 기대다. created_at이 같을 때의 순서를 고정하려고
				// payment_seq를 2차 정렬로 둔다 — 없으면 페이지 경계에서 같은 행이 두 번
				// 나오거나 아예 빠질 수 있다.
				.orderBy(PAYMENT.CREATED_AT.desc(), PAYMENT.PAYMENT_SEQ.desc())
				.limit(query.size)
				.offset(query.page.toLong() * query.size)
				.fetch { record ->
					PaymentListEntry(
						paymentId = PaymentId(record.get(PAYMENT.PAYMENT_ID)!!),
						merchantId = MerchantId(record.get(MERCHANT.MERCHANT_ID)!!),
						merchantName = record.get(MERCHANT.MERCHANT_NAME)!!,
						merchantOrderId = MerchantOrderId(record.get(PAYMENT.MERCHANT_ORDER_ID)!!),
						orderName = record.get(PAYMENT.ORDER_NAME)!!,
						orderAmount = Money(record.get(PAYMENT.ORDER_AMOUNT)!!),
						paymentAsset = Asset(record.get(PAYMENT.PAYMENT_ASSET_CODE)!!),
						paymentAmount = TokenAmount(record.get(PAYMENT.PAYMENT_AMOUNT_MINOR)!!),
						tokenDecimals = record.get(PAYMENT.TOKEN_DECIMALS)!!.toInt(),
						network = BlockchainNetwork(record.get(PAYMENT.NETWORK_CODE)!!),
						status = PaymentStatus.valueOf(record.get(PAYMENT.PAYMENT_STATUS)!!),
						failureReason = record.get(PAYMENT.FAILURE_CODE)?.let { PaymentFailureReason.valueOf(it) },
						transactionHash = record.get(BLOCKCHAIN_TRANSACTION.TRANSACTION_HASH)?.let { TransactionHash(it) },
						paidAt = record.get(PAYMENT.PAID_AT)?.toUtcInstant(),
						createdAt = record.get(PAYMENT.CREATED_AT)!!.toUtcInstant(),
					)
				}

		val totalCount =
			dsl
				.selectCount()
				.from(PAYMENT)
				.join(MERCHANT)
				.on(MERCHANT.MERCHANT_SEQ.eq(PAYMENT.MERCHANT_SEQ))
				.where(conditions)
				.fetchOne(0, Long::class.java) ?: 0L

		return PaymentListPage(entries = entries, totalCount = totalCount)
	}

	/**
	 * 지정된 필터만 조건으로 만든다. `merchantId`는 **가맹점 코드가 아니라 공개 ID로**
	 * 비교한다 — `merchant`를 이미 조인하고 있어 별도 seq 변환이 필요 없다.
	 */
	private fun conditionsOf(query: PaymentListQuery): List<Condition> =
		buildList {
			query.merchantId?.let { add(MERCHANT.MERCHANT_ID.eq(it.value)) }
			query.status?.let { add(PAYMENT.PAYMENT_STATUS.eq(it.name)) }
			query.createdFrom?.let { add(PAYMENT.CREATED_AT.ge(it.toUtcLocalDateTime())) }
			query.createdTo?.let { add(PAYMENT.CREATED_AT.le(it.toUtcLocalDateTime())) }
			if (isEmpty()) add(DSL.noCondition())
		}
}
