package paytech.practice.pay.api.payment.checkout.web

import java.time.Instant

/**
 * `GET /checkout/sessions/{checkoutSessionId}`의 응답 본문이다
 * (`docs/architecture/checkout-api.md`의 4.1).
 *
 * 이 응답은 **인증 없이 고객 브라우저에 나간다** — 가맹점 식별자나 내부 Seq를
 * 절대 담지 않는다.
 */
data class CheckoutSessionResponse(
	val checkoutSessionId: String,
	val checkoutSessionStatus: String,
	val expiresAt: Instant,
	val successUrl: String,
	val cancelUrl: String?,
	val connectedWallet: String?,
	val order: CheckoutOrderResponse,
	val payment: CheckoutPaymentResponse,
	val quote: CheckoutQuoteResponse,
)

data class CheckoutOrderResponse(
	val orderName: String,
	val orderAmount: Long,
	val orderCurrency: String,
)

/**
 * [amount]가 `Long`이 아니라 `String`인 것이 핵심이다 — Minor Unit 금액은 JavaScript
 * `Number`의 안전 정수 범위(2^53-1)를 넘을 수 있어서, 숫자로 직렬화하면 브라우저에서
 * 조용히 정밀도를 잃는다. 이 저장소는 이미 `BigInteger.toLong()`이 값을 잘라
 * `TokenAmount`가 음수가 된 사고를 겪었다(`backend/CLAUDE.md`의 "테스트가 잡지 못하는 층").
 *
 * [chainId]/[tokenContractAddress]/[receivingWallet]/[requiredConfirmationCount]를
 * 응답에 담는 이유는 프론트가 이 값들을 상수로 박아두지 않게 하려는 것이다 — 토큰을
 * Symbol만으로 판단하지 않고 항상 (네트워크, Contract 주소) 조합으로 다룬다는 도메인
 * 규칙을 프론트까지 확장한다.
 */
data class CheckoutPaymentResponse(
	val paymentId: String,
	val paymentStatus: String,
	val asset: String,
	val amount: String,
	val tokenDecimals: Int,
	val network: String,
	val chainId: Long,
	val tokenContractAddress: String,
	val receivingWallet: String,
	val requiredConfirmationCount: Int,
)

/** [appliedRate]는 `BigDecimal`을 문자열로 준다 — 부동소수점 변환으로 정밀도를 잃지 않기 위해서다. */
data class CheckoutQuoteResponse(
	val appliedRate: String,
	val quotedAt: Instant,
	val expiresAt: Instant,
)
