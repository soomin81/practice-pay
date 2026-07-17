package paytech.practice.pay.api.payment.support

import org.springframework.stereotype.Component
import paytech.practice.pay.application.port.outbound.ExchangeRateProvider
import paytech.practice.pay.application.port.outbound.MarketRateQuote
import paytech.practice.pay.domain.shared.ExchangeRate
import java.math.BigDecimal
import java.time.Clock

/**
 * [ExchangeRateProvider] Port를 고정 환율로 구현한다.
 *
 * MVP는 실제 시장 환율 조회 없이 Fake Exchange로 결제를 처리한다(`docs/decisions/ADR-004-fake-exchange.md`).
 * 이 구현은 그 Fake Exchange의 시장 환율 부분을 대표한다 — 실거래소 연동이 생기면
 * 이 클래스를 교체하기만 하면 된다([ExchangeRateProvider]를 쓰는 쪽은 바뀌지 않는다).
 */
@Component
class FakeExchangeRateProvider(
	private val clock: Clock,
) : ExchangeRateProvider {
	override fun currentRate(): MarketRateQuote =
		MarketRateQuote(
			providerCode = PROVIDER_CODE,
			rate = FIXED_RATE,
			quotedAt = clock.instant(),
		)

	companion object {
		private const val PROVIDER_CODE = "fake-exchange"

		/** 1 USDC당 KRW 고정 환율. 실제 시장 환율이 아니라 MVP용 임의의 상수다. */
		private val FIXED_RATE = ExchangeRate(BigDecimal("1400.000000000000"))
	}
}
