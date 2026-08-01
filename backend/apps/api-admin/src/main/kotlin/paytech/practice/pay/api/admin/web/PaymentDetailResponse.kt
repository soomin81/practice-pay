package paytech.practice.pay.api.admin.web

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * `GET /admin/payments/{paymentId}`의 응답이다(`docs/architecture/admin-console-api.md`).
 *
 * 결제 한 건의 전체 맥락을 단계별로 나눠 담는다 — 흐름이 진행돼야 생기는 부분은 `null`이고,
 * **그 `null` 자체가 "어디까지 갔는지"를 말해준다**(예: `blockchainTransaction`이 `null`이면
 * 고객이 아직 Hash를 제출하지 않았다).
 */
data class PaymentDetailResponse(
	val payment: PaymentDetailPaymentResponse,
	val quote: PaymentDetailQuoteResponse,
	val checkoutSession: PaymentDetailCheckoutSessionResponse,
	val blockchainTransaction: PaymentDetailBlockchainTransactionResponse?,
	val exchangeOrder: PaymentDetailExchangeOrderResponse?,
	val settlementReceivable: PaymentDetailSettlementResponse?,
	val webhookDeliveries: List<PaymentDetailWebhookDeliveryResponse>,
)

/**
 * @property paymentAmount USDC **Minor Unit 정수를 문자열로** 준다(목록과 같은 이유 —
 * 토큰 금액이 JavaScript `Number`의 안전 정수 범위를 넘을 수 있다). KRW는 숫자다.
 */
data class PaymentDetailPaymentResponse(
	val paymentId: String,
	val merchantId: String,
	val merchantName: String,
	val merchantOrderId: String,
	val orderName: String,
	val orderAmount: Long,
	val orderCurrency: String,
	val paymentAsset: String,
	val paymentAmount: String,
	val tokenDecimals: Int,
	val network: String,
	val receivingWallet: String,
	val customerWallet: String?,
	val status: String,
	val failureReason: String?,
	val expiresAt: Instant,
	val paidAt: Instant?,
	val createdAt: Instant,
)

data class PaymentDetailQuoteResponse(
	val marketProviderCode: String,
	val marketRate: BigDecimal,
	val appliedRate: BigDecimal,
	val spreadRate: BigDecimal,
	val quotedAt: Instant,
	val expiresAt: Instant,
)

data class PaymentDetailCheckoutSessionResponse(
	val checkoutSessionId: String,
	val status: String,
	val connectedWallet: String?,
	val expiresAt: Instant,
)

/** 온체인에서 실제로 무슨 일이 있었는지. **결제가 실패해도 이 값은 남는다**(ADR-007). */
data class PaymentDetailBlockchainTransactionResponse(
	val transactionHash: String,
	val status: String,
	val blockNumber: Long?,
	val confirmationCount: Int,
	val requiredConfirmationCount: Int,
	val fromAddress: String?,
	val toAddress: String?,
	val tokenContractAddress: String?,
	/** 실제로 받은 토큰 금액. Minor Unit 문자열이다. */
	val amountMinor: String?,
	val failureCode: String?,
	val submittedAt: Instant,
	val detectedAt: Instant?,
	val confirmedAt: Instant?,
)

data class PaymentDetailExchangeOrderResponse(
	val exchangeOrderId: String,
	val providerCode: String,
	val status: String,
	val executedAmount: String?,
	val averageExecutionRate: BigDecimal?,
	val receivedAmount: Long?,
	val feeAmount: Long?,
	val completedAt: Instant?,
)

data class PaymentDetailSettlementResponse(
	val settlementReceivableId: String,
	val status: String,
	val grossAmount: Long,
	val feeRate: BigDecimal,
	val feeAmount: Long,
	val adjustmentAmount: Long,
	val netAmount: Long,
	val exchangeProfitLossAmount: Long?,
	val eligibleDate: LocalDate,
)

data class PaymentDetailWebhookDeliveryResponse(
	val webhookDeliveryId: String,
	val eventType: String,
	val destinationUrl: String,
	val status: String,
	val attemptCount: Int,
	val lastHttpStatus: Int?,
	val lastErrorMessage: String?,
	val nextRetryAt: Instant?,
	val deliveredAt: Instant?,
	val createdAt: Instant,
)
