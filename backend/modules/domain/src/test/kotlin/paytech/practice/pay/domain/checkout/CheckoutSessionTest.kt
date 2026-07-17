package paytech.practice.pay.domain.checkout

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.temporal.ChronoUnit
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.shared.HttpUrl
import paytech.practice.pay.domain.shared.WalletAddress

private val CREATED_AT: Instant = Instant.parse("2026-07-17T00:00:00Z")
private val EXPIRES_AT: Instant = CREATED_AT.plus(30, ChronoUnit.MINUTES)
private val CUSTOMER_WALLET = WalletAddress("0x" + "a".repeat(40))

private fun newSession(): CheckoutSession = CheckoutSession.create(
	id = CheckoutSessionId("cs_test_001"),
	paymentId = PaymentId("pay_test_001"),
	successUrl = HttpUrl("https://merchant.example.com/success"),
	cancelUrl = HttpUrl("https://merchant.example.com/cancel"),
	expiresAt = EXPIRES_AT,
	createdAt = CREATED_AT,
)

class CheckoutSessionTest : FunSpec({

	test("create starts in CREATED with no connected wallet or completedAt") {
		val session = newSession()

		session.status shouldBe CheckoutSessionStatus.CREATED
		session.connectedWallet.shouldBeNull()
		session.completedAt.shouldBeNull()
		session.updatedAt shouldBe CREATED_AT
	}

	test("create rejects an expiresAt that is not after createdAt") {
		shouldThrow<IllegalArgumentException> {
			CheckoutSession.create(
				id = CheckoutSessionId("cs_test_002"),
				paymentId = PaymentId("pay_test_001"),
				successUrl = HttpUrl("https://merchant.example.com/success"),
				cancelUrl = null,
				expiresAt = CREATED_AT,
				createdAt = CREATED_AT,
			)
		}
	}

	test("open moves CREATED to OPEN") {
		val session = newSession()
		val openedAt = CREATED_AT.plusSeconds(1)

		session.open(openedAt)

		session.status shouldBe CheckoutSessionStatus.OPEN
		session.openedAt shouldBe openedAt
		session.updatedAt shouldBe openedAt
	}

	test("open fails when not CREATED") {
		val session = newSession()
		session.open(CREATED_AT.plusSeconds(1))

		shouldThrow<IllegalStateException> { session.open(CREATED_AT.plusSeconds(2)) }
	}

	test("connectWallet moves OPEN to WALLET_CONNECTED and records the wallet") {
		val session = newSession()
		session.open(CREATED_AT.plusSeconds(1))
		val connectedAt = CREATED_AT.plusSeconds(2)

		session.connectWallet(CUSTOMER_WALLET, connectedAt)

		session.status shouldBe CheckoutSessionStatus.WALLET_CONNECTED
		session.connectedWallet shouldBe CUSTOMER_WALLET
		session.walletConnectedAt shouldBe connectedAt
	}

	test("connectWallet fails when not OPEN") {
		val session = newSession()

		shouldThrow<IllegalStateException> { session.connectWallet(CUSTOMER_WALLET, CREATED_AT.plusSeconds(1)) }
	}

	test("submitPayment moves WALLET_CONNECTED to PAYMENT_SUBMITTED") {
		val session = newSession()
		session.open(CREATED_AT.plusSeconds(1))
		session.connectWallet(CUSTOMER_WALLET, CREATED_AT.plusSeconds(2))
		val submittedAt = CREATED_AT.plusSeconds(3)

		session.submitPayment(submittedAt)

		session.status shouldBe CheckoutSessionStatus.PAYMENT_SUBMITTED
		session.paymentSubmittedAt shouldBe submittedAt
	}

	test("complete moves PAYMENT_SUBMITTED to COMPLETED") {
		val session = newSession()
		session.open(CREATED_AT.plusSeconds(1))
		session.connectWallet(CUSTOMER_WALLET, CREATED_AT.plusSeconds(2))
		session.submitPayment(CREATED_AT.plusSeconds(3))
		val completedAt = CREATED_AT.plusSeconds(4)

		session.complete(completedAt)

		session.status shouldBe CheckoutSessionStatus.COMPLETED
		session.completedAt shouldBe completedAt
	}

	test("complete fails when not PAYMENT_SUBMITTED") {
		val session = newSession()

		shouldThrow<IllegalStateException> { session.complete(CREATED_AT.plusSeconds(1)) }
	}

	test("cancel moves CREATED, OPEN or WALLET_CONNECTED to CANCELLED") {
		val fromCreated = newSession()
		fromCreated.cancel(CREATED_AT.plusSeconds(1))
		fromCreated.status shouldBe CheckoutSessionStatus.CANCELLED

		val fromOpen = newSession()
		fromOpen.open(CREATED_AT.plusSeconds(1))
		fromOpen.cancel(CREATED_AT.plusSeconds(2))
		fromOpen.status shouldBe CheckoutSessionStatus.CANCELLED

		val fromWalletConnected = newSession()
		fromWalletConnected.open(CREATED_AT.plusSeconds(1))
		fromWalletConnected.connectWallet(CUSTOMER_WALLET, CREATED_AT.plusSeconds(2))
		fromWalletConnected.cancel(CREATED_AT.plusSeconds(3))
		fromWalletConnected.status shouldBe CheckoutSessionStatus.CANCELLED
	}

	test("cancel fails once PAYMENT_SUBMITTED") {
		val session = newSession()
		session.open(CREATED_AT.plusSeconds(1))
		session.connectWallet(CUSTOMER_WALLET, CREATED_AT.plusSeconds(2))
		session.submitPayment(CREATED_AT.plusSeconds(3))

		shouldThrow<IllegalStateException> { session.cancel(CREATED_AT.plusSeconds(4)) }
	}

	test("expire moves CREATED, OPEN or WALLET_CONNECTED to EXPIRED") {
		val fromCreated = newSession()
		fromCreated.expire(CREATED_AT.plusSeconds(1))
		fromCreated.status shouldBe CheckoutSessionStatus.EXPIRED

		val fromWalletConnected = newSession()
		fromWalletConnected.open(CREATED_AT.plusSeconds(1))
		fromWalletConnected.connectWallet(CUSTOMER_WALLET, CREATED_AT.plusSeconds(2))
		fromWalletConnected.expire(CREATED_AT.plusSeconds(3))
		fromWalletConnected.status shouldBe CheckoutSessionStatus.EXPIRED
	}

	test("expire fails once PAYMENT_SUBMITTED") {
		val session = newSession()
		session.open(CREATED_AT.plusSeconds(1))
		session.connectWallet(CUSTOMER_WALLET, CREATED_AT.plusSeconds(2))
		session.submitPayment(CREATED_AT.plusSeconds(3))

		shouldThrow<IllegalStateException> { session.expire(CREATED_AT.plusSeconds(4)) }
	}

	test("reconstitute rejects COMPLETED without completedAt") {
		shouldThrow<IllegalArgumentException> {
			CheckoutSession.reconstitute(
				id = CheckoutSessionId("cs_test_003"),
				paymentId = PaymentId("pay_test_001"),
				successUrl = HttpUrl("https://merchant.example.com/success"),
				cancelUrl = null,
				expiresAt = EXPIRES_AT,
				createdAt = CREATED_AT,
				connectedWallet = CUSTOMER_WALLET,
				status = CheckoutSessionStatus.COMPLETED,
				openedAt = CREATED_AT.plusSeconds(1),
				walletConnectedAt = CREATED_AT.plusSeconds(2),
				paymentSubmittedAt = CREATED_AT.plusSeconds(3),
				completedAt = null,
				updatedAt = CREATED_AT.plusSeconds(3),
			)
		}
	}

	test("reconstitute restores a COMPLETED session faithfully") {
		val completedAt = CREATED_AT.plusSeconds(10)

		val session = CheckoutSession.reconstitute(
			id = CheckoutSessionId("cs_test_004"),
			paymentId = PaymentId("pay_test_001"),
			successUrl = HttpUrl("https://merchant.example.com/success"),
			cancelUrl = null,
			expiresAt = EXPIRES_AT,
			createdAt = CREATED_AT,
			connectedWallet = CUSTOMER_WALLET,
			status = CheckoutSessionStatus.COMPLETED,
			openedAt = CREATED_AT.plusSeconds(1),
			walletConnectedAt = CREATED_AT.plusSeconds(2),
			paymentSubmittedAt = CREATED_AT.plusSeconds(3),
			completedAt = completedAt,
			updatedAt = completedAt,
		)

		session.status shouldBe CheckoutSessionStatus.COMPLETED
		session.completedAt shouldBe completedAt
		session.connectedWallet shouldBe CUSTOMER_WALLET
	}
})
