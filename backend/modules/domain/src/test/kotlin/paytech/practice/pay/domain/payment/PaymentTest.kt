package paytech.practice.pay.domain.payment

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.TokenAmount
import paytech.practice.pay.domain.shared.WalletAddress
import java.time.Instant
import java.time.temporal.ChronoUnit

private val CREATED_AT: Instant = Instant.parse("2026-07-17T00:00:00Z")
private val EXPIRES_AT: Instant = CREATED_AT.plus(30, ChronoUnit.MINUTES)
private val CUSTOMER_WALLET = WalletAddress("0x" + "a".repeat(40))

private fun newPayment(): Payment =
	Payment.create(
		id = PaymentId("pay_test_001"),
		merchantId = MerchantId("mrc_test_001"),
		merchantOrderId = MerchantOrderId("order-001"),
		orderName = "테스트 주문",
		orderAmount = Money(10_000),
		paymentAsset = Asset.USDC,
		paymentAmount = TokenAmount(7_299_270_1),
		tokenDecimals = 6,
		network = BlockchainNetwork.BASE_SEPOLIA,
		receivingWallet = WalletAddress("0x" + "b".repeat(40)),
		expiresAt = EXPIRES_AT,
		createdAt = CREATED_AT,
	)

class PaymentTest :
	FunSpec({

		test("create starts in CREATED with no customer wallet or paidAt") {
			val payment = newPayment()

			payment.status shouldBe PaymentStatus.CREATED
			payment.customerWallet.shouldBeNull()
			payment.paidAt.shouldBeNull()
			payment.updatedAt shouldBe CREATED_AT
		}

		test("create rejects a blank order name") {
			shouldThrow<IllegalArgumentException> {
				Payment.create(
					id = PaymentId("pay_test_002"),
					merchantId = MerchantId("mrc_test_001"),
					merchantOrderId = MerchantOrderId("order-002"),
					orderName = "   ",
					orderAmount = Money(10_000),
					paymentAsset = Asset.USDC,
					paymentAmount = TokenAmount(1_000_000),
					tokenDecimals = 6,
					network = BlockchainNetwork.BASE_SEPOLIA,
					receivingWallet = WalletAddress("0x" + "b".repeat(40)),
					expiresAt = EXPIRES_AT,
					createdAt = CREATED_AT,
				)
			}
		}

		test("create rejects an expiresAt that is not after createdAt") {
			shouldThrow<IllegalArgumentException> {
				Payment.create(
					id = PaymentId("pay_test_003"),
					merchantId = MerchantId("mrc_test_001"),
					merchantOrderId = MerchantOrderId("order-003"),
					orderName = "테스트 주문",
					orderAmount = Money(10_000),
					paymentAsset = Asset.USDC,
					paymentAmount = TokenAmount(1_000_000),
					tokenDecimals = 6,
					network = BlockchainNetwork.BASE_SEPOLIA,
					receivingWallet = WalletAddress("0x" + "b".repeat(40)),
					expiresAt = CREATED_AT,
					createdAt = CREATED_AT,
				)
			}
		}

		test("ready moves CREATED to READY") {
			val payment = newPayment()
			val changedAt = CREATED_AT.plusSeconds(1)

			payment.ready(changedAt)

			payment.status shouldBe PaymentStatus.READY
			payment.updatedAt shouldBe changedAt
		}

		test("ready fails when not CREATED") {
			val payment = newPayment()
			payment.ready(CREATED_AT.plusSeconds(1))

			shouldThrow<IllegalStateException> { payment.ready(CREATED_AT.plusSeconds(2)) }
		}

		test("submit moves READY to PROCESSING and records the customer wallet") {
			val payment = newPayment()
			payment.ready(CREATED_AT.plusSeconds(1))
			val submittedAt = CREATED_AT.plusSeconds(2)

			payment.submit(CUSTOMER_WALLET, submittedAt)

			payment.status shouldBe PaymentStatus.PROCESSING
			payment.customerWallet shouldBe CUSTOMER_WALLET
			payment.updatedAt shouldBe submittedAt
		}

		test("submit fails when not READY") {
			val payment = newPayment()

			shouldThrow<IllegalStateException> { payment.submit(CUSTOMER_WALLET, CREATED_AT.plusSeconds(1)) }
		}

		test("startConfirmation moves PROCESSING to CONFIRMING") {
			val payment = newPayment()
			payment.ready(CREATED_AT.plusSeconds(1))
			payment.submit(CUSTOMER_WALLET, CREATED_AT.plusSeconds(2))
			val changedAt = CREATED_AT.plusSeconds(3)

			payment.startConfirmation(changedAt)

			payment.status shouldBe PaymentStatus.CONFIRMING
			payment.updatedAt shouldBe changedAt
		}

		test("succeed moves CONFIRMING to SUCCEEDED and records paidAt") {
			val payment = newPayment()
			payment.ready(CREATED_AT.plusSeconds(1))
			payment.submit(CUSTOMER_WALLET, CREATED_AT.plusSeconds(2))
			payment.startConfirmation(CREATED_AT.plusSeconds(3))
			val paidAt = CREATED_AT.plusSeconds(4)

			payment.succeed(paidAt)

			payment.status shouldBe PaymentStatus.SUCCEEDED
			payment.paidAt shouldBe paidAt
			payment.updatedAt shouldBe paidAt
		}

		test("succeed fails when not CONFIRMING") {
			val payment = newPayment()

			shouldThrow<IllegalStateException> { payment.succeed(CREATED_AT.plusSeconds(1)) }
		}

		test("expire moves CREATED to EXPIRED") {
			val payment = newPayment()
			val expiredAt = CREATED_AT.plusSeconds(1)

			payment.expire(expiredAt)

			payment.status shouldBe PaymentStatus.EXPIRED
			payment.updatedAt shouldBe expiredAt
		}

		test("expire moves READY to EXPIRED") {
			val payment = newPayment()
			payment.ready(CREATED_AT.plusSeconds(1))

			payment.expire(CREATED_AT.plusSeconds(2))

			payment.status shouldBe PaymentStatus.EXPIRED
		}

		test("expire fails once PROCESSING") {
			val payment = newPayment()
			payment.ready(CREATED_AT.plusSeconds(1))
			payment.submit(CUSTOMER_WALLET, CREATED_AT.plusSeconds(2))

			shouldThrow<IllegalStateException> { payment.expire(CREATED_AT.plusSeconds(3)) }
		}

		test("fail moves PROCESSING to FAILED with a reason") {
			val payment = newPayment()
			payment.ready(CREATED_AT.plusSeconds(1))
			payment.submit(CUSTOMER_WALLET, CREATED_AT.plusSeconds(2))
			val failedAt = CREATED_AT.plusSeconds(3)

			payment.fail(PaymentFailureReason.TRANSACTION_RECEIPT_FAILED, failedAt)

			payment.status shouldBe PaymentStatus.FAILED
			payment.failureReason shouldBe PaymentFailureReason.TRANSACTION_RECEIPT_FAILED
			payment.updatedAt shouldBe failedAt
		}

		test("fail moves CONFIRMING to FAILED with a reason") {
			val payment = newPayment()
			payment.ready(CREATED_AT.plusSeconds(1))
			payment.submit(CUSTOMER_WALLET, CREATED_AT.plusSeconds(2))
			payment.startConfirmation(CREATED_AT.plusSeconds(3))

			payment.fail(PaymentFailureReason.DUPLICATE_TRANSACTION_HASH, CREATED_AT.plusSeconds(4))

			payment.status shouldBe PaymentStatus.FAILED
			payment.failureReason shouldBe PaymentFailureReason.DUPLICATE_TRANSACTION_HASH
		}

		test("fail fails when still CREATED") {
			val payment = newPayment()

			shouldThrow<IllegalStateException> {
				payment.fail(PaymentFailureReason.NETWORK_MISMATCH, CREATED_AT.plusSeconds(1))
			}
		}

		test("reconstitute rejects SUCCEEDED without paidAt") {
			shouldThrow<IllegalArgumentException> {
				Payment.reconstitute(
					id = PaymentId("pay_test_004"),
					merchantId = MerchantId("mrc_test_001"),
					merchantOrderId = MerchantOrderId("order-004"),
					orderName = "테스트 주문",
					orderAmount = Money(10_000),
					paymentAsset = Asset.USDC,
					paymentAmount = TokenAmount(1_000_000),
					tokenDecimals = 6,
					network = BlockchainNetwork.BASE_SEPOLIA,
					receivingWallet = WalletAddress("0x" + "b".repeat(40)),
					expiresAt = EXPIRES_AT,
					createdAt = CREATED_AT,
					customerWallet = CUSTOMER_WALLET,
					status = PaymentStatus.SUCCEEDED,
					failureReason = null,
					failureMessage = null,
					paidAt = null,
					updatedAt = CREATED_AT,
				)
			}
		}

		test("reconstitute restores a SUCCEEDED payment faithfully") {
			val paidAt = CREATED_AT.plusSeconds(10)

			val payment =
				Payment.reconstitute(
					id = PaymentId("pay_test_005"),
					merchantId = MerchantId("mrc_test_001"),
					merchantOrderId = MerchantOrderId("order-005"),
					orderName = "테스트 주문",
					orderAmount = Money(10_000),
					paymentAsset = Asset.USDC,
					paymentAmount = TokenAmount(1_000_000),
					tokenDecimals = 6,
					network = BlockchainNetwork.BASE_SEPOLIA,
					receivingWallet = WalletAddress("0x" + "b".repeat(40)),
					expiresAt = EXPIRES_AT,
					createdAt = CREATED_AT,
					customerWallet = CUSTOMER_WALLET,
					status = PaymentStatus.SUCCEEDED,
					failureReason = null,
					failureMessage = null,
					paidAt = paidAt,
					updatedAt = paidAt,
				)

			payment.status shouldBe PaymentStatus.SUCCEEDED
			payment.paidAt shouldBe paidAt
			payment.customerWallet shouldBe CUSTOMER_WALLET
		}
	})
