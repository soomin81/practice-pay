package paytech.practice.pay.infra.persistence.jooq.payment

import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import paytech.practice.pay.application.port.outbound.PaymentDetailBlockchainTransaction
import paytech.practice.pay.application.port.outbound.PaymentDetailCheckoutSession
import paytech.practice.pay.application.port.outbound.PaymentDetailExchangeOrder
import paytech.practice.pay.application.port.outbound.PaymentDetailPayment
import paytech.practice.pay.application.port.outbound.PaymentDetailProjection
import paytech.practice.pay.application.port.outbound.PaymentDetailQuote
import paytech.practice.pay.application.port.outbound.PaymentDetailSettlement
import paytech.practice.pay.application.port.outbound.PaymentDetailView
import paytech.practice.pay.application.port.outbound.PaymentDetailWebhookDelivery
import paytech.practice.pay.dbcore.jooq.tables.BlockchainTransaction.Companion.BLOCKCHAIN_TRANSACTION
import paytech.practice.pay.dbcore.jooq.tables.CheckoutSession.Companion.CHECKOUT_SESSION
import paytech.practice.pay.dbcore.jooq.tables.ExchangeOrder.Companion.EXCHANGE_ORDER
import paytech.practice.pay.dbcore.jooq.tables.Merchant.Companion.MERCHANT
import paytech.practice.pay.dbcore.jooq.tables.Payment.Companion.PAYMENT
import paytech.practice.pay.dbcore.jooq.tables.PaymentQuote.Companion.PAYMENT_QUOTE
import paytech.practice.pay.dbcore.jooq.tables.SettlementReceivable.Companion.SETTLEMENT_RECEIVABLE
import paytech.practice.pay.dbcore.jooq.tables.WebhookDelivery.Companion.WEBHOOK_DELIVERY
import paytech.practice.pay.domain.blockchain.BlockchainTransactionStatus
import paytech.practice.pay.domain.blockchain.TransactionHash
import paytech.practice.pay.domain.blockchain.TransactionType
import paytech.practice.pay.domain.checkout.CheckoutSessionStatus
import paytech.practice.pay.domain.exchange.ExchangeOrderStatus
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.PaymentFailureReason
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus
import paytech.practice.pay.domain.settlement.SettlementReceivableStatus
import paytech.practice.pay.domain.webhook.WebhookDeliveryStatus
import paytech.practice.pay.infra.persistence.jooq.toUtcInstant

/**
 * jOOQ로 [PaymentDetailProjection] Port를 구현한다.
 *
 * **한 쿼리로 조인해도 행이 늘어나지 않는다** — `payment_quote`/`checkout_session`/
 * `exchange_order`/`settlement_receivable`은 전부 `UNIQUE (payment_seq)`이고
 * `blockchain_transaction`은 `UNIQUE (payment_seq, transaction_type)`이라 `PAYMENT` 타입으로
 * 좁히면 최대 한 건이다. 스키마의 이 제약이 이 구현의 전제이므로, 제약이 사라지면 여기가
 * 먼저 깨져야 한다(테스트가 그것을 고정한다).
 *
 * `payment_quote`/`checkout_session`은 결제 생성 트랜잭션에서 함께 만들어지므로 `INNER JOIN`이
 * 맞다(`CheckoutViewProjectionAdapter`가 세운 판단). 나머지는 흐름이 진행돼야 생기므로
 * `LEFT JOIN`이다.
 *
 * **Webhook 전송만 1:N이라 따로 읽는다** — 같은 쿼리에 넣으면 결제 행이 전송 수만큼 복제된다.
 */
