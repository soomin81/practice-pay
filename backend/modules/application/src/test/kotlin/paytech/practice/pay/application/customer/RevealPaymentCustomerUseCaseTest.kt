package paytech.practice.pay.application.customer

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import paytech.practice.pay.application.port.outbound.CustomerPiiAccessAuditRepository
import paytech.practice.pay.application.port.outbound.EncryptedPaymentCustomer
import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.PaymentCustomerRepository
import paytech.practice.pay.application.port.outbound.PaymentRepository
import paytech.practice.pay.application.port.outbound.PiiBlindIndexer
import paytech.practice.pay.application.port.outbound.PiiEncryptor
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.domain.customer.CustomerEmail
import paytech.practice.pay.domain.customer.CustomerPiiAccessAudit
import paytech.practice.pay.domain.customer.PaymentCustomerId
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.payment.Payment
import paytech.practice.pay.domain.payment.PaymentId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private val NOW: Instant = Instant.parse("2026-08-20T00:00:00Z")
private val FIXED_CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
private val PAY_ID = PaymentId("pay_test_001")
private val ACTOR = InternalUserId("iu_sa01")

private class ImmediateTransactionManager : TransactionManager {
	override fun <T> runInTransaction(block: () -> T): T = block()
}

/** 앞뒤 표식만 붙였다 떼는 가짜 암호화 — 실제 AES-GCM은 `AesGcmPiiEncryptorTest`가 본다. */
private class ReversiblePiiEncryptor : PiiEncryptor {
	override fun encrypt(plaintext: String): String = "enc($plaintext)"

	override fun decrypt(ciphertext: String): String = ciphertext.removePrefix("enc(").removeSuffix(")")
}

private fun stored() =
	EncryptedPaymentCustomer(
		id = PaymentCustomerId("pcu_001"),
		paymentId = PAY_ID,
		nameEncrypted = "enc(홍길동)",
		nameMasked = "홍*동",
		emailEncrypted = "enc(gildong@example.com)",
		emailMasked = "gi***@example.com",
		emailIndex = "idx(gildong@example.com)",
		phoneEncrypted = "enc(010-1234-5678)",
		phoneMasked = "010-****-5678",
		phoneIndex = "idx(01012345678)",
		createdAt = NOW.minusSeconds(600),
		updatedAt = NOW.minusSeconds(600),
	)

private fun newUseCase(
	paymentRepository: PaymentRepository,
	paymentCustomerRepository: PaymentCustomerRepository,
	auditRepository: CustomerPiiAccessAuditRepository,
): RevealPaymentCustomerUseCase =
	RevealPaymentCustomerUseCase(
		paymentRepository = paymentRepository,
		paymentCustomerRepository = paymentCustomerRepository,
		paymentCustomerCrypto = PaymentCustomerCrypto(ReversiblePiiEncryptor(), PiiBlindIndexer { "idx($it)" }),
		customerPiiAccessAuditRepository = auditRepository,
		idGenerator = IdGenerator { "generated" },
		transactionManager = ImmediateTransactionManager(),
		clock = FIXED_CLOCK,
	)

private fun command(reason: String = "결제 실패 문의 대응") =
	RevealPaymentCustomerCommand(
		paymentId = PAY_ID,
		actorInternalUserId = ACTOR,
		reason = reason,
		clientIp = "203.0.113.7",
	)

