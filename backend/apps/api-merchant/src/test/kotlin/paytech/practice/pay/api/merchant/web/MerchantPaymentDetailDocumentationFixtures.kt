package paytech.practice.pay.api.merchant.web

import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import paytech.practice.pay.application.port.outbound.PaymentDetailBlockchainTransaction
import paytech.practice.pay.application.port.outbound.PaymentDetailCheckoutSession
import paytech.practice.pay.application.port.outbound.PaymentDetailExchangeOrder
import paytech.practice.pay.application.port.outbound.PaymentDetailPayment
import paytech.practice.pay.application.port.outbound.PaymentDetailQuote
import paytech.practice.pay.application.port.outbound.PaymentDetailSettlement
import paytech.practice.pay.application.port.outbound.PaymentDetailView
import paytech.practice.pay.application.port.outbound.PaymentDetailWebhookDelivery
import paytech.practice.pay.domain.blockchain.BlockchainTransactionStatus
import paytech.practice.pay.domain.blockchain.TransactionHash
import paytech.practice.pay.domain.checkout.CheckoutSessionStatus
import paytech.practice.pay.domain.exchange.ExchangeOrderStatus
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus
import paytech.practice.pay.domain.settlement.SettlementReceivableStatus
import paytech.practice.pay.domain.webhook.WebhookDeliveryStatus
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * 가맹점 콘솔 결제 상세의 문서화 픽스처·필드 서술이다.
 *
 * **api-admin의 같은 파일과 거의 같다** — 가맹점 열(`payment.merchantId`/`merchantName`)만
 * 없다. 앱은 서로를 모르는 독립 배포 단위라 공유하지 않는다. **한쪽을 고치면 다른 쪽도 함께 본다.**
 */
private val DOC_NOW: Instant = Instant.parse("2026-08-01T04:07:24Z")

/** 모든 단계가 채워진 결제를 쓴다 — REST Docs는 `null` 값의 타입을 추론하지 못한다. */
internal fun merchantPaymentDetailFixture() =
	PaymentDetailView(
		payment =
			PaymentDetailPayment(
				paymentId = PaymentId("pay_3b81"),
				merchantId = MerchantId("mrc_test_001"),
				merchantName = "테스트 가맹점",
				merchantOrderId = MerchantOrderId("order-1001"),
				orderName = "테스트 상품",
				orderAmount = 20_000,
				orderCurrency = "KRW",
				paymentAsset = "USDC",
				paymentAmountMinor = 14_357_502,
				tokenDecimals = 6,
				network = "BASE_SEPOLIA",
				receivingWallet = "0x9dC10cd9f75B98DE43c8B8B40D4c6B4DA5Cab9e1",
				customerWallet = "0xb2d9b0e2298fe19d41883b7490fd430097167f68",
				status = PaymentStatus.SUCCEEDED,
				failureReason = null,
				expiresAt = DOC_NOW,
				paidAt = DOC_NOW,
				createdAt = DOC_NOW,
			),
		quote =
			PaymentDetailQuote(
				marketProviderCode = "FAKE",
				marketRate = BigDecimal("1400.000000000000"),
				appliedRate = BigDecimal("1393.000000000000"),
				spreadRate = BigDecimal("0.005000000000"),
				quotedAt = DOC_NOW,
				expiresAt = DOC_NOW,
			),
		checkoutSession =
			PaymentDetailCheckoutSession(
				checkoutSessionId = "cs_05a3",
				status = CheckoutSessionStatus.PAYMENT_SUBMITTED,
				connectedWallet = "0xb2d9b0e2298fe19d41883b7490fd430097167f68",
				expiresAt = DOC_NOW,
			),
		blockchainTransaction =
			PaymentDetailBlockchainTransaction(
				transactionHash = TransactionHash("0x" + "7f3a".repeat(16)),
				status = BlockchainTransactionStatus.CONFIRMED,
				blockNumber = 44_910_246,
				confirmationCount = 433,
				requiredConfirmationCount = 12,
				fromAddress = "0xb2d9b0e2298fe19d41883b7490fd430097167f68",
				toAddress = "0x9dc10cd9f75b98de43c8b8b40d4c6b4da5cab9e1",
				tokenContractAddress = "0x036CbD53842c5426634e7929541eC2318f3dCF7e",
				amountMinor = 14_357_502,
				failureCode = null,
				submittedAt = DOC_NOW,
				detectedAt = DOC_NOW,
				confirmedAt = DOC_NOW,
			),
		exchangeOrder =
			PaymentDetailExchangeOrder(
				exchangeOrderId = "exo_7c1a",
				providerCode = "FAKE",
				status = ExchangeOrderStatus.COMPLETED,
				executedAmountMinor = 14_357_502,
				averageExecutionRate = BigDecimal("1400.000000000000"),
				receivedAmount = 20_101,
				feeAmount = 0,
				completedAt = DOC_NOW,
			),
		settlementReceivable =
			PaymentDetailSettlement(
				settlementReceivableId = "str_9a1c",
				status = SettlementReceivableStatus.READY,
				grossAmount = 20_000,
				feeRate = BigDecimal("0.01500000"),
				feeAmount = 300,
				adjustmentAmount = 0,
				netAmount = 19_700,
				exchangeProfitLossAmount = 101,
				eligibleDate = LocalDate.parse("2026-08-01"),
			),
		webhookDeliveries =
			listOf(
				PaymentDetailWebhookDelivery(
					webhookDeliveryId = "whd_001",
					eventType = "payment.created",
					destinationUrl = "https://merchant.example.com/webhooks/payments",
					status = WebhookDeliveryStatus.SUCCEEDED,
					attemptCount = 1,
					lastHttpStatus = 200,
					lastErrorMessage = null,
					nextRetryAt = null,
					deliveredAt = DOC_NOW,
					createdAt = DOC_NOW,
				),
			),
	)

