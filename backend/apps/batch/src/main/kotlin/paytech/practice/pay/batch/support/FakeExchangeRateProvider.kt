package paytech.practice.pay.batch.support

import org.springframework.stereotype.Component
import paytech.practice.pay.application.port.outbound.ExchangeRateProvider
import paytech.practice.pay.application.port.outbound.MarketRateQuote
import paytech.practice.pay.domain.shared.ExchangeRate
import java.math.BigDecimal
import java.time.Clock

/**
 * [ExchangeRateProvider] Port를 고정 환율로 구현한다 — `apps:api-payment`의
 * `FakeExchangeRateProvider`와 완전히 같은 구현이다.
 *
 * `IdGenerator`→`UuidIdGenerator`가 이미 앱마다 자기 `support` 패키지에 복제돼
 * 있는 것과 같은 기존 관례를 따른다 — 다른 앱이 필요로 하게 되면 그때 공유
 * 위치로 옮길 수 있는, 지금은 이 정도로 충분한 임시 구현이다.
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
