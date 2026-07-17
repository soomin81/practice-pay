package paytech.practice.pay.domain.shared

/**
 * 부호가 있는 KRW(원화) 금액을 표현하는 Value Object다.
 *
 * [Money]와 달리 음수를 허용한다. 정산의 조정 금액(`adjustment_amount`, 추가 가산
 * 또는 차감 가능)과 거래소 손익(`exchange_profit_loss_amount`, 이익 또는 손실
 * 가능)처럼 부호 자체가 의미를 가지는 금액에 쓴다(`docs/domain/glossary.md`의
 * Adjustment Amount 정의 참고). DB에는 `BIGINT`로 저장된다.
 *
 * @property amount KRW 금액(원 단위 정수). 양수·음수·0 모두 허용한다.
 */
@JvmInline
value class SignedMoney(
	val amount: Long,
) : Comparable<SignedMoney> {
	/** 두 금액을 더한 새 [SignedMoney]를 반환한다. */
	operator fun plus(other: SignedMoney): SignedMoney = SignedMoney(amount + other.amount)

	/** 두 금액을 뺀 새 [SignedMoney]를 반환한다. */
	operator fun minus(other: SignedMoney): SignedMoney = SignedMoney(amount - other.amount)

	override fun compareTo(other: SignedMoney): Int = amount.compareTo(other.amount)

	companion object {
		/** 0원을 나타내는 상수다. */
		val ZERO = SignedMoney(0)
	}
}