@Repository
class PaymentDetailProjectionAdapter(
	private val dsl: DSLContext,
) : PaymentDetailProjection {
	override fun findByPaymentId(paymentId: PaymentId): PaymentDetailView? {
		val record =
			dsl
				.select()
				.from(PAYMENT)
				.join(MERCHANT)
				.on(MERCHANT.MERCHANT_SEQ.eq(PAYMENT.MERCHANT_SEQ))
				.join(PAYMENT_QUOTE)
				.on(PAYMENT_QUOTE.PAYMENT_SEQ.eq(PAYMENT.PAYMENT_SEQ))
				.join(CHECKOUT_SESSION)
				.on(CHECKOUT_SESSION.PAYMENT_SEQ.eq(PAYMENT.PAYMENT_SEQ))
				.leftJoin(BLOCKCHAIN_TRANSACTION)
				.on(
					BLOCKCHAIN_TRANSACTION.PAYMENT_SEQ
						.eq(PAYMENT.PAYMENT_SEQ)
						.and(BLOCKCHAIN_TRANSACTION.TRANSACTION_TYPE.eq(TransactionType.PAYMENT.name)),
				).leftJoin(EXCHANGE_ORDER)
				.on(EXCHANGE_ORDER.PAYMENT_SEQ.eq(PAYMENT.PAYMENT_SEQ))
				.leftJoin(SETTLEMENT_RECEIVABLE)
				.on(SETTLEMENT_RECEIVABLE.PAYMENT_SEQ.eq(PAYMENT.PAYMENT_SEQ))
				.where(PAYMENT.PAYMENT_ID.eq(paymentId.value))
				.fetchOne() ?: return null

		return PaymentDetailView(
			payment = toPayment(record),
			quote = toQuote(record),
			checkoutSession = toCheckoutSession(record),
			blockchainTransaction = toBlockchainTransaction(record),
			exchangeOrder = toExchangeOrder(record),
			settlementReceivable = toSettlement(record),
			webhookDeliveries = findWebhookDeliveries(paymentId),
		)
	}

	private fun findWebhookDeliveries(paymentId: PaymentId): List<PaymentDetailWebhookDelivery> =
		dsl
			.select()
			.from(WEBHOOK_DELIVERY)
			// aggregate_id로 잇는다 — webhook_delivery는 payment가 아니라 "무엇에 대한
			// 이벤트인가"로 연결된다(Outbox가 aggregateId를 그대로 넘긴다).
			.where(
				WEBHOOK_DELIVERY.AGGREGATE_TYPE
					.eq("Payment")
					.and(WEBHOOK_DELIVERY.AGGREGATE_ID.eq(paymentId.value)),
			).orderBy(WEBHOOK_DELIVERY.CREATED_AT.asc())
			.fetch { record ->
				PaymentDetailWebhookDelivery(
					webhookDeliveryId = record.get(WEBHOOK_DELIVERY.WEBHOOK_DELIVERY_ID)!!,
					eventType = record.get(WEBHOOK_DELIVERY.EVENT_TYPE)!!,
					destinationUrl = record.get(WEBHOOK_DELIVERY.DESTINATION_URL)!!,
					status = WebhookDeliveryStatus.valueOf(record.get(WEBHOOK_DELIVERY.DELIVERY_STATUS)!!),
					attemptCount = record.get(WEBHOOK_DELIVERY.ATTEMPT_COUNT)!!,
					lastHttpStatus = record.get(WEBHOOK_DELIVERY.LAST_HTTP_STATUS),
					lastErrorMessage = record.get(WEBHOOK_DELIVERY.LAST_ERROR_MESSAGE),
					nextRetryAt = record.get(WEBHOOK_DELIVERY.NEXT_RETRY_AT)?.toUtcInstant(),
					deliveredAt = record.get(WEBHOOK_DELIVERY.DELIVERED_AT)?.toUtcInstant(),
					createdAt = record.get(WEBHOOK_DELIVERY.CREATED_AT)!!.toUtcInstant(),
				)
			}

	private fun toPayment(record: Record) =
		PaymentDetailPayment(
			paymentId = PaymentId(record.get(PAYMENT.PAYMENT_ID)!!),
			merchantId = MerchantId(record.get(MERCHANT.MERCHANT_ID)!!),
			merchantName = record.get(MERCHANT.MERCHANT_NAME)!!,
			merchantOrderId = MerchantOrderId(record.get(PAYMENT.MERCHANT_ORDER_ID)!!),
			orderName = record.get(PAYMENT.ORDER_NAME)!!,
			orderAmount = record.get(PAYMENT.ORDER_AMOUNT)!!,
			orderCurrency = record.get(PAYMENT.ORDER_CURRENCY)!!,
			paymentAsset = record.get(PAYMENT.PAYMENT_ASSET_CODE)!!,
			paymentAmountMinor = record.get(PAYMENT.PAYMENT_AMOUNT_MINOR)!!,
			tokenDecimals = record.get(PAYMENT.TOKEN_DECIMALS)!!.toInt(),
			network = record.get(PAYMENT.NETWORK_CODE)!!,
			receivingWallet = record.get(PAYMENT.RECEIVING_WALLET_ADDRESS)!!,
			customerWallet = record.get(PAYMENT.CUSTOMER_WALLET_ADDRESS),
			status = PaymentStatus.valueOf(record.get(PAYMENT.PAYMENT_STATUS)!!),
			failureReason = record.get(PAYMENT.FAILURE_CODE)?.let { PaymentFailureReason.valueOf(it) },
			expiresAt = record.get(PAYMENT.EXPIRES_AT)!!.toUtcInstant(),
			paidAt = record.get(PAYMENT.PAID_AT)?.toUtcInstant(),
			createdAt = record.get(PAYMENT.CREATED_AT)!!.toUtcInstant(),
		)

	private fun toQuote(record: Record) =
		PaymentDetailQuote(
			marketProviderCode = record.get(PAYMENT_QUOTE.MARKET_PROVIDER_CODE)!!,
			marketRate = record.get(PAYMENT_QUOTE.MARKET_RATE)!!,
			appliedRate = record.get(PAYMENT_QUOTE.APPLIED_RATE)!!,
			spreadRate = record.get(PAYMENT_QUOTE.SPREAD_RATE)!!,
			quotedAt = record.get(PAYMENT_QUOTE.QUOTED_AT)!!.toUtcInstant(),
			expiresAt = record.get(PAYMENT_QUOTE.EXPIRES_AT)!!.toUtcInstant(),
		)

	private fun toCheckoutSession(record: Record) =
		PaymentDetailCheckoutSession(
			checkoutSessionId = record.get(CHECKOUT_SESSION.CHECKOUT_SESSION_ID)!!,
			status = CheckoutSessionStatus.valueOf(record.get(CHECKOUT_SESSION.CHECKOUT_STATUS)!!),
			connectedWallet = record.get(CHECKOUT_SESSION.CONNECTED_WALLET_ADDRESS),
			expiresAt = record.get(CHECKOUT_SESSION.EXPIRES_AT)!!.toUtcInstant(),
		)

	/** LEFT JOIN이라 매칭이 없으면 키 컬럼이 `null`이다 — 그것으로 존재 여부를 판단한다. */
	private fun toBlockchainTransaction(record: Record): PaymentDetailBlockchainTransaction? {
		val hash = record.get(BLOCKCHAIN_TRANSACTION.TRANSACTION_HASH) ?: return null
		return PaymentDetailBlockchainTransaction(
			blockchainTransactionId = record.get(BLOCKCHAIN_TRANSACTION.BLOCKCHAIN_TRANSACTION_ID)!!,
			transactionHash = TransactionHash(hash),
			status = BlockchainTransactionStatus.valueOf(record.get(BLOCKCHAIN_TRANSACTION.TRANSACTION_STATUS)!!),
			blockNumber = record.get(BLOCKCHAIN_TRANSACTION.BLOCK_NUMBER),
			confirmationCount = record.get(BLOCKCHAIN_TRANSACTION.CONFIRMATION_COUNT)!!,
			requiredConfirmationCount = record.get(BLOCKCHAIN_TRANSACTION.REQUIRED_CONFIRMATION_COUNT)!!,
			fromAddress = record.get(BLOCKCHAIN_TRANSACTION.FROM_ADDRESS),
			toAddress = record.get(BLOCKCHAIN_TRANSACTION.TO_ADDRESS),
			tokenContractAddress = record.get(BLOCKCHAIN_TRANSACTION.TOKEN_CONTRACT_ADDRESS),
			amountMinor = record.get(BLOCKCHAIN_TRANSACTION.AMOUNT_MINOR),
			failureCode = record.get(BLOCKCHAIN_TRANSACTION.FAILURE_CODE),
			submittedAt = record.get(BLOCKCHAIN_TRANSACTION.SUBMITTED_AT)!!.toUtcInstant(),
			detectedAt = record.get(BLOCKCHAIN_TRANSACTION.DETECTED_AT)?.toUtcInstant(),
			confirmedAt = record.get(BLOCKCHAIN_TRANSACTION.CONFIRMED_AT)?.toUtcInstant(),
		)
	}

	private fun toExchangeOrder(record: Record): PaymentDetailExchangeOrder? {
		val id = record.get(EXCHANGE_ORDER.EXCHANGE_ORDER_ID) ?: return null
		return PaymentDetailExchangeOrder(
			exchangeOrderId = id,
			providerCode = record.get(EXCHANGE_ORDER.EXCHANGE_PROVIDER_CODE)!!,
			status = ExchangeOrderStatus.valueOf(record.get(EXCHANGE_ORDER.EXCHANGE_ORDER_STATUS)!!),
			executedAmountMinor = record.get(EXCHANGE_ORDER.EXECUTED_AMOUNT_MINOR),
			averageExecutionRate = record.get(EXCHANGE_ORDER.AVERAGE_EXECUTION_RATE),
			receivedAmount = record.get(EXCHANGE_ORDER.RECEIVED_AMOUNT),
			feeAmount = record.get(EXCHANGE_ORDER.EXCHANGE_FEE_AMOUNT),
			completedAt = record.get(EXCHANGE_ORDER.COMPLETED_AT)?.toUtcInstant(),
		)
	}

	private fun toSettlement(record: Record): PaymentDetailSettlement? {
		val id = record.get(SETTLEMENT_RECEIVABLE.SETTLEMENT_RECEIVABLE_ID) ?: return null
		return PaymentDetailSettlement(
			settlementReceivableId = id,
			status = SettlementReceivableStatus.valueOf(record.get(SETTLEMENT_RECEIVABLE.RECEIVABLE_STATUS)!!),
			grossAmount = record.get(SETTLEMENT_RECEIVABLE.GROSS_AMOUNT)!!,
			feeRate = record.get(SETTLEMENT_RECEIVABLE.FEE_RATE)!!,
			feeAmount = record.get(SETTLEMENT_RECEIVABLE.FEE_AMOUNT)!!,
			adjustmentAmount = record.get(SETTLEMENT_RECEIVABLE.ADJUSTMENT_AMOUNT)!!,
			netAmount = record.get(SETTLEMENT_RECEIVABLE.NET_AMOUNT)!!,
			exchangeProfitLossAmount = record.get(SETTLEMENT_RECEIVABLE.EXCHANGE_PROFIT_LOSS_AMOUNT),
			eligibleDate = record.get(SETTLEMENT_RECEIVABLE.ELIGIBLE_DATE)!!,
		)
	}
}
