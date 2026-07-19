package paytech.practice.pay.infra.persistence.jooq.checkout

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import paytech.practice.pay.dbcore.jooq.tables.Payment.Companion.PAYMENT
import paytech.practice.pay.dbcore.jooq.tables.PaymentQuote.Companion.PAYMENT_QUOTE
import paytech.practice.pay.domain.blockchain.BlockchainTransaction
import paytech.practice.pay.domain.blockchain.BlockchainTransactionId
import paytech.practice.pay.domain.blockchain.ChainId
import paytech.practice.pay.domain.blockchain.ContractAddress
import paytech.practice.pay.domain.blockchain.TransactionHash
import paytech.practice.pay.domain.blockchain.TransactionType
import paytech.practice.pay.domain.checkout.CheckoutSession
import paytech.practice.pay.domain.checkout.CheckoutSessionId
import paytech.practice.pay.domain.checkout.CheckoutSessionStatus
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.HttpUrl
import paytech.practice.pay.domain.shared.TokenAmount
import paytech.practice.pay.domain.shared.WalletAddress
import paytech.practice.pay.infra.persistence.jooq.PersistenceTestSupport
import paytech.practice.pay.infra.persistence.jooq.blockchain.BlockchainTransactionRepositoryAdapter
import paytech.practice.pay.infra.persistence.jooq.insertTestMerchant
import paytech.practice.pay.infra.persistence.jooq.insertTestPayment
import paytech.practice.pay.infra.persistence.jooq.toUtcLocalDateTime
import paytech.practice.pay.infra.persistence.jooq.uniqueSuffix
import java.math.BigDecimal
import java.time.Instant

private val NOW: Instant = Instant.parse("2026-07-19T10:00:00Z")

/**
 * `payment_quote`는 도메인 Repository Adapter를 거치지 않고 직접 심는다 — Projection이
 * 조인하는 대상일 뿐이고, 이 테스트가 검증하려는 건 조인 결과이지 Quote 저장이 아니다.
 */
private fun insertTestQuote(paymentId: String) {
	val paymentSeq =
		PersistenceTestSupport.dsl
			.select(PAYMENT.PAYMENT_SEQ)
			.from(PAYMENT)
			.where(PAYMENT.PAYMENT_ID.eq(paymentId))
			.fetchOne(PAYMENT.PAYMENT_SEQ)!!

	PersistenceTestSupport.dsl
		.newRecord(PAYMENT_QUOTE)
		.apply {
			paymentQuoteId = "pq_${uniqueSuffix()}"
			this.paymentSeq = paymentSeq
			marketProviderCode = "FAKE"
			baseAssetCode = "USDC"
			quoteCurrency = "KRW"
			marketRate = BigDecimal("1377.135678000000")
			appliedRate = BigDecimal("1370.250000000000")
			spreadRate = BigDecimal("0.005000000000")
			orderAmount = 10_000
			paymentAmountMinor = 6_666_667
			quotedAt = NOW.toUtcLocalDateTime()
			expiresAt = NOW.plusSeconds(1_800).toUtcLocalDateTime()
			createdAt = NOW.toUtcLocalDateTime()
		}.store()
}

private fun insertTestSession(paymentId: PaymentId): CheckoutSessionId {
	val session =
		CheckoutSession.create(
			id = CheckoutSessionId("cs_${uniqueSuffix()}"),
			paymentId = paymentId,
			successUrl = HttpUrl("https://merchant.example.com/done"),
			cancelUrl = HttpUrl("https://merchant.example.com/cancel"),
			expiresAt = NOW.plusSeconds(1_800),
			createdAt = NOW,
		)
	CheckoutSessionRepositoryAdapter(PersistenceTestSupport.dsl).save(session)
	return session.id
}

