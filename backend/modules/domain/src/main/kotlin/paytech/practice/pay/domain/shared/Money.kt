package paytech.practice.pay.domain.shared

/**
 * KRW(원화) 금액을 표현하는 Value Object다.
 *
 * DB에는 `BIGINT`로 저장된다(`KRW BIGINT ↔ Money` 타입 매핑, `docs/architecture/persistence-jooq.md` 참고).
 * "금액과 환율에 `FLOAT`/`DOUBLE`을 쓰지 않는다"는 프로젝트 규칙에 따라 정수(`Long`)만 다룬다.
 *
 * @property amount KRW 금액(원 단위 정수). 음수를 허용하지 않는다.
 */
@JvmInline
value class Money(
	val amount: Long,
) : Comparable<Money> {
	init {
		require(amount >= 0) { "Money 금액은 음수일 수 없습니다: $amount" }
	}

	/** 두 금액을 더한 새 [Money]를 반환한다. */
	operator fun plus(other: Money): Money = Money(amount + other.amount)

	/**
	 * 두 금액을 뺀 새 [Money]를 반환한다.
	 *
	 * 뺀 결과가 음수이면 생성자의 검증(`init`)에 의해 [IllegalArgumentException]이 발생한다.
	 */
	operator fun minus(other: Money): Money = Money(amount - other.amount)

	override fun compareTo(other: Money): Int = amount.compareTo(other.amount)

	companion object {
		/** 0원을 나타내는 상수다. */
		val ZERO = Money(0)
	}
}
