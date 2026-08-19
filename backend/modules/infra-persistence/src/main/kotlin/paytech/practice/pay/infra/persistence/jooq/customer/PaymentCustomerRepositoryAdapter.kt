package paytech.practice.pay.infra.persistence.jooq.customer

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import paytech.practice.pay.application.port.outbound.EncryptedPaymentCustomer
import paytech.practice.pay.application.port.outbound.PaymentCustomerRepository
import paytech.practice.pay.dbcore.jooq.tables.PaymentCustomer.Companion.PAYMENT_CUSTOMER
import paytech.practice.pay.dbcore.jooq.tables.records.PaymentCustomerRecord
import paytech.practice.pay.domain.customer.PaymentCustomerId
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.infra.persistence.jooq.paymentId
import paytech.practice.pay.infra.persistence.jooq.paymentSeq
import paytech.practice.pay.infra.persistence.jooq.toUtcInstant
import paytech.practice.pay.infra.persistence.jooq.toUtcLocalDateTime

private val logger = KotlinLogging.logger {}

/**
 * jOOQ로 [PaymentCustomerRepository] Port를 구현한다.
 *
 * **이 클래스는 암호 키를 갖지 않는다.** 오가는 값이 이미 암호화된
 * [EncryptedPaymentCustomer]라 평문을 볼 수도, 만들 수도 없다 — 암복호는 application 계층의
 * `PaymentCustomerCrypto`가 하고, 그 이유는 Port의 KDoc에 있다(ADR-008).
 *
 * 다른 어댑터와 달리 `findById`가 없다 — 결제 1건당 1건이라(`uk_payment_customer_payment`)
 * 실제 진입점이 언제나 `PaymentId`이고, 공개 ID는 응답에 실어 보내는 용도다.
 */
