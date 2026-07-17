package paytech.practice.pay.infra.persistence.jooq.payment

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import paytech.practice.pay.application.port.outbound.PaymentRepository
import paytech.practice.pay.dbcore.jooq.tables.Merchant.Companion.MERCHANT
import paytech.practice.pay.dbcore.jooq.tables.Payment.Companion.PAYMENT
import paytech.practice.pay.dbcore.jooq.tables.records.PaymentRecord
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.Payment
import paytech.practice.pay.domain.payment.PaymentFailureReason
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.TokenAmount
import paytech.practice.pay.domain.shared.WalletAddress
import paytech.practice.pay.infra.persistence.jooq.toUtcInstant
import paytech.practice.pay.infra.persistence.jooq.toUtcLocalDateTime

/**
 * jOOQ로 [PaymentRepository] Port를 구현한다.
 *
 * **낙관적 잠금에 대한 한계**: `payment` 테이블은 `version` 컬럼으로 낙관적 잠금을
 * 하도록 설계돼 있다(`docs/architecture/persistence-jooq.md`). 하지만 도메인
 * [Payment] Aggregate는 자신이 어떤 version에서 읽혔는지 알지 못한다(그런 필드가
 * 없다 — 영속성 관심사를 도메인에 새지 않기 위해 의도적으로 뺐다). 그래서 [save]는
 * "이 값을 저장하는 시점에 DB에 있던 version"을 그대로 읽어 +1 해서 쓴다 — 이건
 * 두 동시 트랜잭션이 "서로 다른 시점에 읽은 값"을 저장하려 할 때의 충돌은 잡아내지
 * 못하는, 사실상 last-write-wins에 가까운 절충이다. 오늘 이 메서드를 부르는
 * `CreatePaymentUseCase`는 항상 새로 만든 Payment만 저장하므로(기존 값을 읽어서
 * 고쳐 쓰는 경우가 없으므로) 지금 당장은 영향이 없다 — 나중에 상태 전이(예: 결제
 * 완료) Use Case가 생기면 이 한계를 반드시 다시 검토해야 한다.
 */
@Repository
class PaymentRepositoryAdapter(
	private val dsl: DSLContext,
) : PaymentRepository {
	override fun save(payment: Payment) {
		val existing =
			dsl
				.selectFrom(PAYMENT)
				.where(PAYMENT.PAYMENT_ID.eq(payment.id.value))
				.fetchOne()

		if (existing == null) {
			dsl
				.newRecord(PAYMENT)
				.apply {
					fillFrom(payment)
					version = 0L
				}.insert()
		} else {
			dsl
				.update(PAYMENT)
				.set(PAYMENT.PAYMENT_STATUS, payment.status.name)
				.set(PAYMENT.CUSTOMER_WALLET_ADDRESS, payment.customerWallet?.value)
				.set(PAYMENT.FAILURE_CODE, payment.failureReason?.name)
				.set(PAYMENT.FAILURE_MESSAGE, payment.failureMessage)
				.set(PAYMENT.PAID_AT, payment.paidAt?.toUtcLocalDateTime())
				.set(PAYMENT.UPDATED_AT, payment.updatedAt.toUtcLocalDateTime())
				.set(PAYMENT.VERSION, (existing.version ?: 0L) + 1)
				.where(PAYMENT.PAYMENT_SEQ.eq(existing.paymentSeq))
				.and(PAYMENT.VERSION.eq(existing.version))
				.execute()
				.also { updatedRows ->
					check(updatedRows == 1) {
						"Payment(${payment.id.value}) 저장에 실패했습니다 — 동시에 변경된 것으로 보입니다(예상 version=${existing.version})."
					}
				}
		}
	}

	override fun findById(paymentId: PaymentId): Payment? =
		dsl
			.selectFrom(PAYMENT)
			.where(PAYMENT.PAYMENT_ID.eq(paymentId.value))
			.fetchOne()
			?.toDomain()

	override fun findByMerchantOrderId(
		merchantId: MerchantId,
		merchantOrderId: MerchantOrderId,
	): Payment? =
		dsl
			.selectFrom(PAYMENT)
			.where(PAYMENT.MERCHANT_ORDER_ID.eq(merchantOrderId.value))
			.and(PAYMENT.MERCHANT_SEQ.eq(resolveMerchantSeq(merchantId)))
			.fetchOne()
			?.toDomain()

	private fun resolveMerchantSeq(merchantId: MerchantId): Long =
		dsl
			.select(MERCHANT.MERCHANT_SEQ)
			.from(MERCHANT)
			.where(MERCHANT.MERCHANT_ID.eq(merchantId.value))
			.fetchOne(MERCHANT.MERCHANT_SEQ)
			?: error("Merchant(${merchantId.value})를 찾을 수 없습니다.")

	private fun PaymentRecord.fillFrom(payment: Payment) {
		paymentId = payment.id.value
		merchantSeq = resolveMerchantSeq(payment.merchantId)
		merchantOrderId = payment.merchantOrderId.value
		orderName = payment.orderName
		orderCurrency = "KRW"
		orderAmount = payment.orderAmount.amount
		paymentAssetCode = payment.paymentAsset.code
		paymentAmountMinor = payment.paymentAmount.amountMinor
		tokenDecimals = payment.tokenDecimals.toShort()
		networkCode = payment.network.code
		receivingWalletAddress = payment.receivingWallet.value
		customerWalletAddress = payment.customerWallet?.value
		paymentStatus = payment.status.name
		failureCode = payment.failureReason?.name
		failureMessage = payment.failureMessage
		expiresAt = payment.expiresAt.toUtcLocalDateTime()
		paidAt = payment.paidAt?.toUtcLocalDateTime()
		createdAt = payment.createdAt.toUtcLocalDateTime()
		updatedAt = payment.updatedAt.toUtcLocalDateTime()
	}

	private fun PaymentRecord.toDomain(): Payment =
		Payment.reconstitute(
			id = PaymentId(paymentId!!),
			merchantId = resolveMerchantId(merchantSeq!!),
			merchantOrderId = MerchantOrderId(merchantOrderId!!),
			orderName = orderName!!,
			orderAmount = Money(orderAmount!!),
			paymentAsset = Asset(paymentAssetCode!!),
			paymentAmount = TokenAmount(paymentAmountMinor!!),
			tokenDecimals = tokenDecimals!!.toInt(),
			network = BlockchainNetwork(networkCode!!),
			receivingWallet = WalletAddress(receivingWalletAddress!!),
			expiresAt = expiresAt!!.toUtcInstant(),
			createdAt = createdAt!!.toUtcInstant(),
			customerWallet = customerWalletAddress?.let { WalletAddress(it) },
			status = PaymentStatus.valueOf(paymentStatus!!),
			failureReason = failureCode?.let { PaymentFailureReason.valueOf(it) },
			failureMessage = failureMessage,
			paidAt = paidAt?.toUtcInstant(),
			updatedAt = updatedAt!!.toUtcInstant(),
		)

	private fun resolveMerchantId(merchantSeq: Long): MerchantId =
		dsl
			.select(MERCHANT.MERCHANT_ID)
			.from(MERCHANT)
			.where(MERCHANT.MERCHANT_SEQ.eq(merchantSeq))
			.fetchOne(MERCHANT.MERCHANT_ID)
			?.let { MerchantId(it) }
			?: error("Merchant(seq=$merchantSeq)를 찾을 수 없습니다.")
}
