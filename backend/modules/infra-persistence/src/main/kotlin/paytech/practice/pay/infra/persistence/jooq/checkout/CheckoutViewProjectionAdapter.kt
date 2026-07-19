package paytech.practice.pay.infra.persistence.jooq.checkout

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import paytech.practice.pay.application.port.outbound.CheckoutSessionView
import paytech.practice.pay.application.port.outbound.CheckoutStatusView
import paytech.practice.pay.application.port.outbound.CheckoutViewProjection
import paytech.practice.pay.dbcore.jooq.tables.BlockchainTransaction.Companion.BLOCKCHAIN_TRANSACTION
import paytech.practice.pay.dbcore.jooq.tables.CheckoutSession.Companion.CHECKOUT_SESSION
import paytech.practice.pay.dbcore.jooq.tables.Payment.Companion.PAYMENT
import paytech.practice.pay.dbcore.jooq.tables.PaymentQuote.Companion.PAYMENT_QUOTE
import paytech.practice.pay.domain.blockchain.TransactionHash
import paytech.practice.pay.domain.blockchain.TransactionType
import paytech.practice.pay.domain.checkout.CheckoutSessionId
import paytech.practice.pay.domain.checkout.CheckoutSessionStatus
import paytech.practice.pay.domain.payment.PaymentFailureReason
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.ExchangeRate
import paytech.practice.pay.domain.shared.HttpUrl
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.TokenAmount
import paytech.practice.pay.domain.shared.WalletAddress
import paytech.practice.pay.infra.persistence.jooq.toUtcInstant

/**
 * jOOQ로 [CheckoutViewProjection] Port를 구현한다.
 *
 * `checkout_session`을 기준으로 `payment`(필수)와 `payment_quote`(필수)를 조인한다 —
 * 셋 다 "결제 생성" 트랜잭션에서 함께 만들어지므로(`CreatePaymentUseCase`) 하나라도
 * 없으면 데이터가 깨진 것이라 `INNER JOIN`이 맞다.
 *
 * `blockchain_transaction`은 고객이 Hash를 제출하기 전에는 없으므로 조인하지 않고
 * 필요할 때(상태 조회) 별도로 읽는다.
 */
