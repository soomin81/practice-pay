package paytech.practice.pay.domain.shared

/**
 * 토큰(스테이블코인)의 최소 단위(Minor Unit) 수량을 표현하는 Value Object다.
 *
 * DB에는 `BIGINT`로 저장된다. 예: `72.992701 USDC`는 `72,992,701`로 저장한다
 * (`docs/domain/glossary.md`의 Minor Unit 정의 참고). 소수 자릿수(decimals)는 이
 * 타입이 아니라 별도 필드(예: `Payment.tokenDecimals`)로 관리한다 — 자산마다
 * decimals가 다를 수 있어서 수량 자체와는 분리해서 다룬다.
 *
 * @property amountMinor 최소 단위 정수 수량. 음수를 허용하지 않는다.
 */
@JvmInline
value class TokenAmount(val amountMinor: Long) : Comparable<TokenAmount> {

	init {
		require(amountMinor >= 0) { "TokenAmount는 음수일 수 없습니다: $amountMinor" }
	}

	/** 두 수량을 더한 새 [TokenAmount]를 반환한다. */
	operator fun plus(other: TokenAmount): TokenAmount = TokenAmount(amountMinor + other.amountMinor)

	/**
	 * 두 수량을 뺀 새 [TokenAmount]를 반환한다.
	 *
	 * 뺀 결과가 음수이면 생성자의 검증(`init`)에 의해 [IllegalArgumentException]이 발생한다.
	 */
	operator fun minus(other: TokenAmount): TokenAmount = TokenAmount(amountMinor - other.amountMinor)

	override fun compareTo(other: TokenAmount): Int = amountMinor.compareTo(other.amountMinor)

	companion object {
		/** 0 수량을 나타내는 상수다. */
		val ZERO = TokenAmount(0)
	}
}
