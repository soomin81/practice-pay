package paytech.practice.pay.application.checkout

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import paytech.practice.pay.application.port.outbound.CheckoutSessionView
import paytech.practice.pay.application.port.outbound.CheckoutStatusView
import paytech.practice.pay.application.port.outbound.CheckoutViewProjection
import paytech.practice.pay.domain.checkout.CheckoutSessionId
import paytech.practice.pay.domain.checkout.CheckoutSessionStatus
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.ExchangeRate
import paytech.practice.pay.domain.shared.HttpUrl
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.TokenAmount
import paytech.practice.pay.domain.shared.WalletAddress
import java.math.BigDecimal
import java.time.Instant

private val NOW: Instant = Instant.parse("2026-07-19T10:00:00Z")
private val SESSION_ID = CheckoutSessionId("cs_test_001")

private fun view(status: CheckoutSessionStatus): CheckoutSessionView =
	CheckoutSessionView(
		checkoutSessionId = SESSION_ID,
		checkoutSessionStatus = status,
		expiresAt = NOW.plusSeconds(1_800),
		successUrl = HttpUrl("https://merchant.example.com/done"),
		cancelUrl = null,
		connectedWallet = null,
		orderName = "테스트 상품",
		orderAmount = Money(50_000),
		paymentId = PaymentId("pay_test_001"),
		paymentStatus = PaymentStatus.READY,
		paymentAsset = Asset.USDC,
		paymentAmount = TokenAmount(72_992_701),
		tokenDecimals = 6,
		network = BlockchainNetwork.BASE_SEPOLIA,
		receivingWallet = WalletAddress("0x" + "a".repeat(40)),
		appliedRate = ExchangeRate(BigDecimal("1370.250000000000")),
		quotedAt = NOW,
		quoteExpiresAt = NOW.plusSeconds(1_800),
	)

class GetCheckoutSessionUseCaseTest :
	FunSpec({

		test("returns the projection view as-is") {
			val projection = mockk<CheckoutViewProjection>()
			every { projection.findSessionView(SESSION_ID) } returns view(CheckoutSessionStatus.CREATED)

			GetCheckoutSessionUseCase(projection).execute(SESSION_ID).checkoutSessionId shouldBe SESSION_ID
		}

		test("an unknown session throws CheckoutSessionNotFoundException") {
			val projection = mockk<CheckoutViewProjection>()
			every { projection.findSessionView(SESSION_ID) } returns null

			shouldThrow<CheckoutSessionNotFoundException> { GetCheckoutSessionUseCase(projection).execute(SESSION_ID) }
		}

		test("terminal-state sessions are still returned so the frontend can render the end screen") {
			// 만료·취소·완료를 404나 410으로 막으면 프론트가 "무슨 일이 있었는지" 화면을
			// 그릴 수 없다 — 조회는 열어두고 변경 엔드포인트만 막는다는 계약 그대로다.
			val projection = mockk<CheckoutViewProjection>()

			listOf(
				CheckoutSessionStatus.COMPLETED,
				CheckoutSessionStatus.EXPIRED,
				CheckoutSessionStatus.CANCELLED,
			).forEach { status ->
				every { projection.findSessionView(SESSION_ID) } returns view(status)

				GetCheckoutSessionUseCase(projection).execute(SESSION_ID).checkoutSessionStatus shouldBe status
			}
		}
	})

class GetCheckoutStatusUseCaseTest :
	FunSpec({

		test("returns the projection status view as-is") {
			val projection = mockk<CheckoutViewProjection>()
			every { projection.findStatusView(SESSION_ID) } returns
				CheckoutStatusView(
					checkoutSessionStatus = CheckoutSessionStatus.PAYMENT_SUBMITTED,
					paymentStatus = PaymentStatus.CONFIRMING,
					confirmationCount = 7,
					transactionHash = null,
					failureReason = null,
					successUrl = HttpUrl("https://merchant.example.com/done"),
					cancelUrl = null,
				)

			GetCheckoutStatusUseCase(projection).execute(SESSION_ID).confirmationCount shouldBe 7
		}

		test("an unknown session throws CheckoutSessionNotFoundException") {
			val projection = mockk<CheckoutViewProjection>()
			every { projection.findStatusView(SESSION_ID) } returns null

			shouldThrow<CheckoutSessionNotFoundException> { GetCheckoutStatusUseCase(projection).execute(SESSION_ID) }
		}
	})
