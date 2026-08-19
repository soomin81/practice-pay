package paytech.practice.pay.infra.persistence.jooq.customer

import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.TableField
import org.springframework.stereotype.Repository
import paytech.practice.pay.application.port.outbound.PaymentCustomerSearchEntry
import paytech.practice.pay.application.port.outbound.PaymentCustomerSearchProjection
import paytech.practice.pay.dbcore.jooq.tables.Merchant.Companion.MERCHANT
import paytech.practice.pay.dbcore.jooq.tables.Payment.Companion.PAYMENT
import paytech.practice.pay.dbcore.jooq.tables.PaymentCustomer.Companion.PAYMENT_CUSTOMER
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.infra.persistence.jooq.toUtcInstant

/**
 * jOOQ로 [PaymentCustomerSearchProjection] Port를 구현한다.
 *
 * **이 클래스는 암호 키도 Pepper도 갖지 않는다.** 받은 인덱스 문자열로 `*_index` 컬럼을
 * 비교하고, 돌려주는 값은 `*_masked` 컬럼뿐이다 — 검색 경로에서 복호화가 일어날 여지가
 * 구조적으로 없다(ADR-008).
 *
 * `idx_payment_customer_email`/`idx_payment_customer_phone`이 이 조회를 받는다.
 */
@Repository
class PaymentCustomerSearchProjectionAdapter(
	private val dsl: DSLContext,
) : PaymentCustomerSearchProjection {
	override fun findByEmailIndex(emailIndex: String): List<PaymentCustomerSearchEntry> =
		findByIndex(PAYMENT_CUSTOMER.CUSTOMER_EMAIL_INDEX, emailIndex)

	override fun findByPhoneIndex(phoneIndex: String): List<PaymentCustomerSearchEntry> =
		findByIndex(PAYMENT_CUSTOMER.CUSTOMER_PHONE_INDEX, phoneIndex)

	/**
	 * 이메일과 휴대전화가 **같은 조회를 컬럼만 바꿔 쓴다** — 두 벌로 복제하면 한쪽만 고쳐져
	 * 서로 다른 결과를 내기 시작한다(정렬 기준이 특히 그렇다).
	 *
	 * 최신순으로 준다. 문의를 받은 운영자가 가장 먼저 찾는 것은 방금 한 결제다.
	 */
	private fun findByIndex(
		indexColumn: TableField<out Record, String?>,
		indexValue: String,
	): List<PaymentCustomerSearchEntry> =
		dsl
			.select(
				PAYMENT.PAYMENT_ID,
				MERCHANT.MERCHANT_ID,
				MERCHANT.MERCHANT_NAME,
				PAYMENT.MERCHANT_ORDER_ID,
				PAYMENT.ORDER_NAME,
				PAYMENT.ORDER_AMOUNT,
				PAYMENT.PAYMENT_STATUS,
				PAYMENT_CUSTOMER.CUSTOMER_NAME_MASKED,
				PAYMENT_CUSTOMER.CUSTOMER_EMAIL_MASKED,
				PAYMENT_CUSTOMER.CUSTOMER_PHONE_MASKED,
				PAYMENT.PAID_AT,
				PAYMENT.CREATED_AT,
			).from(PAYMENT_CUSTOMER)
			.join(PAYMENT)
			.on(PAYMENT.PAYMENT_SEQ.eq(PAYMENT_CUSTOMER.PAYMENT_SEQ))
			.join(MERCHANT)
			.on(MERCHANT.MERCHANT_SEQ.eq(PAYMENT.MERCHANT_SEQ))
			.where(indexColumn.eq(indexValue))
			.orderBy(PAYMENT.CREATED_AT.desc())
			.fetch()
			.map { record ->
				PaymentCustomerSearchEntry(
					paymentId = PaymentId(checkNotNull(record[PAYMENT.PAYMENT_ID])),
					merchantId = MerchantId(checkNotNull(record[MERCHANT.MERCHANT_ID])),
					merchantName = checkNotNull(record[MERCHANT.MERCHANT_NAME]),
					merchantOrderId = MerchantOrderId(checkNotNull(record[PAYMENT.MERCHANT_ORDER_ID])),
					orderName = checkNotNull(record[PAYMENT.ORDER_NAME]),
					orderAmount = Money(checkNotNull(record[PAYMENT.ORDER_AMOUNT])),
					status = PaymentStatus.valueOf(checkNotNull(record[PAYMENT.PAYMENT_STATUS])),
					nameMasked = checkNotNull(record[PAYMENT_CUSTOMER.CUSTOMER_NAME_MASKED]),
					emailMasked = checkNotNull(record[PAYMENT_CUSTOMER.CUSTOMER_EMAIL_MASKED]),
					phoneMasked = checkNotNull(record[PAYMENT_CUSTOMER.CUSTOMER_PHONE_MASKED]),
					paidAt = record[PAYMENT.PAID_AT]?.toUtcInstant(),
					createdAt = checkNotNull(record[PAYMENT.CREATED_AT]).toUtcInstant(),
				)
			}
}
