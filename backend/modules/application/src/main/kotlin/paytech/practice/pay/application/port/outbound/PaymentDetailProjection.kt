package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.blockchain.BlockchainTransactionStatus
import paytech.practice.pay.domain.blockchain.TransactionHash
import paytech.practice.pay.domain.checkout.CheckoutSessionStatus
import paytech.practice.pay.domain.exchange.ExchangeOrderStatus
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.PaymentFailureReason
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus
import paytech.practice.pay.domain.settlement.SettlementReceivableStatus
import paytech.practice.pay.domain.webhook.WebhookDeliveryStatus
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * 결제 **한 건의 전체 맥락**을 조회하는 전용 jOOQ Projection Outbound Port다.
 *
 * 목록([PaymentListProjection])이 "무엇이 있나"에 답한다면 이쪽은 **"이 결제가 왜 이 상태이고
 * 돈이 어디 있나"**에 답한다 — ADR-007이 "사람이 `blockchain_transaction`을 조회해서
 * 판단한다"고 전제한 그 조회를 화면으로 만든 것이다.
 *
 * 결제를 중심으로 견적·체크아웃·온체인 거래·환전·정산·Webhook 전송까지 한 번에 모은다.
 */
fun interface PaymentDetailProjection {
	/** 없으면 `null`. 호출부가 404로 옮긴다. */
	fun findByPaymentId(paymentId: PaymentId): PaymentDetailView?
}

/**
 * @property quote 결제 생성 트랜잭션에서 함께 만들어지므로 언제나 있다.
 * @property blockchainTransaction 고객이 Hash를 제출하기 전에는 `null`.
 * @property exchangeOrder 결제가 `SUCCEEDED`가 되어 매도가 일어나기 전에는 `null`.
 * @property settlementReceivable 환전 전에는 `null`.
 * @property webhookDeliveries 이벤트마다 한 건씩 쌓인다(`payment.created`, `payment.succeeded` 등).
 * 가맹점이 Webhook을 설정하지 않았으면 비어 있다.
 */
data class PaymentDetailView(
	val payment: PaymentDetailPayment,
	val quote: PaymentDetailQuote,
	val checkoutSession: PaymentDetailCheckoutSession,
	val blockchainTransaction: PaymentDetailBlockchainTransaction?,
	val exchangeOrder: PaymentDetailExchangeOrder?,
	val settlementReceivable: PaymentDetailSettlement?,
	val webhookDeliveries: List<PaymentDetailWebhookDelivery>,
)

data class PaymentDetailPayment(
	val paymentId: PaymentId,
	val merchantId: MerchantId,
	val merchantName: String,
	val merchantOrderId: MerchantOrderId,
	val orderName: String,
	val orderAmount: Long,
	val orderCurrency: String,
	val paymentAsset: String,
	val paymentAmountMinor: Long,
	val tokenDecimals: Int,
	val network: String,
	val receivingWallet: String,
	/** 고객이 지갑을 연결하기 전에는 `null`. */
	val customerWallet: String?,
	val status: PaymentStatus,
	val failureReason: PaymentFailureReason?,
	val expiresAt: Instant,
	val paidAt: Instant?,
	val createdAt: Instant,
)

data class PaymentDetailQuote(
	val marketProviderCode: String,
	val marketRate: BigDecimal,
	val appliedRate: BigDecimal,
	val spreadRate: BigDecimal,
	val quotedAt: Instant,
	val expiresAt: Instant,
)

data class PaymentDetailCheckoutSession(
	val checkoutSessionId: String,
	val status: CheckoutSessionStatus,
	val connectedWallet: String?,
	val expiresAt: Instant,
)

/**
 * **`Payment`가 실패해도 이 행은 지우지 않는다**(ADR-007) — 자금이 실제로 어디까지 갔는지는
 * 여기에만 남는다. 상세 화면의 존재 이유이기도 하다.
 */
data class PaymentDetailBlockchainTransaction(
	val transactionHash: TransactionHash,
	val status: BlockchainTransactionStatus,
	val blockNumber: Long?,
	val confirmationCount: Int,
	val requiredConfirmationCount: Int,
	val fromAddress: String?,
	val toAddress: String?,
	val tokenContractAddress: String?,
	val amountMinor: Long?,
	val failureCode: String?,
	val submittedAt: Instant,
	val detectedAt: Instant?,
	val confirmedAt: Instant?,
)

data class PaymentDetailExchangeOrder(
	val exchangeOrderId: String,
	val providerCode: String,
	val status: ExchangeOrderStatus,
	val executedAmountMinor: Long?,
	val averageExecutionRate: BigDecimal?,
	val receivedAmount: Long?,
	val feeAmount: Long?,
	val completedAt: Instant?,
)

data class PaymentDetailSettlement(
	val settlementReceivableId: String,
	val status: SettlementReceivableStatus,
	val grossAmount: Long,
	val feeRate: BigDecimal,
	val feeAmount: Long,
	val adjustmentAmount: Long,
	val netAmount: Long,
	val exchangeProfitLossAmount: Long?,
	val eligibleDate: LocalDate,
)

/**
 * @property lastHttpStatus 마지막 시도의 응답 코드. 네트워크 자체가 실패했으면 `null`.
 * @property nextRetryAt `RETRY_WAITING`일 때만 값이 있다.
 */
data class PaymentDetailWebhookDelivery(
	val webhookDeliveryId: String,
	val eventType: String,
	val destinationUrl: String,
	val status: WebhookDeliveryStatus,
	val attemptCount: Int,
	val lastHttpStatus: Int?,
	val lastErrorMessage: String?,
	val nextRetryAt: Instant?,
	val deliveredAt: Instant?,
	val createdAt: Instant,
)
