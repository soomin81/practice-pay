package paytech.practice.pay.domain.quote

import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.ExchangeRate
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.TokenAmount
import java.math.BigDecimal
import java.time.Instant

/**
 * 결제 견적(PaymentQuote)이다.
 *
 * KRW 주문 금액을 USDC 수량으로 변환할 때 사용한 시장 환율, 적용 환율, 스프레드,
 * 금액, 유효시간의 불변 스냅샷이다(`docs/domain/glossary.md`). `Payment`에 1:1로
 * 붙는다(`payment_seq` UNIQUE, `docs/database/database-design.md`).
 *
 * 다른 Aggregate(`Payment`, `CheckoutSession` 등)와 달리 상태 전이 메서드도,
 * `private constructor` + `create()`/`reconstitute()` 팩토리도 없다 — 한번
 * 만들어지면 값이 절대 바뀌지 않는 스냅샷이라 "새로 생성"과 "저장된 값 복원"이
 * 완전히 같은 모양이고, 보호해야 할 상태 전이 자체가 없다. DB 스키마에도
 * `updated_at`/`version` 컬럼이 없다(다른 모든 테이블과 다른 점) — 그래서 이
 * 타입도 평범한 `data class`로 두고 생성자를 그대로 공개한다.
 *
 * @property id 결제 견적 공개 ID.
 * @property paymentId 이 견적이 속한 결제. ID로만 참조한다.
 * @property marketProviderCode 시장 환율을 제공한 프로바이더 코드.
 * @property baseAsset 환산 대상 자산(MVP는 USDC).
 * @property marketRate 프로바이더가 제공한 시장 환율.
 * @property appliedRate 이 결제에 실제로 적용한 환율. 실제 체결 환율(`ExchangeOrder.averageExecutionRate`)과는 분리해서 저장한다.
 * @property spreadRate 시장 환율에 적용한 스프레드 비율. 스키마에 부호 제약이 없어 검증하지 않는다.
 * @property orderAmount KRW 주문 금액.
 * @property paymentAmount 환산된 USDC 결제 금액(최소 단위).
 * @property quotedAt 견적을 받은 시각.
 * @property expiresAt 견적 유효 만료 시각. [quotedAt]보다 이후여야 한다.
 * @property createdAt 레코드 생성 시각.
 *
 * @see docs/domain/domain-model.md
 */
data class PaymentQuote(
	val id: PaymentQuoteId,
	val paymentId: PaymentId,
	val marketProviderCode: String,
	val baseAsset: Asset,
	val marketRate: ExchangeRate,
	val appliedRate: ExchangeRate,
	val spreadRate: BigDecimal,
	val orderAmount: Money,
	val paymentAmount: TokenAmount,
	val quotedAt: Instant,
	val expiresAt: Instant,
	val createdAt: Instant,
) {
	init {
		require(marketProviderCode.isNotBlank()) { "marketProviderCode는 공백일 수 없습니다." }
		require(quotedAt.isBefore(expiresAt)) {
			"expiresAt은 quotedAt 이후여야 합니다: quotedAt=$quotedAt, expiresAt=$expiresAt"
		}
	}
}
