package paytech.practice.pay.application.checkout

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import paytech.practice.pay.application.port.outbound.CheckoutSessionRepository
import paytech.practice.pay.domain.checkout.CheckoutSession
import paytech.practice.pay.domain.checkout.CheckoutSessionId
import paytech.practice.pay.domain.checkout.CheckoutSessionStatus
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.shared.HttpUrl
import paytech.practice.pay.domain.shared.WalletAddress
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private val NOW: Instant = Instant.parse("2026-07-19T10:00:00Z")
private val SESSION_ID = CheckoutSessionId("cs_test_001")

private fun fixedClock(instant: Instant = NOW): Clock = Clock.fixed(instant, ZoneOffset.UTC)

private fun session(
	expiresAt: Instant = NOW.plusSeconds(1_800),
	cancelUrl: HttpUrl? = HttpUrl("https://merchant.example.com/cancel"),
): CheckoutSession =
	CheckoutSession.create(
		id = SESSION_ID,
		paymentId = PaymentId("pay_test_001"),
		successUrl = HttpUrl("https://merchant.example.com/done"),
		cancelUrl = cancelUrl,
		expiresAt = expiresAt,
		createdAt = NOW.minusSeconds(600),
	)

class CancelCheckoutSessionUseCaseTest :
	FunSpec({

		test("a CREATED session is cancelled and saved") {
			val checkoutSession = session()
			val repository = mockk<CheckoutSessionRepository>(relaxed = true)
			every { repository.findById(SESSION_ID) } returns checkoutSession

			val result =
				CancelCheckoutSessionUseCase(repository, fixedClock())
					.execute(CancelCheckoutSessionCommand(SESSION_ID))

			result.checkoutSessionStatus shouldBe CheckoutSessionStatus.CANCELLED
			result.cancelUrl shouldBe HttpUrl("https://merchant.example.com/cancel")
			verify(exactly = 1) { repository.save(checkoutSession) }
		}

		test("a WALLET_CONNECTED session can still be cancelled") {
			val checkoutSession =
				session().apply {
					open(NOW.minusSeconds(300))
					connectWallet(WalletAddress("0x" + "b".repeat(40)), NOW.minusSeconds(200))
				}
			val repository = mockk<CheckoutSessionRepository>(relaxed = true)
			every { repository.findById(SESSION_ID) } returns checkoutSession

			val result =
				CancelCheckoutSessionUseCase(repository, fixedClock())
					.execute(CancelCheckoutSessionCommand(SESSION_ID))

			result.checkoutSessionStatus shouldBe CheckoutSessionStatus.CANCELLED
		}

		test("a PAYMENT_SUBMITTED session cannot be cancelled") {
			val checkoutSession =
				session().apply {
					open(NOW.minusSeconds(300))
					connectWallet(WalletAddress("0x" + "b".repeat(40)), NOW.minusSeconds(200))
					submitPayment(NOW.minusSeconds(100))
				}
			val repository = mockk<CheckoutSessionRepository>(relaxed = true)
			every { repository.findById(SESSION_ID) } returns checkoutSession

			shouldThrow<CheckoutSessionNotCancellableException> {
				CancelCheckoutSessionUseCase(repository, fixedClock()).execute(CancelCheckoutSessionCommand(SESSION_ID))
			}
			verify(exactly = 0) { repository.save(any()) }
		}

		test("an expired session throws CheckoutSessionExpiredException, not NotCancellable") {
			// 상태는 아직 CREATED다 — 만료 Sweep Worker가 없어서 DB 상태는 그대로다.
			// 그래서 status가 아니라 expiresAt으로 판단해야 만료를 잡는다.
			val checkoutSession = session(expiresAt = NOW.minusSeconds(1))
			val repository = mockk<CheckoutSessionRepository>(relaxed = true)
			every { repository.findById(SESSION_ID) } returns checkoutSession

			shouldThrow<CheckoutSessionExpiredException> {
				CancelCheckoutSessionUseCase(repository, fixedClock()).execute(CancelCheckoutSessionCommand(SESSION_ID))
			}
			verify(exactly = 0) { repository.save(any()) }
		}

		test("an unknown session throws CheckoutSessionNotFoundException") {
			val repository = mockk<CheckoutSessionRepository>(relaxed = true)
			every { repository.findById(SESSION_ID) } returns null

			shouldThrow<CheckoutSessionNotFoundException> {
				CancelCheckoutSessionUseCase(repository, fixedClock()).execute(CancelCheckoutSessionCommand(SESSION_ID))
			}
		}

		test("a session with no cancelUrl returns null so the frontend stays put") {
			val checkoutSession = session(cancelUrl = null)
			val repository = mockk<CheckoutSessionRepository>(relaxed = true)
			every { repository.findById(SESSION_ID) } returns checkoutSession

			val result =
				CancelCheckoutSessionUseCase(repository, fixedClock())
					.execute(CancelCheckoutSessionCommand(SESSION_ID))

			result.cancelUrl shouldBe null
		}
	})
