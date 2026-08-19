package paytech.practice.pay.application.checkout

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import paytech.practice.pay.application.customer.PaymentCustomerCrypto
import paytech.practice.pay.application.port.outbound.CheckoutSessionRepository
import paytech.practice.pay.application.port.outbound.EncryptedPaymentCustomer
import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.PaymentCustomerRepository
import paytech.practice.pay.application.port.outbound.PiiBlindIndexer
import paytech.practice.pay.application.port.outbound.PiiEncryptor
import paytech.practice.pay.domain.checkout.CheckoutSession
import paytech.practice.pay.domain.checkout.CheckoutSessionId
import paytech.practice.pay.domain.checkout.CheckoutSessionStatus
import paytech.practice.pay.domain.customer.CustomerEmail
import paytech.practice.pay.domain.customer.CustomerName
import paytech.practice.pay.domain.customer.CustomerPhone
import paytech.practice.pay.domain.customer.PaymentCustomerId
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.shared.HttpUrl
import paytech.practice.pay.domain.shared.WalletAddress
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private val NOW: Instant = Instant.parse("2026-08-19T00:00:00Z")
private val FIXED_CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
private val CS_ID = CheckoutSessionId("cs_test_001")
private val PAY_ID = PaymentId("pay_test_001")

private val NAME = CustomerName("홍길동")
private val EMAIL = CustomerEmail("gildong@example.com")
private val PHONE = CustomerPhone("010-1234-5678")

private fun newSession(): CheckoutSession =
	CheckoutSession.create(
		id = CS_ID,
		paymentId = PAY_ID,
		successUrl = HttpUrl("https://merchant.example.com/success"),
		cancelUrl = null,
		expiresAt = NOW.plusSeconds(1_800),
		createdAt = NOW.minusSeconds(60),
	)

/**
 * 앞뒤에 표식을 붙여 되돌리는 가짜 암호화다 — 실제 AES-GCM은 `AesGcmPiiEncryptorTest`가
 * 검증한다. 여기서 확인하려는 것은 **평문이 저장 형태로 나갈 때 반드시 이 변환을 거치는가**
 * 이지 암호 자체가 아니다.
 */
private class ReversiblePiiEncryptor : PiiEncryptor {
	override fun encrypt(plaintext: String): String = "enc($plaintext)"

	override fun decrypt(ciphertext: String): String = ciphertext.removePrefix("enc(").removeSuffix(")")
}

private fun newCrypto(): PaymentCustomerCrypto =
	PaymentCustomerCrypto(
		piiEncryptor = ReversiblePiiEncryptor(),
		piiBlindIndexer = PiiBlindIndexer { "idx($it)" },
	)

private fun newUseCase(
	checkoutSessionRepository: CheckoutSessionRepository,
	paymentCustomerRepository: PaymentCustomerRepository,
): SubmitCheckoutCustomerUseCase =
	SubmitCheckoutCustomerUseCase(
		checkoutSessionRepository = checkoutSessionRepository,
		paymentCustomerRepository = paymentCustomerRepository,
		paymentCustomerCrypto = newCrypto(),
		idGenerator = IdGenerator { "generated" },
		transactionManager = ImmediateTransactionManager(),
		clock = FIXED_CLOCK,
	)

private fun command(
	name: CustomerName = NAME,
	email: CustomerEmail = EMAIL,
	phone: CustomerPhone = PHONE,
): SubmitCheckoutCustomerCommand = SubmitCheckoutCustomerCommand(CS_ID, name, email, phone)