/**
 * **부모 객체도 선언해야 생성된 타입이 required가 된다** — 빠뜨리면 화면 코드가 매번 null
 * 검사를 하게 된다(api-admin에서 실제로 겪었다).
 */
internal fun merchantPaymentDetailFields(): List<FieldDescriptor> =
	merchantPaymentFields() + merchantQuoteFields() + merchantCheckoutFields() +
		merchantBlockchainFields() + merchantExchangeFields() + merchantSettlementFields() + merchantWebhookFields()

private fun merchantPaymentFields() =
	listOf(
		fieldWithPath("payment").description("결제 본문. 언제나 있다. **가맹점 열은 없다**(언제나 자기 가맹점이다)."),
		fieldWithPath("payment.paymentId").description("결제 식별자"),
		fieldWithPath("payment.merchantOrderId").description("가맹점이 부여한 주문 식별자"),
		fieldWithPath("payment.orderName").description("주문명"),
		fieldWithPath("payment.orderAmount").description("KRW 주문 금액(원 단위 정수)"),
		fieldWithPath("payment.orderCurrency").description("주문 통화(KRW)"),
		fieldWithPath("payment.paymentAsset").description("결제 자산 코드(USDC)"),
		fieldWithPath("payment.paymentAmount").description("결제 토큰 금액. Minor Unit 정수를 문자열로 준다."),
		fieldWithPath("payment.tokenDecimals").description("토큰 소수 자릿수"),
		fieldWithPath("payment.network").description("블록체인 네트워크 코드"),
		fieldWithPath("payment.receivingWallet").description("PG 수취 지갑 주소"),
		fieldWithPath("payment.customerWallet").type(JsonFieldType.STRING).description("고객 지갑. 연결 전에는 null.").optional(),
		fieldWithPath("payment.status").description("PaymentStatus 값"),
		fieldWithPath("payment.failureReason").type(JsonFieldType.STRING).description("실패 사유. FAILED가 아니면 null.").optional(),
		fieldWithPath("payment.expiresAt").description("결제 만료 시각(UTC)"),
		fieldWithPath("payment.paidAt").type(JsonFieldType.STRING).description("완료 시각(UTC). SUCCEEDED가 아니면 null.").optional(),
		fieldWithPath("payment.createdAt").description("생성 시각(UTC)"),
	)

private fun merchantQuoteFields() =
	listOf(
		fieldWithPath("quote").description("견적 스냅샷. 언제나 있다."),
		fieldWithPath("quote.marketProviderCode").description("시장 환율 제공자 코드"),
		fieldWithPath("quote.marketRate").description("시장 환율"),
		fieldWithPath("quote.appliedRate").description("적용 환율"),
		fieldWithPath("quote.spreadRate").description("스프레드율"),
		fieldWithPath("quote.quotedAt").description("견적 시각(UTC)"),
		fieldWithPath("quote.expiresAt").description("견적 만료 시각(UTC)"),
	)

private fun merchantCheckoutFields() =
	listOf(
		fieldWithPath("checkoutSession").description("체크아웃 세션. 언제나 있다."),
		fieldWithPath("checkoutSession.checkoutSessionId").description("체크아웃 세션 식별자"),
		fieldWithPath("checkoutSession.status").description("CheckoutSessionStatus 값"),
		fieldWithPath("checkoutSession.connectedWallet")
			.type(JsonFieldType.STRING)
			.description("연결된 지갑. 연결 전에는 null.")
			.optional(),
		fieldWithPath("checkoutSession.expiresAt").description("세션 만료 시각(UTC)"),
	)

