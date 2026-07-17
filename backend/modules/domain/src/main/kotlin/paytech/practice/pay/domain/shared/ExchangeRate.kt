package paytech.practice.pay.domain.shared

import java.math.BigDecimal

/**
 * 환율을 표현하는 Value Object다.
 *
 * DB에는 `DECIMAL(24,12)`로 저장된다(`docs/architecture/persistence-jooq.md`의
 * `DECIMAL(24,12) ↔ BigDecimal` 타입 매핑 참고). "금액과 환율에 `FLOAT`/`DOUBLE`을
 * 쓰지 않는다"는 프로젝트 규칙에 따라 `BigDecimal`만 다룬다. PaymentQuote의 시장/적용
 * 환율(향후)과 ExchangeOrder의 실제 체결 환율(`average_execution_rate`)이 모두 이
 * 타입을 쓴다 — "결제 적용 환율과 실제 체결 환율은 분리한다"는 규칙에 따라 값은
 * 문맥마다 따로 저장하되 타입만 공유한다(`docs/domain/glossary.md`).
 *
 * @property value 환율 값. 0보다 커야 한다.
 */
@JvmInline
value class ExchangeRate(
	val value: BigDecimal,
) {
	init {
		require(value > BigDecimal.ZERO) { "ExchangeRate는 0보다 커야 합니다: $value" }
	}
}