class SubmitCheckoutCustomerUseCaseTest :
	FunSpec({

		test("a CREATED session is opened by the customer info step") {
			val checkoutSessionRepository = mockk<CheckoutSessionRepository>(relaxed = true)
			val paymentCustomerRepository = mockk<PaymentCustomerRepository>(relaxed = true)
			val session = newSession()
			every { checkoutSessionRepository.findByIdForUpdate(CS_ID) } returns session
			every { paymentCustomerRepository.findByPaymentId(PAY_ID) } returns null

			val result = newUseCase(checkoutSessionRepository, paymentCustomerRepository).execute(command())

			result.checkoutSessionStatus shouldBe CheckoutSessionStatus.OPEN
			session.openedAt shouldBe NOW
			verify(exactly = 1) { checkoutSessionRepository.save(session) }
		}

		test("stores the encrypted, masked and indexed values") {
			val checkoutSessionRepository = mockk<CheckoutSessionRepository>(relaxed = true)
			val paymentCustomerRepository = mockk<PaymentCustomerRepository>(relaxed = true)
			every { checkoutSessionRepository.findByIdForUpdate(CS_ID) } returns newSession()
			every { paymentCustomerRepository.findByPaymentId(PAY_ID) } returns null
			val saved = slot<EncryptedPaymentCustomer>()

			newUseCase(checkoutSessionRepository, paymentCustomerRepository).execute(command())

			verify(exactly = 1) { paymentCustomerRepository.save(capture(saved)) }
			saved.captured.id shouldBe PaymentCustomerId("pcu_generated")
			saved.captured.paymentId shouldBe PAY_ID
			saved.captured.emailEncrypted shouldBe "enc(gildong@example.com)"
			saved.captured.emailMasked shouldBe "gi***@example.com"
			saved.captured.phoneMasked shouldBe "010-****-5678"
			saved.captured.createdAt shouldBe NOW
		}

		/**
		 * Blind Index는 **정규화된 값**으로 만들어야 한다 — 원문을 그대로 넣으면
		 * `A@b.com`과 `a@b.com`이 다른 인덱스를 가져 같은 사람이 검색에 걸리지 않는다.
		 */
		test("the blind index is built from the normalized value, not the raw input") {
			val checkoutSessionRepository = mockk<CheckoutSessionRepository>(relaxed = true)
			val paymentCustomerRepository = mockk<PaymentCustomerRepository>(relaxed = true)
			every { checkoutSessionRepository.findByIdForUpdate(CS_ID) } returns newSession()
			every { paymentCustomerRepository.findByPaymentId(PAY_ID) } returns null
			val saved = slot<EncryptedPaymentCustomer>()

			newUseCase(checkoutSessionRepository, paymentCustomerRepository)
				.execute(command(email = CustomerEmail("GilDong@Example.com"), phone = CustomerPhone("01012345678")))

			verify { paymentCustomerRepository.save(capture(saved)) }
			saved.captured.emailIndex shouldBe "idx(gildong@example.com)"
			saved.captured.phoneIndex shouldBe "idx(01012345678)"
			// 암호문은 입력 그대로를 담는다 — 정규화는 인덱스만의 관심사다.
			saved.captured.emailEncrypted shouldBe "enc(GilDong@Example.com)"
		}

		/** 고객이 오타를 냈을 때 결제를 처음부터 다시 만들게 하는 것은 과하다. */
		test("a second submission overwrites the existing row and keeps createdAt") {
			val checkoutSessionRepository = mockk<CheckoutSessionRepository>(relaxed = true)
			val paymentCustomerRepository = mockk<PaymentCustomerRepository>(relaxed = true)
			every { checkoutSessionRepository.findByIdForUpdate(CS_ID) } returns newSession().apply { open(NOW.minusSeconds(30)) }
			every { paymentCustomerRepository.findByPaymentId(PAY_ID) } returns
				EncryptedPaymentCustomer(
					id = PaymentCustomerId("pcu_existing"),
					paymentId = PAY_ID,
					nameEncrypted = "enc(홍길동)",
					nameMasked = "홍*동",
					emailEncrypted = "enc(typo@example.com)",
					emailMasked = "ty***@example.com",
					emailIndex = "idx(typo@example.com)",
					phoneEncrypted = "enc(010-1234-5678)",
					phoneMasked = "010-****-5678",
					phoneIndex = "idx(01012345678)",
					createdAt = NOW.minusSeconds(120),
					updatedAt = NOW.minusSeconds(120),
				)
			val saved = slot<EncryptedPaymentCustomer>()

			newUseCase(checkoutSessionRepository, paymentCustomerRepository)
				.execute(command(email = CustomerEmail("fixed@example.com")))

			verify(exactly = 1) { paymentCustomerRepository.save(capture(saved)) }
			// 새 ID를 발급하지 않는다 — 같은 행을 고친다.
			saved.captured.id shouldBe PaymentCustomerId("pcu_existing")
			saved.captured.emailEncrypted shouldBe "enc(fixed@example.com)"
			saved.captured.createdAt shouldBe NOW.minusSeconds(120)
			saved.captured.updatedAt shouldBe NOW
		}

		/** 응답에 원본이 실리면 로그·에러 리포트로 새는 경로가 하나 생긴다. */
		test("the result carries masked values only") {
			val checkoutSessionRepository = mockk<CheckoutSessionRepository>(relaxed = true)
			val paymentCustomerRepository = mockk<PaymentCustomerRepository>(relaxed = true)
			every { checkoutSessionRepository.findByIdForUpdate(CS_ID) } returns newSession()
			every { paymentCustomerRepository.findByPaymentId(PAY_ID) } returns null

			val result = newUseCase(checkoutSessionRepository, paymentCustomerRepository).execute(command())

			result.nameMasked shouldBe "홍*동"
			result.emailMasked shouldNotContain "gildong"
			result.phoneMasked shouldNotContain "1234"
		}

		test("customer info is still accepted after the wallet is connected") {
			val checkoutSessionRepository = mockk<CheckoutSessionRepository>(relaxed = true)
			val paymentCustomerRepository = mockk<PaymentCustomerRepository>(relaxed = true)
			val session =
				newSession().apply {
					open(NOW.minusSeconds(30))
					connectWallet(WalletAddress("0x" + "b".repeat(40)), NOW.minusSeconds(10))
				}
			every { checkoutSessionRepository.findByIdForUpdate(CS_ID) } returns session
			every { paymentCustomerRepository.findByPaymentId(PAY_ID) } returns null

			val result = newUseCase(checkoutSessionRepository, paymentCustomerRepository).execute(command())

			result.checkoutSessionStatus shouldBe CheckoutSessionStatus.WALLET_CONNECTED
		}

		/**
		 * 전송이 브로드캐스트된 뒤에 연락처가 바뀌면 그 결제에 문제가 생겼을 때 연락할
		 * 상대가 소리 없이 달라진다.
		 */
		test("refuses customer info once the payment has been submitted") {
			val checkoutSessionRepository = mockk<CheckoutSessionRepository>(relaxed = true)
			val paymentCustomerRepository = mockk<PaymentCustomerRepository>(relaxed = true)
			val session =
				newSession().apply {
					open(NOW.minusSeconds(30))
					connectWallet(WalletAddress("0x" + "b".repeat(40)), NOW.minusSeconds(20))
					submitPayment(NOW.minusSeconds(10))
				}
			every { checkoutSessionRepository.findByIdForUpdate(CS_ID) } returns session

			shouldThrow<CheckoutCustomerNotEditableException> {
				newUseCase(checkoutSessionRepository, paymentCustomerRepository).execute(command())
			}
			verify(exactly = 0) { paymentCustomerRepository.save(any()) }
		}

		test("throws CheckoutSessionExpiredException past the expiry instant") {
			val checkoutSessionRepository = mockk<CheckoutSessionRepository>(relaxed = true)
			val paymentCustomerRepository = mockk<PaymentCustomerRepository>(relaxed = true)
			every { checkoutSessionRepository.findByIdForUpdate(CS_ID) } returns
				CheckoutSession.create(
					id = CS_ID,
					paymentId = PAY_ID,
					successUrl = HttpUrl("https://merchant.example.com/success"),
					cancelUrl = null,
					expiresAt = NOW.minusSeconds(1),
					createdAt = NOW.minusSeconds(1_800),
				)

			shouldThrow<CheckoutSessionExpiredException> {
				newUseCase(checkoutSessionRepository, paymentCustomerRepository).execute(command())
			}
		}

		test("throws CheckoutSessionNotFoundException when the id does not exist") {
			val checkoutSessionRepository = mockk<CheckoutSessionRepository>()
			val paymentCustomerRepository = mockk<PaymentCustomerRepository>()
			every { checkoutSessionRepository.findByIdForUpdate(CS_ID) } returns null

			shouldThrow<CheckoutSessionNotFoundException> {
				newUseCase(checkoutSessionRepository, paymentCustomerRepository).execute(command())
			}
		}
	})
