package paytech.practice.pay.infra.persistence.jooq

import paytech.practice.pay.dbcore.jooq.tables.Merchant.Companion.MERCHANT
import paytech.practice.pay.dbcore.jooq.tables.Payment.Companion.PAYMENT
import java.time.LocalDateTime
import java.util.UUID

/** 여러 테스트가 겹치지 않게 매번 다른 값을 만드는 데 쓴다. */
fun uniqueSuffix(): String = UUID.randomUUID().toString().take(8)

/**
 * FK 제약을 만족시키기 위한 최소한의 Merchant 행을 raw jOOQ로 직접 심는다.
 *
 * `MerchantRepositoryAdapter.save`로도 만들 수 있지만(`Merchant.create` 경유),
 * 이 헬퍼는 다른 Aggregate(Payment 등)의 FK 픽스처로 주로 쓰여서 `Merchant`
 * 자체의 필드 조합에는 관심이 없는 호출부가 많다 — 그런 곳에서는 여전히 이
 * 간단한 raw insert가 더 짧다. `Merchant`의 필드·상태 전이 자체를 검증하는
 * 테스트는 `MerchantRepositoryAdapterTest`처럼 `save`를 직접 쓴다.
 */
fun insertTestMerchant(
	merchantId: String = "mrc_${uniqueSuffix()}",
	merchantCode: String = "code-${uniqueSuffix()}",
	createdAt: LocalDateTime = LocalDateTime.now(),
): String {
	PersistenceTestSupport.dsl
		.newRecord(MERCHANT)
		.apply {
			this.merchantId = merchantId
			this.merchantCode = merchantCode
			merchantName = "테스트 가맹점"
			merchantStatus = "ACTIVE"
			webhookUrl = null
			this.createdAt = createdAt
			updatedAt = createdAt
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