private fun merchantBlockchainFields() =
	listOf(
		fieldWithPath("blockchainTransaction")
			.type(JsonFieldType.OBJECT)
			.description("온체인 거래. Hash 제출 전에는 null.")
			.optional(),
		fieldWithPath("blockchainTransaction.transactionHash").description("거래 Hash"),
		fieldWithPath("blockchainTransaction.status").description("BlockchainTransactionStatus 값"),
		fieldWithPath("blockchainTransaction.blockNumber").type(JsonFieldType.NUMBER).description("블록 번호").optional(),
		fieldWithPath("blockchainTransaction.confirmationCount").description("누적 Confirm 수"),
		fieldWithPath("blockchainTransaction.requiredConfirmationCount").description("확정에 필요한 Confirm 수"),
		fieldWithPath("blockchainTransaction.fromAddress").type(JsonFieldType.STRING).description("보낸 주소").optional(),
		fieldWithPath("blockchainTransaction.toAddress").type(JsonFieldType.STRING).description("받은 주소").optional(),
		fieldWithPath("blockchainTransaction.tokenContractAddress").type(JsonFieldType.STRING).description("토큰 Contract 주소").optional(),
		fieldWithPath("blockchainTransaction.amountMinor").type(JsonFieldType.STRING).description("실제 수령 금액(Minor Unit 문자열)").optional(),
		fieldWithPath("blockchainTransaction.failureCode").type(JsonFieldType.STRING).description("실패 코드").optional(),
		fieldWithPath("blockchainTransaction.submittedAt").description("제출 시각(UTC)"),
		fieldWithPath("blockchainTransaction.detectedAt").type(JsonFieldType.STRING).description("감지 시각(UTC)").optional(),
		fieldWithPath("blockchainTransaction.confirmedAt").type(JsonFieldType.STRING).description("확정 시각(UTC)").optional(),
	)

private fun merchantExchangeFields() =
	listOf(
		fieldWithPath("exchangeOrder").type(JsonFieldType.OBJECT).description("환전 주문. 매도 전에는 null.").optional(),
		fieldWithPath("exchangeOrder.exchangeOrderId").description("환전 주문 식별자"),
		fieldWithPath("exchangeOrder.providerCode").description("거래소 코드(MVP는 FAKE)"),
		fieldWithPath("exchangeOrder.status").description("ExchangeOrderStatus 값"),
		fieldWithPath("exchangeOrder.executedAmount").type(JsonFieldType.STRING).description("체결 수량(Minor Unit 문자열)").optional(),
		fieldWithPath("exchangeOrder.averageExecutionRate").type(JsonFieldType.NUMBER).description("평균 체결 환율").optional(),
		fieldWithPath("exchangeOrder.receivedAmount").type(JsonFieldType.NUMBER).description("확보한 KRW").optional(),
		fieldWithPath("exchangeOrder.feeAmount").type(JsonFieldType.NUMBER).description("거래소 수수료").optional(),
		fieldWithPath("exchangeOrder.completedAt").type(JsonFieldType.STRING).description("체결 완료 시각(UTC)").optional(),
	)

private fun merchantSettlementFields() =
	listOf(
		fieldWithPath("settlementReceivable").type(JsonFieldType.OBJECT).description("정산 채권. 환전 전에는 null.").optional(),
		fieldWithPath("settlementReceivable.settlementReceivableId").description("정산 채권 식별자"),
		fieldWithPath("settlementReceivable.status").description("SettlementReceivableStatus 값"),
		fieldWithPath("settlementReceivable.grossAmount").description("정산 기준 금액"),
		fieldWithPath("settlementReceivable.feeRate").description("적용 수수료율"),
		fieldWithPath("settlementReceivable.feeAmount").description("수수료"),
		fieldWithPath("settlementReceivable.adjustmentAmount").description("조정 금액(음수 가능)"),
		fieldWithPath("settlementReceivable.netAmount").description("정산 예정 금액"),
		fieldWithPath("settlementReceivable.exchangeProfitLossAmount")
			.type(JsonFieldType.NUMBER)
			.description("환전 손익. 음수 가능.")
			.optional(),
		fieldWithPath("settlementReceivable.eligibleDate").description("정산 예정일"),
	)

private fun merchantWebhookFields() =
	listOf(
		fieldWithPath("webhookDeliveries").description("Webhook 전송 이력(오래된 순). 설정하지 않았으면 빈 배열."),
		fieldWithPath("webhookDeliveries[].webhookDeliveryId").description("전송 식별자"),
		fieldWithPath("webhookDeliveries[].eventType").description("이벤트 종류"),
		fieldWithPath("webhookDeliveries[].destinationUrl").description("전송 대상 URL"),
		fieldWithPath("webhookDeliveries[].status").description("WebhookDeliveryStatus 값"),
		fieldWithPath("webhookDeliveries[].attemptCount").description("시도 횟수"),
		fieldWithPath("webhookDeliveries[].lastHttpStatus").type(JsonFieldType.NUMBER).description("마지막 응답 코드").optional(),
		fieldWithPath("webhookDeliveries[].lastErrorMessage").type(JsonFieldType.STRING).description("마지막 오류 메시지").optional(),
		fieldWithPath("webhookDeliveries[].nextRetryAt").type(JsonFieldType.STRING).description("다음 재시도 시각").optional(),
		fieldWithPath("webhookDeliveries[].deliveredAt").type(JsonFieldType.STRING).description("전송 성공 시각(UTC)").optional(),
		fieldWithPath("webhookDeliveries[].createdAt").description("생성 시각(UTC)"),
	)
