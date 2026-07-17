package paytech.practice.pay.infra.persistence.jooq

import paytech.practice.pay.dbcore.jooq.tables.Merchant.Companion.MERCHANT
import paytech.practice.pay.dbcore.jooq.tables.Payment.Companion.PAYMENT
import java.time.LocalDateTime
import java.util.UUID

/** 여러 테스트가 겹치지 않게 매번 다른 값을 만드는 데 쓴다. */
fun uniqueSuffix(): String = UUID.randomUUID().toString().take(8)

/**
 * FK 제약을 만족시키기 위한 최소한의 Merchant 행을 raw jOOQ로 직접 심는다 —
 * `MerchantRepository` Port에는 `save`가 없어서 Adapter를 통해서는 만들 수 없다.
 */
fun insertTestMerchant(
	merchantId: String = "mrc_${uniqueSuffix()}",
	merchantCode: String = "code-${uniqueSuffix()}",
): String {
	PersistenceTestSupport.dsl
		.newRecord(MERCHANT)
		.apply {
			this.merchantId = merchantId
			this.merchantCode = merchantCode
			merchantName = "테스트 가맹점"
			merchantStatus = "ACTIVE"
			webhookUrl = null
			createdAt = LocalDateTime.now()
			updatedAt = LocalDateTime.now()
			version = 0
		}.insert()
	return merchantId
}

/** [PaymentQuoteRepositoryAdapter]/[CheckoutSessionRepositoryAdapter] 테스트가 딸릴 Payment 행을 raw jOOQ로 직접 심는다. */
fun insertTestPayment(
	merchantId: String,
	paymentId: String = "pay_${uniqueSuffix()}",
	merchantOrderId: String = "order-${uniqueSuffix()}",
): String {
	val merchantSeq =
		PersistenceTestSupport.dsl
			.select(MERCHANT.MERCHANT_SEQ)
			.from(MERCHANT)
			.where(MERCHANT.MERCHANT_ID.eq(merchantId))
			.fetchOne(MERCHANT.MERCHANT_SEQ)!!

	PersistenceTestSupport.dsl
		.newRecord(PAYMENT)
		.apply {
			this.paymentId = paymentId
			this.merchantSeq = merchantSeq
			this.merchantOrderId = merchantOrderId
			orderName = "테스트 주문"
			orderCurrency = "KRW"
			orderAmount = 10_000
			paymentAssetCode = "USDC"
			paymentAmountMinor = 6_666_667
			tokenDecimals = 6
			networkCode = "BASE_SEPOLIA"
			receivingWalletAddress = "0x" + "a".repeat(40)
			customerWalletAddress = null
			paymentStatus = "READY"
			failureCode = null
			failureMessage = null
			expiresAt = LocalDateTime.now().plusMinutes(30)
			paidAt = null
			createdAt = LocalDateTime.now()
			updatedAt = LocalDateTime.now()
			version = 0
		}.insert()
	return paymentId
}