class RevealPaymentCustomerUseCaseTest :
	FunSpec({

		test("decrypts the stored values and returns the plaintext") {
			val paymentRepository = mockk<PaymentRepository>()
			val customerRepository = mockk<PaymentCustomerRepository>()
			val auditRepository = mockk<CustomerPiiAccessAuditRepository>(relaxed = true)
			every { paymentRepository.findById(PAY_ID) } returns mockk<Payment>()
			every { customerRepository.findByPaymentId(PAY_ID) } returns stored()

			val result = newUseCase(paymentRepository, customerRepository, auditRepository).execute(command())

			result.email shouldBe CustomerEmail("gildong@example.com")
			result.revealedAt shouldBe NOW
		}

		/**
		 * **이 Use Case의 존재 이유다.** 원문은 이미 나갔는데 기록만 빠지면 "누가 봤나"에
		 * 영영 답할 수 없다.
		 */
		test("records who looked, why, and from where") {
			val paymentRepository = mockk<PaymentRepository>()
			val customerRepository = mockk<PaymentCustomerRepository>()
			val auditRepository = mockk<CustomerPiiAccessAuditRepository>(relaxed = true)
			every { paymentRepository.findById(PAY_ID) } returns mockk<Payment>()
			every { customerRepository.findByPaymentId(PAY_ID) } returns stored()
			val audit = slot<CustomerPiiAccessAudit>()

			newUseCase(paymentRepository, customerRepository, auditRepository).execute(command())

			verify(exactly = 1) { auditRepository.append(capture(audit)) }
			audit.captured.internalUserId shouldBe ACTOR
			audit.captured.paymentId shouldBe PAY_ID
			audit.captured.reason shouldBe "결제 실패 문의 대응"
			audit.captured.clientIp shouldBe "203.0.113.7"
			audit.captured.occurredAt shouldBe NOW
		}

		/** 남기면 `payment_customer` 행을 지워도 원문이 감사 로그에 남아 파기가 반쪽이 된다. */
		test("the audit entry never carries the values that were revealed") {
			val paymentRepository = mockk<PaymentRepository>()
			val customerRepository = mockk<PaymentCustomerRepository>()
			val auditRepository = mockk<CustomerPiiAccessAuditRepository>(relaxed = true)
			every { paymentRepository.findById(PAY_ID) } returns mockk<Payment>()
			every { customerRepository.findByPaymentId(PAY_ID) } returns stored()
			val audit = slot<CustomerPiiAccessAudit>()

			newUseCase(paymentRepository, customerRepository, auditRepository).execute(command())

			verify { auditRepository.append(capture(audit)) }
			audit.captured.reason shouldBe "결제 실패 문의 대응"
			// 기록에 담기는 것은 사유와 식별자뿐이다 — 원문이 들어갈 필드 자체가 없다.
			audit.captured.toString().contains("gildong@example.com") shouldBe false
		}

		test("refuses a blank reason and does not decrypt") {
			val paymentRepository = mockk<PaymentRepository>()
			val customerRepository = mockk<PaymentCustomerRepository>(relaxed = true)
			val auditRepository = mockk<CustomerPiiAccessAuditRepository>(relaxed = true)

			shouldThrow<IllegalArgumentException> {
				newUseCase(paymentRepository, customerRepository, auditRepository).execute(command(reason = "   "))
			}
			verify(exactly = 0) { customerRepository.findByPaymentId(any()) }
			verify(exactly = 0) { auditRepository.append(any()) }
		}

		/** 없는 결제 ID로 부르면 어댑터가 seq 해석에 실패해 500이 된다 — 앞에서 404로 막는다. */
		test("an unknown payment is reported as not found") {
			val paymentRepository = mockk<PaymentRepository>()
			val customerRepository = mockk<PaymentCustomerRepository>(relaxed = true)
			val auditRepository = mockk<CustomerPiiAccessAuditRepository>(relaxed = true)
			every { paymentRepository.findById(PAY_ID) } returns null

			shouldThrow<PaymentCustomerNotFoundException> {
				newUseCase(paymentRepository, customerRepository, auditRepository).execute(command())
			}
			verify(exactly = 0) { customerRepository.findByPaymentId(any()) }
		}

		test("a payment without customer info is reported as not found") {
			val paymentRepository = mockk<PaymentRepository>()
			val customerRepository = mockk<PaymentCustomerRepository>()
			val auditRepository = mockk<CustomerPiiAccessAuditRepository>(relaxed = true)
			every { paymentRepository.findById(PAY_ID) } returns mockk<Payment>()
			every { customerRepository.findByPaymentId(PAY_ID) } returns null

			shouldThrow<PaymentCustomerNotFoundException> {
				newUseCase(paymentRepository, customerRepository, auditRepository).execute(command())
			}
			verify(exactly = 0) { auditRepository.append(any()) }
		}
	})