private fun transactionFor(
	paymentId: PaymentId,
	hash: String,
	submittedAt: Instant,
): BlockchainTransaction =
	BlockchainTransaction.create(
		id = BlockchainTransactionId("btx_${uniqueSuffix()}"),
		paymentId = paymentId,
		transactionType = TransactionType.PAYMENT,
		network = BlockchainNetwork.BASE_SEPOLIA,
		chainId = ChainId(84_532),
		transactionHash = TransactionHash(hash),
		fromAddress = WalletAddress("0x" + "b".repeat(40)),
		toAddress = WalletAddress("0x" + "a".repeat(40)),
		tokenContractAddress = ContractAddress("0x036CbD53842c5426634e7929541eC2318f3dCF7e"),
		tokenAsset = Asset.USDC,
		amountMinor = TokenAmount(6_666_667),
		requiredConfirmationCount = 12,
		submittedAt = submittedAt,
	)

class CheckoutViewProjectionAdapterTest :
	FunSpec({
		val projection = CheckoutViewProjectionAdapter(PersistenceTestSupport.dsl)
		val transactionAdapter = BlockchainTransactionRepositoryAdapter(PersistenceTestSupport.dsl)

		test("findSessionView joins checkout_session + payment + payment_quote") {
			val merchantId = insertTestMerchant()
			val paymentId = PaymentId(insertTestPayment(merchantId))
			insertTestQuote(paymentId.value)
			val sessionId = insertTestSession(paymentId)

			val view = projection.findSessionView(sessionId)

			view.shouldNotBeNull()
			view.checkoutSessionId shouldBe sessionId
			view.checkoutSessionStatus shouldBe CheckoutSessionStatus.CREATED
			view.paymentId shouldBe paymentId
			view.paymentStatus shouldBe PaymentStatus.READY
			view.orderAmount.amount shouldBe 10_000
			view.paymentAmount.amountMinor shouldBe 6_666_667
			view.tokenDecimals shouldBe 6
			view.network shouldBe BlockchainNetwork.BASE_SEPOLIA
			view.appliedRate.value.compareTo(BigDecimal("1370.250000000000")) shouldBe 0
		}

		test("findSessionView returns null for an unknown session") {
			projection.findSessionView(CheckoutSessionId("cs_no_such_session")).shouldBeNull()
		}

		test("findStatusView reports zero confirmations before any transaction is submitted") {
			val merchantId = insertTestMerchant()
			val paymentId = PaymentId(insertTestPayment(merchantId))
			insertTestQuote(paymentId.value)
			val sessionId = insertTestSession(paymentId)

			val view = projection.findStatusView(sessionId)

			view.shouldNotBeNull()
			view.confirmationCount shouldBe 0
			view.transactionHash.shouldBeNull()
			view.failureReason.shouldBeNull()
		}

		test("findStatusView reads the PAYMENT transaction's hash and confirmation count") {
			val merchantId = insertTestMerchant()
			val paymentId = PaymentId(insertTestPayment(merchantId))
			insertTestQuote(paymentId.value)
			val sessionId = insertTestSession(paymentId)

			val transaction = transactionFor(paymentId, "0x" + "1".repeat(64), NOW)
			transactionAdapter.save(transaction)

			val view = projection.findStatusView(sessionId)

			view.shouldNotBeNull()
			view.transactionHash shouldBe transaction.transactionHash
			view.confirmationCount shouldBe 0
		}

		test("a payment cannot have two PAYMENT transactions — the schema forbids it") {
			val merchantId = insertTestMerchant()
			val paymentId = PaymentId(insertTestPayment(merchantId))
			transactionAdapter.save(transactionFor(paymentId, "0x" + "3".repeat(64), NOW))

			// uk_blockchain_payment_type(payment_seq, transaction_type)이 UNIQUE다.
			// 이 테스트를 남겨두는 이유는 Projection이 "최신 1건"을 고르는 대신 타입으로
			// 정확히 집어도 되는 근거가 바로 이 제약이기 때문이다 — 제약이 사라지면
			// 이 테스트가 먼저 깨져서 Projection을 다시 보게 만든다.
			shouldThrow<Exception> {
				transactionAdapter.save(transactionFor(paymentId, "0x" + "4".repeat(64), NOW.plusSeconds(60)))
			}
		}

		test("findStatusView returns null for an unknown session") {
			projection.findStatusView(CheckoutSessionId("cs_no_such_session")).shouldBeNull()
		}
	})
