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

private val NOW: Instant = Instant.parse("2026-07-17T00:00:00Z")
private val FIXED_CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
private val CS_ID = CheckoutSessionId("cs_test_001")
private val WALLET = WalletAddress("0x" + "b".repeat(40))

private fun newSession(): CheckoutSession =
	CheckoutSession.create(
		id = CS_ID,
		paymentId = PaymentId("pay_test_001"),
		successUrl = HttpUrl("https://merchant.example.com/success"),
		cancelUrl = null,
		expiresAt = NOW.plusSeconds(1_800),
		createdAt = NOW.minusSeconds(60),
	)

private fun newUseCase(checkoutSessionRepository: CheckoutSessionRepository): ConnectCheckoutWalletUseCase =
	ConnectCheckoutWalletUseCase(checkoutSessionRepository = checkoutSessionRepository, clock = FIXED_CLOCK)

class ConnectCheckoutWalletUseCaseTest :
	FunSpec({

		test("a CREATED session is opened and then connected in one call") {
			val checkoutSessionRepository = mockk<CheckoutSessionRepository>(relaxed = true)
			val session = newSession()
			every { checkoutSessionRepository.findById(CS_ID) } returns session

			val result = newUseCase(checkoutSessionRepository).execute(ConnectCheckoutWalletCommand(CS_ID, WALLET))

			result.checkoutSessionStatus shouldBe CheckoutSessionStatus.WALLET_CONNECTED
			result.connectedWallet shouldBe WALLET
			session.openedAt shouldBe NOW
			session.walletConnectedAt shouldBe NOW
			verify(exactly = 1) { checkoutSessionRepository.save(session) }
		}

		test("an already OPEN session is connected without re-opening") {
			val checkoutSessionRepository = mockk<CheckoutSessionRepository>(relaxed = true)
			val session = newSession()
			session.open(NOW.minusSeconds(30))
			every { checkoutSessionRepository.findById(CS_ID) } returns session

			val result = newUseCase(checkoutSessionRepository).execute(ConnectCheckoutWalletCommand(CS_ID, WALLET))

			result.checkoutSessionStatus shouldBe CheckoutSessionStatus.WALLET_CONNECTED
			session.openedAt shouldBe NOW.minusSeconds(30)
		}

		test("throws CheckoutSessionNotFoundException when the id does not exist") {
			val checkoutSessionRepository = mockk<CheckoutSessionRepository>()
			every { checkoutSessionRepository.findById(CS_ID) } returns null

			shouldThrow<CheckoutSessionNotFoundException> {
				newUseCase(checkoutSessionRepository).execute(ConnectCheckoutWalletCommand(CS_ID, WALLET))
			}
		}

		test("throws when the session is already WALLET_CONNECTED") {
			val checkoutSessionRepository = mockk<CheckoutSessionRepository>()
			val session = newSession()
			session.open(NOW.minusSeconds(30))
			session.connectWallet(WALLET, NOW.minusSeconds(20))
			every { checkoutSessionRepository.findById(CS_ID) } returns session

			shouldThrow<IllegalStateException> {
				newUseCase(checkoutSessionRepository).execute(ConnectCheckoutWalletCommand(CS_ID, WALLET))
			}
		}

		test("throws when the session is already CANCELLED") {
			val checkoutSessionRepository = mockk<CheckoutSessionRepository>()
			val session = newSession()
			session.cancel(NOW.minusSeconds(10))
			every { checkoutSessionRepository.findById(CS_ID) } returns session

			shouldThrow<IllegalStateException> {
				newUseCase(checkoutSessionRepository).execute(ConnectCheckoutWalletCommand(CS_ID, WALLET))
			}
		}
	})
