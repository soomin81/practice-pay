package paytech.practice.pay.application.sweep

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import paytech.practice.pay.application.port.outbound.CheckoutSessionRepository
import paytech.practice.pay.application.port.outbound.PaymentRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.domain.checkout.CheckoutSession
import paytech.practice.pay.domain.checkout.CheckoutSessionId
import paytech.practice.pay.domain.checkout.CheckoutSessionStatus
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.Payment
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.HttpUrl
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.TokenAmount
import paytech.practice.pay.domain.shared.WalletAddress
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private val NOW: Instant = Instant.parse("2026-07-19T00:00:00Z")
private val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
private val PAYMENT_ID = PaymentId("pay_target")
private val RECEIVING_WALLET = WalletAddress("0x" + "a".repeat(40))

private fun newPayment(): Payment =
	Payment.create(
		id = PAYMENT_ID,
		merchantId = MerchantId("mrc_test_001"),
		merchantOrderId = MerchantOrderId("order-001"),
		orderName = "테스트 주문",
		orderAmount = Money(10_000),
		paymentAsset = Asset.USDC,
		paymentAmount = TokenAmount(6_666_667),
		tokenDecimals = 6,
		network = BlockchainNetwork.BASE_SEPOLIA,
		receivingWallet = RECEIVING_WALLET,
		expiresAt = NOW.minusSeconds(60),
		createdAt = NOW.minusSeconds(3_600),
	)

private fun newSession(): CheckoutSession =
	CheckoutSession.create(
		id = CheckoutSessionId("cs_target"),
		paymentId = PAYMENT_ID,
		successUrl = HttpUrl("https://merchant.example.com/success"),
		cancelUrl = null,
		expiresAt = NOW.minusSeconds(60),
		createdAt = NOW.minusSeconds(3_600),
	)

private class ImmediateTransactionManager : TransactionManager {
	override fun <T> runInTransaction(block: () -> T): T = block()
}

private fun useCase(
	paymentRepository: PaymentRepository,
	checkoutSessionRepository: CheckoutSessionRepository,
) = ExpireCheckoutUseCase(paymentRepository, checkoutSessionRepository, ImmediateTransactionManager(), CLOCK)

private fun command() = ExpireCheckoutCommand(PAYMENT_ID)

class ExpireCheckoutUseCaseTest :
	FunSpec({

		test("a CREATED payment with no session is expired") {
			val payment = newPayment()
			val paymentRepository = mockk<PaymentRepository>()
			val sessionRepository = mockk<CheckoutSessionRepository>()
			every { paymentRepository.findByIdForUpdate(PAYMENT_ID) } returns payment
			every { sessionRepository.findByPaymentId(PAYMENT_ID) } returns null
			justRun { paymentRepository.save(any()) }

			useCase(paymentRepository, sessionRepository).execute(command())

			payment.status shouldBe PaymentStatus.EXPIRED
			verify { paymentRepository.save(payment) }
			verify(exactly = 0) { sessionRepository.save(any()) }
		}

		test("a CREATED payment and its pre-submit session are both expired") {
			val payment = newPayment()
			val session = newSession()
			val paymentRepository = mockk<PaymentRepository>()
			val sessionRepository = mockk<CheckoutSessionRepository>()
			every { paymentRepository.findByIdForUpdate(PAYMENT_ID) } returns payment
			every { sessionRepository.findByPaymentId(PAYMENT_ID) } returns session
			// 세션은 paymentId로 찾은 뒤 잠금 조회로 다시 읽는다(Use Case 참고).
			every { sessionRepository.findByIdForUpdate(session.id) } returns session
			justRun { paymentRepository.save(any()) }
			justRun { sessionRepository.save(any()) }

			useCase(paymentRepository, sessionRepository).execute(command())

			payment.status shouldBe PaymentStatus.EXPIRED
			session.status shouldBe CheckoutSessionStatus.EXPIRED
			verify { paymentRepository.save(payment) }
			verify { sessionRepository.save(session) }
		}

		test("payment and session are guarded independently") {
			// 이미 결제가 진행된 Payment(PROCESSING)는 건드리지 않고, 아직 만료 가능한 세션만 만료한다.
			val payment =
				newPayment().apply {
					ready(NOW.minusSeconds(50))
					submit(RECEIVING_WALLET, NOW.minusSeconds(40))
				}
			val session = newSession()
			val paymentRepository = mockk<PaymentRepository>()
			val sessionRepository = mockk<CheckoutSessionRepository>()
			every { paymentRepository.findByIdForUpdate(PAYMENT_ID) } returns payment
			every { sessionRepository.findByPaymentId(PAYMENT_ID) } returns session
			// 세션은 paymentId로 찾은 뒤 잠금 조회로 다시 읽는다(Use Case 참고).
			every { sessionRepository.findByIdForUpdate(session.id) } returns session
			justRun { sessionRepository.save(any()) }

			useCase(paymentRepository, sessionRepository).execute(command())

			payment.status shouldBe PaymentStatus.PROCESSING
			session.status shouldBe CheckoutSessionStatus.EXPIRED
			verify(exactly = 0) { paymentRepository.save(any()) }
			verify { sessionRepository.save(session) }
		}

		test("an already-terminal session is not re-expired") {
			val payment = newPayment()
			val session = newSession().apply { expire(NOW.minusSeconds(120)) }
			val paymentRepository = mockk<PaymentRepository>()
			val sessionRepository = mockk<CheckoutSessionRepository>()
			every { paymentRepository.findByIdForUpdate(PAYMENT_ID) } returns payment
			every { sessionRepository.findByPaymentId(PAYMENT_ID) } returns session
			// 세션은 paymentId로 찾은 뒤 잠금 조회로 다시 읽는다(Use Case 참고).
			every { sessionRepository.findByIdForUpdate(session.id) } returns session
			justRun { paymentRepository.save(any()) }

			useCase(paymentRepository, sessionRepository).execute(command())

			payment.status shouldBe PaymentStatus.EXPIRED
			verify(exactly = 0) { sessionRepository.save(any()) }
		}

		test("a missing payment is a no-op") {
			val paymentRepository = mockk<PaymentRepository>()
			val sessionRepository = mockk<CheckoutSessionRepository>()
			every { paymentRepository.findByIdForUpdate(PAYMENT_ID) } returns null
			every { sessionRepository.findByPaymentId(PAYMENT_ID) } returns null

			useCase(paymentRepository, sessionRepository).execute(command())

			verify(exactly = 0) { paymentRepository.save(any()) }
			verify(exactly = 0) { sessionRepository.save(any()) }
		}
	})