@Repository
class CheckoutViewProjectionAdapter(
	private val dsl: DSLContext,
) : CheckoutViewProjection {
	override fun findSessionView(checkoutSessionId: CheckoutSessionId): CheckoutSessionView? =
		dsl
			.select(
				CHECKOUT_SESSION.CHECKOUT_SESSION_ID,
				CHECKOUT_SESSION.CHECKOUT_STATUS,
				CHECKOUT_SESSION.EXPIRES_AT,
				CHECKOUT_SESSION.SUCCESS_URL,
				CHECKOUT_SESSION.CANCEL_URL,
				CHECKOUT_SESSION.CONNECTED_WALLET_ADDRESS,
				PAYMENT.PAYMENT_ID,
				PAYMENT.PAYMENT_STATUS,
				PAYMENT.ORDER_NAME,
				PAYMENT.ORDER_AMOUNT,
				PAYMENT.PAYMENT_ASSET_CODE,
				PAYMENT.PAYMENT_AMOUNT_MINOR,
				PAYMENT.TOKEN_DECIMALS,
				PAYMENT.NETWORK_CODE,
				PAYMENT.RECEIVING_WALLET_ADDRESS,
				PAYMENT_QUOTE.APPLIED_RATE,
				PAYMENT_QUOTE.QUOTED_AT,
				PAYMENT_QUOTE.EXPIRES_AT,
			).from(CHECKOUT_SESSION)
			.join(PAYMENT)
			.on(PAYMENT.PAYMENT_SEQ.eq(CHECKOUT_SESSION.PAYMENT_SEQ))
			.join(PAYMENT_QUOTE)
			.on(PAYMENT_QUOTE.PAYMENT_SEQ.eq(CHECKOUT_SESSION.PAYMENT_SEQ))
			.where(CHECKOUT_SESSION.CHECKOUT_SESSION_ID.eq(checkoutSessionId.value))
			.fetchOne { record ->
				CheckoutSessionView(
					checkoutSessionId = CheckoutSessionId(record.get(CHECKOUT_SESSION.CHECKOUT_SESSION_ID)!!),
					checkoutSessionStatus = CheckoutSessionStatus.valueOf(record.get(CHECKOUT_SESSION.CHECKOUT_STATUS)!!),
					expiresAt = record.get(CHECKOUT_SESSION.EXPIRES_AT)!!.toUtcInstant(),
					successUrl = HttpUrl(record.get(CHECKOUT_SESSION.SUCCESS_URL)!!),
					cancelUrl = record.get(CHECKOUT_SESSION.CANCEL_URL)?.let { HttpUrl(it) },
					connectedWallet = record.get(CHECKOUT_SESSION.CONNECTED_WALLET_ADDRESS)?.let { WalletAddress(it) },
					orderName = record.get(PAYMENT.ORDER_NAME)!!,
					orderAmount = Money(record.get(PAYMENT.ORDER_AMOUNT)!!),
					paymentId = PaymentId(record.get(PAYMENT.PAYMENT_ID)!!),
					paymentStatus = PaymentStatus.valueOf(record.get(PAYMENT.PAYMENT_STATUS)!!),
					paymentAsset = Asset(record.get(PAYMENT.PAYMENT_ASSET_CODE)!!),
					paymentAmount = TokenAmount(record.get(PAYMENT.PAYMENT_AMOUNT_MINOR)!!),
					tokenDecimals = record.get(PAYMENT.TOKEN_DECIMALS)!!.toInt(),
					network = BlockchainNetwork(record.get(PAYMENT.NETWORK_CODE)!!),
					receivingWallet = WalletAddress(record.get(PAYMENT.RECEIVING_WALLET_ADDRESS)!!),
					appliedRate = ExchangeRate(record.get(PAYMENT_QUOTE.APPLIED_RATE)!!),
					quotedAt = record.get(PAYMENT_QUOTE.QUOTED_AT)!!.toUtcInstant(),
					quoteExpiresAt = record.get(PAYMENT_QUOTE.EXPIRES_AT)!!.toUtcInstant(),
				)
			}

	override fun findStatusView(checkoutSessionId: CheckoutSessionId): CheckoutStatusView? {
		val base =
			dsl
				.select(
					CHECKOUT_SESSION.PAYMENT_SEQ,
					CHECKOUT_SESSION.CHECKOUT_STATUS,
					CHECKOUT_SESSION.SUCCESS_URL,
					CHECKOUT_SESSION.CANCEL_URL,
					PAYMENT.PAYMENT_STATUS,
					PAYMENT.FAILURE_CODE,
				).from(CHECKOUT_SESSION)
				.join(PAYMENT)
				.on(PAYMENT.PAYMENT_SEQ.eq(CHECKOUT_SESSION.PAYMENT_SEQ))
				.where(CHECKOUT_SESSION.CHECKOUT_SESSION_ID.eq(checkoutSessionId.value))
				.fetchOne() ?: return null

		val paymentTransaction = findPaymentTransaction(base.get(CHECKOUT_SESSION.PAYMENT_SEQ)!!)

		return CheckoutStatusView(
			checkoutSessionStatus = CheckoutSessionStatus.valueOf(base.get(CHECKOUT_SESSION.CHECKOUT_STATUS)!!),
			paymentStatus = PaymentStatus.valueOf(base.get(PAYMENT.PAYMENT_STATUS)!!),
			confirmationCount = paymentTransaction?.second ?: 0,
			transactionHash = paymentTransaction?.first,
			failureReason = base.get(PAYMENT.FAILURE_CODE)?.let { PaymentFailureReason.valueOf(it) },
			successUrl = HttpUrl(base.get(CHECKOUT_SESSION.SUCCESS_URL)!!),
			cancelUrl = base.get(CHECKOUT_SESSION.CANCEL_URL)?.let { HttpUrl(it) },
		)
	}

	/**
	 * 이 결제의 `PAYMENT` 타입 `BlockchainTransaction`에서 (Hash, Confirm 수)를 읽는다.
	 *
	 * **결제당 최대 한 건이다** — 스키마의 `uk_blockchain_payment_type`이
	 * `(payment_seq, transaction_type)`을 UNIQUE로 걸고 있다. 그래서 정렬해서 최신 1건을
	 * 고르는 게 아니라 타입으로 정확히 집는다. (처음에는 "재제출로 여러 건이 생길 수
	 * 있다"고 보고 `created_at` 내림차순을 썼는데, 통합 테스트가 그 제약 위반으로
	 * 실패하면서 잘못된 가정이었음이 드러났다.)
	 *
	 * `REFUND`는 MVP 범위 밖이지만(ADR-001) 타입이 이미 스키마에 있으므로, 나중에
	 * 환불 거래가 생겨도 이 조회가 그걸 결제 거래로 착각하지 않도록 조건을 명시한다.
	 */
	private fun findPaymentTransaction(paymentSeq: Long): Pair<TransactionHash, Int>? =
		dsl
			.select(BLOCKCHAIN_TRANSACTION.TRANSACTION_HASH, BLOCKCHAIN_TRANSACTION.CONFIRMATION_COUNT)
			.from(BLOCKCHAIN_TRANSACTION)
			.where(BLOCKCHAIN_TRANSACTION.PAYMENT_SEQ.eq(paymentSeq))
			.and(BLOCKCHAIN_TRANSACTION.TRANSACTION_TYPE.eq(TransactionType.PAYMENT.name))
			.fetchOne { record ->
				TransactionHash(record.get(BLOCKCHAIN_TRANSACTION.TRANSACTION_HASH)!!) to
					record.get(BLOCKCHAIN_TRANSACTION.CONFIRMATION_COUNT)!!
			}
}
