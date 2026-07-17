package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.shared.ExchangeRate
import java.time.Instant

/**
 * 결제 견적 계산에 쓸 시장 환율을 조회하는 Outbound Port다.
 *
 * MVP는 KRW → USDC 한 쌍만 지원하므로(`docs/architecture/mvp-scope.md`) 통화쌍을
 * 파라미터로 받지 않는다 — `Money`가 이미 KRW 전용, [paytech.practice.pay.domain.shared.Asset.USDC]가
 * 결제 자산 전용으로 고정돼 있는 것과 같은 이유다.
 */
fun interface ExchangeRateProvider {
	/** 지금 시점의 시장 환율을 조회한다. */
	fun currentRate(): MarketRateQuote
}

/**
 * [ExchangeRateProvider]가 반환하는 시장 환율 조회 결과다.
 *
 * [PaymentQuote][paytech.practice.pay.domain.quote.PaymentQuote]의
 * `marketProviderCode`/`marketRate`/`quotedAt` 필드에 그대로 매핑된다.
 *
 * @property providerCode 시장 환율을 제공한 프로바이더 코드.
 * @property rate 프로바이더가 제공한 시장 환율.
 * @property quotedAt 이 환율을 조회한 시각.
 */
data class MarketRateQuote(
	val providerCode: String,
	val rate: ExchangeRate,
	val quotedAt: Instant,
)
