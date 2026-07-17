package paytech.practice.pay.infra.persistence.jooq.checkout

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import paytech.practice.pay.domain.checkout.CheckoutSession
import paytech.practice.pay.domain.checkout.CheckoutSessionId
import paytech.practice.pay.domain.checkout.CheckoutSessionStatus
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.shared.HttpUrl
import paytech.practice.pay.infra.persistence.jooq.PersistenceTestSupport
import paytech.practice.pay.infra.persistence.jooq.insertTestMerchant
import paytech.practice.pay.infra.persistence.jooq.insertTestPayment
import paytech.practice.pay.infra.persistence.jooq.uniqueSuffix
import java.time.Instant

private val NOW: Instant = Instant.parse("2026-07-17T00:00:00Z")

class CheckoutSessionRepositoryAdapterTest :
	FunSpec({
		val adapter = CheckoutSessionRepositoryAdapter(PersistenceTestSupport.dsl)

		fun newSession(paymentId: PaymentId): CheckoutSession =
			CheckoutSession.create(
				id = CheckoutSessionId("cs_${uniqueSuffix()}"),
				paymentId = paymentId,
				successUrl = HttpUrl("https://merchant.example.com/success"),
				cancelUrl = HttpUrl("https://merchant.example.com/cancel"),
				expiresAt = NOW.plusSeconds(1_800),
				createdAt = NOW,
			)

		test("save inserts a new CheckoutSession and findByPaymentId round-trips it") {
			val merchantId = insertTestMerchant()
			val paymentId = PaymentId(insertTestPayment(merchantId))
			val session = newSession(paymentId)

			adapter.save(session)
			val found = adapter.findByPaymentId(paymentId)

			found.shouldNotBeNull()
			found.id shouldBe session.id
			found.status shouldBe CheckoutSessionStatus.CREATED
			found.successUrl shouldBe session.successUrl
			found.cancelUrl shouldBe session.cancelUrl
		}

		test("save persists a status transition on an existing CheckoutSession") {
			val merchantId = insertTestMerchant()
			val paymentId = PaymentId(insertTestPayment(merchantId))
			val session = newSession(paymentId)
			adapter.save(session)

			session.open(NOW.plusSeconds(1))
			adapter.save(session)

			val found = adapter.findByPaymentId(paymentId)
			found.shouldNotBeNull()
			found.status shouldBe CheckoutSessionStatus.OPEN
			found.openedAt shouldBe NOW.plusSeconds(1)
		}

		test("findByPaymentId returns null when the payment has no session") {
			val merchantId = insertTestMerchant()
			val paymentId = PaymentId(insertTestPayment(merchantId))

			adapter.findByPaymentId(paymentId).shouldBeNull()
		}
	})
