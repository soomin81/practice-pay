package paytech.practice.pay.api.merchant.web

import paytech.practice.pay.application.port.outbound.PaymentDetailCheckoutSession
import paytech.practice.pay.application.port.outbound.PaymentDetailPayment
import paytech.practice.pay.application.port.outbound.PaymentDetailQuote
import paytech.practice.pay.application.port.outbound.PaymentDetailView
import paytech.practice.pay.domain.checkout.CheckoutSessionStatus
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus
import java.math.BigDecimal
import java.time.Instant

private val FIXTURE_NOW: Instant = Instant.parse("2026-08-01T04:00:00Z")

/**
 * 가맹점 콘솔 상세 테스트가 쓰는 최소 픽스처다. 진행되지 않은 단계는 `null`로 둔다 —
 * 여기서 검증하려는 것은 값의 풍부함이 아니라 **소유 확인과 응답 모양**이다.
 */
internal fun merchantDetailView(merchantId: MerchantId = MerchantId("mrc_001")) =
	PaymentDetailView(
		payment =
			PaymentDetailPayment(
				paymentId = PaymentId("pay_001"),
				merchantId = merchantId,
				merchantName = "테스트 가맹점",
				merchantOrderId = MerchantOrderId("order-001"),
				orderName = "테스트 주문",
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
				expiresAt = FIXTURE_NOW,
				paidAt = null,
				createdAt = FIXTURE_NOW,
			),
		quote =
			PaymentDetailQuote(
				marketProviderCode = "FAKE",
				marketRate = BigDecimal("1400"),
				appliedRate = BigDecimal("1393"),
				spreadRate = BigDecimal("0.005"),
				quotedAt = FIXTURE_NOW,
				expiresAt = FIXTURE_NOW,
			),
		checkoutSession =
			PaymentDetailCheckoutSession(
				checkoutSessionId = "cs_001",
				status = CheckoutSessionStatus.CREATED,
				connectedWallet = null,
				expiresAt = FIXTURE_NOW,
			),
		blockchainTransaction = null,
		exchangeOrder = null,
		settlementReceivable = null,
		webhookDeliveries = emptyList(),
	)
