package paytech.practice.pay.application.payment

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import paytech.practice.pay.application.port.outbound.PaymentDetailCheckoutSession
import paytech.practice.pay.application.port.outbound.PaymentDetailPayment
import paytech.practice.pay.application.port.outbound.PaymentDetailProjection
import paytech.practice.pay.application.port.outbound.PaymentDetailQuote
import paytech.practice.pay.application.port.outbound.PaymentDetailView
import paytech.practice.pay.domain.checkout.CheckoutSessionStatus
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus
import java.math.BigDecimal
import java.time.Instant

private val OWNER_MERCHANT = MerchantId("mrc_owner")
private val OTHER_MERCHANT = MerchantId("mrc_other")
private val PAYMENT_ID = PaymentId("pay_001")
private val NOW: Instant = Instant.parse("2026-08-01T04:00:00Z")

private fun viewOwnedBy(merchantId: MerchantId) =
	PaymentDetailView(
		payment =
			PaymentDetailPayment(
				paymentId = PAYMENT_ID,
				merchantId = merchantId,
				merchantName = "가맹점",
				merchantOrderId = MerchantOrderId("order-001"),
				orderName = "주문",
				orderAmount = 20_000,
				orderCurrency = "KRW",
				paymentAsset = "USDC",
				paymentAmountMinor = 14_357_502,
				tokenDecimals = 6,
				network = "BASE_SEPOLIA",
				receivingWallet = "0x" + "9".repeat(40),
				customerWallet = null,
				status = PaymentStatus.READY,
				failureReason = null,
				expiresAt = NOW,
				paidAt = null,
				createdAt = NOW,
			),
		quote =
			PaymentDetailQuote(
				marketProviderCode = "FAKE",
				marketRate = BigDecimal("1400"),
				appliedRate = BigDecimal("1393"),
				spreadRate = BigDecimal("0.005"),
				quotedAt = NOW,
				expiresAt = NOW,
			),
		checkoutSession =
			PaymentDetailCheckoutSession(
				checkoutSessionId = "cs_001",
				status = CheckoutSessionStatus.CREATED,
				connectedWallet = null,
				expiresAt = NOW,
			),
		blockchainTransaction = null,
		exchangeOrder = null,
		settlementReceivable = null,
		webhookDeliveries = emptyList(),
	)

private fun projectionReturning(view: PaymentDetailView?): PaymentDetailProjection {
	val projection = mockk<PaymentDetailProjection>()
	every { projection.findByPaymentId(PAYMENT_ID) } returns view
	return projection
}

class GetMerchantPaymentDetailUseCaseTest :
	FunSpec({

		test("returns the payment when it belongs to the requesting merchant") {
			val useCase = GetMerchantPaymentDetailUseCase(projectionReturning(viewOwnedBy(OWNER_MERCHANT)))

			val view = useCase.execute(OWNER_MERCHANT, PAYMENT_ID).shouldNotBeNull()

			view.payment.merchantId shouldBe OWNER_MERCHANT
		}

		/**
		 * **이 Use Case가 따로 있는 이유 전체가 이 테스트다.** 단건 조회는 범위를 좁히는
		 * 필터가 없어서, 조회 뒤 소유를 확인하지 않으면 남의 결제를 ID로 찍어 볼 수 있다.
		 */
		test("hides another merchant's payment as if it did not exist") {
			val useCase = GetMerchantPaymentDetailUseCase(projectionReturning(viewOwnedBy(OTHER_MERCHANT)))

			useCase.execute(OWNER_MERCHANT, PAYMENT_ID).shouldBeNull()
		}

		/**
		 * 없는 결제와 남의 결제가 **같은 결과**여야 한다 — 둘을 구분해 돌려주면 식별자를
		 * 훑어 다른 가맹점의 거래 존재 여부를 알아낼 수 있다.
		 */
		test("an unknown payment and another merchant's payment are indistinguishable") {
			val unknown = GetMerchantPaymentDetailUseCase(projectionReturning(null)).execute(OWNER_MERCHANT, PAYMENT_ID)
			val foreign =
				GetMerchantPaymentDetailUseCase(projectionReturning(viewOwnedBy(OTHER_MERCHANT)))
					.execute(OWNER_MERCHANT, PAYMENT_ID)

			unknown shouldBe foreign
		}
	})
