package paytech.practice.pay.infra.persistence.jooq.payment

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.Payment
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.TokenAmount
import paytech.practice.pay.domain.shared.WalletAddress
import paytech.practice.pay.infra.persistence.jooq.PersistenceTestSupport
import paytech.practice.pay.infra.persistence.jooq.insertTestMerchant
import paytech.practice.pay.infra.persistence.jooq.uniqueSuffix
import java.time.Instant

private val NOW: Instant = Instant.parse("2026-07-17T00:00:00Z")
private val RECEIVING_WALLET = WalletAddress("0x" + "a".repeat(40))

private fun newPayment(
	merchantId: MerchantId,
	merchantOrderId: MerchantOrderId = MerchantOrderId("order-${uniqueSuffix()}"),
): Payment =
	Payment.create(
		id = PaymentId("pay_${uniqueSuffix()}"),
		merchantId = merchantId,
		merchantOrderId = merchantOrderId,
		orderName = "테스트 주문",
		orderAmount = Money(10_000),
		paymentAsset = Asset.USDC,
		paymentAmount = TokenAmount(6_666_667),
		tokenDecimals = 6,
		network = BlockchainNetwork.BASE_SEPOLIA,
		receivingWallet = RECEIVING_WALLET,
		expiresAt = NOW.plusSeconds(1_800),
		createdAt = NOW,
	)

class PaymentRepositoryAdapterTest :
	FunSpec({
		val adapter = PaymentRepositoryAdapter(PersistenceTestSupport.dsl)

		test("save inserts a new Payment and findByMerchantOrderId round-trips it") {
			val merchantId = MerchantId(insertTestMerchant())
			val payment = newPayment(merchantId)

			adapter.save(payment)
			val found = adapter.findByMerchantOrderId(merchantId, payment.merchantOrderId)

			found.shouldNotBeNull()
			found.id shouldBe payment.id
			found.status shouldBe PaymentStatus.CREATED
			found.orderAmount shouldBe payment.orderAmount
			found.paymentAmount shouldBe payment.paymentAmount
			found.receivingWallet shouldBe RECEIVING_WALLET
		}

		test("save persists a status transition on an existing Payment") {
			val merchantId = MerchantId(insertTestMerchant())
			val payment = newPayment(merchantId)
			adapter.save(payment)

			payment.ready(NOW.plusSeconds(1))
			adapter.save(payment)

			val found = adapter.findByMerchantOrderId(merchantId, payment.merchantOrderId)
			found.shouldNotBeNull()
			found.status shouldBe PaymentStatus.READY
		}

		test("findByMerchantOrderId returns null when no such order exists") {
			val merchantId = MerchantId(insertTestMerchant())

			adapter.findByMerchantOrderId(merchantId, MerchantOrderId("no-such-order")).shouldBeNull()
		}
	})
