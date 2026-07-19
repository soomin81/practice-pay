package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.blockchain.TransactionHash
import paytech.practice.pay.domain.checkout.CheckoutSessionId
import paytech.practice.pay.domain.checkout.CheckoutSessionStatus
import paytech.practice.pay.domain.payment.PaymentFailureReason
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.ExchangeRate
import paytech.practice.pay.domain.shared.HttpUrl
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.TokenAmount
import paytech.practice.pay.domain.shared.WalletAddress
import java.time.Instant

/**
 * 고객 대면 체크아웃 화면이 필요로 하는 읽기 전용 조회를 담당하는 Port다
 * (`docs/architecture/checkout-api.md`의 4.1/4.2).
 *
 * `CheckoutSessionRepository`(Command Repository)를 쓰지 않고 전용 Projection을
 * 두는 이유는 `MerchantListProjection`이 세운 선례와 같다 — 화면에 필요한 값이
 * `CheckoutSession` 하나에 없고 `Payment`/`PaymentQuote`/최신
 * `BlockchainTransaction`까지 걸쳐 있어서, Aggregate를 각각 복원해 조립하면
 * 쿼리도 늘고 낙관적 잠금 `version` 같은 화면과 무관한 필드까지 끌고 온다.
 *
 * **조회 결과에 가맹점 식별자나 내부 Seq를 담지 않는다** — 고객 브라우저가 보는
 * 응답의 원본이라서, 다른 가맹점·다른 결제를 추론할 단서를 주지 않는다.
 */
interface CheckoutViewProjection {
	/** 체크아웃 화면 전체 렌더용. 세션이 없으면 `null`. */
	fun findSessionView(checkoutSessionId: CheckoutSessionId): CheckoutSessionView?

	/** Confirm 대기 중 폴링용(경량). 세션이 없으면 `null`. */
	fun findStatusView(checkoutSessionId: CheckoutSessionId): CheckoutStatusView?
}

/**
 * `GET /checkout/sessions/{id}` 응답의 원본이다.
 *
 * [paymentAmount]는 Minor Unit이고, 이 값을 JSON으로 내보낼 때는 **문자열로
 * 직렬화한다** — JavaScript `Number`의 안전 정수 범위를 넘을 수 있어서다
 * (`docs/architecture/checkout-api.md`의 4.1). 그 변환은 inbound Adapter의 책임이다.
 */
data class CheckoutSessionView(
	val checkoutSessionId: CheckoutSessionId,
	val checkoutSessionStatus: CheckoutSessionStatus,
	val expiresAt: Instant,
	val successUrl: HttpUrl,
	val cancelUrl: HttpUrl?,
	val connectedWallet: WalletAddress?,
	val orderName: String,
	val orderAmount: Money,
	val paymentId: PaymentId,
	val paymentStatus: PaymentStatus,
	val paymentAsset: Asset,
	val paymentAmount: TokenAmount,
	val tokenDecimals: Int,
	val network: BlockchainNetwork,
	val receivingWallet: WalletAddress,
	val appliedRate: ExchangeRate,
	val quotedAt: Instant,
	val quoteExpiresAt: Instant,
)

/**
 * `GET /checkout/sessions/{id}/status` 응답의 원본이다.
 *
 * [confirmationCount]/[transactionHash]는 아직 `BlockchainTransaction`이 없으면
 * (고객이 Hash를 제출하기 전) `null`/`0`이다.
 */
data class CheckoutStatusView(
	val checkoutSessionStatus: CheckoutSessionStatus,
	val paymentStatus: PaymentStatus,
	val confirmationCount: Int,
	val transactionHash: TransactionHash?,
	val failureReason: PaymentFailureReason?,
	val successUrl: HttpUrl,
	val cancelUrl: HttpUrl?,
)