@Repository
class PaymentCustomerRepositoryAdapter(
	private val dsl: DSLContext,
) : PaymentCustomerRepository {
	/**
	 * `payment_seq`로 기존 행을 찾아 INSERT/UPDATE를 가른다 — 결제 1건당 1건이므로 그것이
	 * 이 테이블의 멱등성 키다.
	 *
	 * `PaymentRepositoryAdapter`와 같은 방식의 낙관적 잠금(읽은 version + 1을 조건과 함께
	 * 쓴다)을 건다. **여기서는 그 한계가 실제로 문제가 된다** — 결제를 만들 때 한 번 쓰고
	 * 마는 `payment`와 달리 구매자 정보는 고객이 오타를 고치며 다시 쓸 수 있어서, 같은
	 * 세션의 중복 요청이 겹칠 수 있다. Use Case가 `CheckoutSession` 행을 `FOR UPDATE`로
	 * 잠근 채 부르므로 그 잠금이 실질적인 직렬화 지점이고, 이 version 확인은 그 뒤의
	 * 이중 방어다.
	 */
	override fun save(customer: EncryptedPaymentCustomer) {
		val existing =
			dsl
				.selectFrom(PAYMENT_CUSTOMER)
				.where(PAYMENT_CUSTOMER.PAYMENT_SEQ.eq(dsl.paymentSeq(customer.paymentId)))
				.fetchOne()

		if (existing == null) {
			dsl
				.newRecord(PAYMENT_CUSTOMER)
				.apply {
					fillFrom(customer)
					createdAt = customer.createdAt.toUtcLocalDateTime()
					version = 0L
				}.insert()
		} else {
			// **수정됐다는 사실이 DB 어디에도 남지 않는다** — 옛 값을 보관하면 파기가
			// 반쪽이 되므로 이력 테이블을 두지 않기로 했다(ADR-008). 그래서 "고객이 연락처를
			// 고쳤나"에 답할 수 있는 흔적은 이 로그 한 줄뿐이다.
			//
			// **무엇을 어떻게 고쳤는지는 찍지 않는다.** 옛 값이든 새 값이든 로그에 남기면
			// 지우려고 만든 구조를 로그가 그대로 우회한다.
			logger.info { "PaymentCustomer 수정(payment=${customer.paymentId.value})" }

			dsl
				.update(PAYMENT_CUSTOMER)
				.set(PAYMENT_CUSTOMER.CUSTOMER_NAME_ENCRYPTED, customer.nameEncrypted)
				.set(PAYMENT_CUSTOMER.CUSTOMER_NAME_MASKED, customer.nameMasked)
				.set(PAYMENT_CUSTOMER.CUSTOMER_EMAIL_ENCRYPTED, customer.emailEncrypted)
				.set(PAYMENT_CUSTOMER.CUSTOMER_EMAIL_MASKED, customer.emailMasked)
				.set(PAYMENT_CUSTOMER.CUSTOMER_EMAIL_INDEX, customer.emailIndex)
				.set(PAYMENT_CUSTOMER.CUSTOMER_PHONE_ENCRYPTED, customer.phoneEncrypted)
				.set(PAYMENT_CUSTOMER.CUSTOMER_PHONE_MASKED, customer.phoneMasked)
				.set(PAYMENT_CUSTOMER.CUSTOMER_PHONE_INDEX, customer.phoneIndex)
				.set(PAYMENT_CUSTOMER.UPDATED_AT, customer.updatedAt.toUtcLocalDateTime())
				.set(PAYMENT_CUSTOMER.VERSION, (existing.version ?: 0L) + 1)
				.where(PAYMENT_CUSTOMER.PAYMENT_CUSTOMER_SEQ.eq(existing.paymentCustomerSeq))
				.and(PAYMENT_CUSTOMER.VERSION.eq(existing.version))
				.execute()
				.also { updatedRows ->
					check(updatedRows == 1) {
						"PaymentCustomer(payment=${customer.paymentId.value}) 저장에 실패했습니다 — " +
							"동시에 변경된 것으로 보입니다(예상 version=${existing.version})."
					}
				}
		}
	}

	override fun findByPaymentId(paymentId: PaymentId): EncryptedPaymentCustomer? =
		dsl
			.selectFrom(PAYMENT_CUSTOMER)
			.where(PAYMENT_CUSTOMER.PAYMENT_SEQ.eq(dsl.paymentSeq(paymentId)))
			.fetchOne()
			?.toEncrypted()

	private fun PaymentCustomerRecord.fillFrom(customer: EncryptedPaymentCustomer) {
		paymentCustomerId = customer.id.value
		paymentSeq = dsl.paymentSeq(customer.paymentId)
		customerNameEncrypted = customer.nameEncrypted
		customerNameMasked = customer.nameMasked
		customerEmailEncrypted = customer.emailEncrypted
		customerEmailMasked = customer.emailMasked
		customerEmailIndex = customer.emailIndex
		customerPhoneEncrypted = customer.phoneEncrypted
		customerPhoneMasked = customer.phoneMasked
		customerPhoneIndex = customer.phoneIndex
		updatedAt = customer.updatedAt.toUtcLocalDateTime()
	}

	private fun PaymentCustomerRecord.toEncrypted(): EncryptedPaymentCustomer =
		EncryptedPaymentCustomer(
			id = PaymentCustomerId(checkNotNull(paymentCustomerId)),
			paymentId = dsl.paymentId(checkNotNull(paymentSeq)),
			nameEncrypted = checkNotNull(customerNameEncrypted),
			nameMasked = checkNotNull(customerNameMasked),
			emailEncrypted = checkNotNull(customerEmailEncrypted),
			emailMasked = checkNotNull(customerEmailMasked),
			emailIndex = checkNotNull(customerEmailIndex),
			phoneEncrypted = checkNotNull(customerPhoneEncrypted),
			phoneMasked = checkNotNull(customerPhoneMasked),
			phoneIndex = checkNotNull(customerPhoneIndex),
			createdAt = checkNotNull(createdAt).toUtcInstant(),
			updatedAt = checkNotNull(updatedAt).toUtcInstant(),
		)
}
