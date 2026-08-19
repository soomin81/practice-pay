package paytech.practice.pay.infra.persistence.jooq.customer

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import paytech.practice.pay.application.port.outbound.EncryptedPaymentCustomer
import paytech.practice.pay.dbcore.jooq.tables.CustomerPiiAccessAudit.Companion.CUSTOMER_PII_ACCESS_AUDIT
import paytech.practice.pay.dbcore.jooq.tables.PaymentCustomer.Companion.PAYMENT_CUSTOMER
import paytech.practice.pay.domain.customer.CustomerPiiAccessAudit
import paytech.practice.pay.domain.customer.CustomerPiiAccessAuditId
import paytech.practice.pay.domain.customer.PaymentCustomerId
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalUser
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.infra.persistence.jooq.PersistenceTestSupport
import paytech.practice.pay.infra.persistence.jooq.identity.InternalUserRepositoryAdapter
import paytech.practice.pay.infra.persistence.jooq.insertTestMerchant
import paytech.practice.pay.infra.persistence.jooq.insertTestPayment
import paytech.practice.pay.infra.persistence.jooq.uniqueSuffix
import java.time.Instant

private val NOW: Instant = Instant.parse("2026-08-19T00:00:00Z")

/**
 * Base64 암호문의 실제 길이를 흉내낸다 — `*_encrypted` 컬럼이 `VARCHAR(512)`라 **긴 값이
 * 잘리는지**는 실제 MySQL에 넣어 봐야 드러난다. 12바이트 IV + 평문 + 16바이트 태그를
 * Base64로 담으면 이 정도 자릿수가 된다.
 */
private fun ciphertext(): String = "enc." + "A".repeat(200) + uniqueSuffix()

private fun savedPaymentId(): PaymentId = PaymentId(insertTestPayment(insertTestMerchant()))

private fun encrypted(
	paymentId: PaymentId,
	id: PaymentCustomerId = PaymentCustomerId("pcu_${uniqueSuffix()}"),
	emailEncrypted: String = ciphertext(),
	emailMasked: String = "gi***@example.com",
	updatedAt: Instant = NOW,
): EncryptedPaymentCustomer =
	EncryptedPaymentCustomer(
		id = id,
		paymentId = paymentId,
		nameEncrypted = ciphertext(),
		// 마스킹된 한글 이름이 그대로 돌아오는지 — 컬럼이 utf8mb4가 아니면 여기서 깨진다.
		nameMasked = "홍*동",
		emailEncrypted = emailEncrypted,
		emailMasked = emailMasked,
		// Blind Index는 CHAR(64)다 — hex SHA-256의 길이와 정확히 같아야 한다.
		emailIndex = "a".repeat(64),
		phoneEncrypted = ciphertext(),
		phoneMasked = "010-****-5678",
		phoneIndex = "b".repeat(64),
		createdAt = NOW,
		updatedAt = updatedAt,
	)

private fun savedInternalUser(): InternalUser {
	val user =
		InternalUser.bootstrap(
			id = InternalUserId("iu_${uniqueSuffix()}"),
			loginId = LoginId("admin-${uniqueSuffix()}"),
			email = Email("${uniqueSuffix()}@example.com"),
			userName = "테스트 관리자",
			passwordHash = "hashed-password",
			createdAt = NOW,
		)
	InternalUserRepositoryAdapter(PersistenceTestSupport.dsl).save(user)
	return user
}

class PaymentCustomerAdapterTest :
	FunSpec({
		val dsl = PersistenceTestSupport.dsl
		val repository = PaymentCustomerRepositoryAdapter(dsl)
		val auditRecorder = CustomerPiiAccessAuditRepositoryAdapter(dsl)

		test("saves and restores every column unchanged") {
			val paymentId = savedPaymentId()
			val customer = encrypted(paymentId)

			repository.save(customer)

			repository.findByPaymentId(paymentId) shouldBe customer
		}

		test("returns null when the payment has no customer info") {
			repository.findByPaymentId(savedPaymentId()).shouldBeNull()
		}

		/**
		 * 고객이 오타를 고치면 같은 행을 덮어쓴다 — `payment_seq`가 `UNIQUE`라 두 행이 생기면
		 * INSERT 자체가 제약 위반으로 실패한다.
		 */
		test("a second save updates the same row and bumps the version") {
			val paymentId = savedPaymentId()
			val first = encrypted(paymentId)
			repository.save(first)

			val corrected =
				encrypted(
					paymentId = paymentId,
					id = first.id,
					emailEncrypted = "enc.corrected",
					emailMasked = "fi***@example.com",
					updatedAt = NOW.plusSeconds(60),
				)
			repository.save(corrected)

			val stored = repository.findByPaymentId(paymentId)
			stored.shouldNotBeNull()
			stored.emailMasked shouldBe "fi***@example.com"
			// createdAt은 처음 저장한 값을 유지한다 — 고친 것이지 새로 만든 것이 아니다.
			stored.createdAt shouldBe NOW
			stored.updatedAt shouldBe NOW.plusSeconds(60)

			dsl.fetchCount(PAYMENT_CUSTOMER, PAYMENT_CUSTOMER.PAYMENT_CUSTOMER_ID.eq(first.id.value)) shouldBe 1
			dsl
				.select(PAYMENT_CUSTOMER.VERSION)
				.from(PAYMENT_CUSTOMER)
				.where(PAYMENT_CUSTOMER.PAYMENT_CUSTOMER_ID.eq(first.id.value))
				.fetchOne(PAYMENT_CUSTOMER.VERSION) shouldBe 1L
		}

		test("append records who looked at the original and why") {
			val user = savedInternalUser()
			val paymentId = savedPaymentId()
			val auditId = CustomerPiiAccessAuditId("cpa_${uniqueSuffix()}")

			auditRecorder.append(
				CustomerPiiAccessAudit(
					id = auditId,
					internalUserId = user.id,
					paymentId = paymentId,
					reason = "결제 실패 문의 대응",
					clientIp = "203.0.113.7",
					occurredAt = NOW,
				),
			)

			val row =
				dsl
					.selectFrom(CUSTOMER_PII_ACCESS_AUDIT)
					.where(CUSTOMER_PII_ACCESS_AUDIT.CUSTOMER_PII_ACCESS_AUDIT_ID.eq(auditId.value))
					.fetchOne()
			row.shouldNotBeNull()
			row.reason shouldBe "결제 실패 문의 대응"
			row.clientIp shouldBe "203.0.113.7"
		}

		/** 프록시 뒤에서는 IP를 알 수 없을 수 있다 — 없다고 열람을 막지는 않는다. */
		test("a missing client IP is stored as null") {
			val user = savedInternalUser()
			val auditId = CustomerPiiAccessAuditId("cpa_${uniqueSuffix()}")

			auditRecorder.append(
				CustomerPiiAccessAudit(
					id = auditId,
					internalUserId = user.id,
					paymentId = savedPaymentId(),
					reason = "정기 점검",
					clientIp = null,
					occurredAt = NOW,
				),
			)

			dsl
				.selectFrom(CUSTOMER_PII_ACCESS_AUDIT)
				.where(CUSTOMER_PII_ACCESS_AUDIT.CUSTOMER_PII_ACCESS_AUDIT_ID.eq(auditId.value))
				.fetchOne()
				?.clientIp
				.shouldBeNull()
		}
	})
