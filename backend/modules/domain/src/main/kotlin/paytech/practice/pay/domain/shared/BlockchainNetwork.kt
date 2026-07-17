package paytech.practice.pay.domain.shared

/**
 * 블록체인 네트워크를 표현하는 Value Object다.
 *
 * DB의 `network_code` 컬럼(`VARCHAR(50)`)과 대응한다. `Payment.network`와
 * `BlockchainTransaction.network` 모두 이 타입을 쓴다. MVP는 [BASE_SEPOLIA] 하나만
 * 지원하지만, 다중 네트워크 확장(ADR-001에서 후속 단계로 미룸)을 염두에 두고 고정된
 * enum이 아니라 코드 문자열로 모델링한다.
 *
 * @property code 네트워크 코드 문자열. 공백일 수 없다.
 */
@JvmInline
value class BlockchainNetwork(
	val code: String,
) {
	init {
		require(code.isNotBlank()) { "BlockchainNetwork 코드는 공백일 수 없습니다." }
	}

	companion object {
		/** MVP에서 지원하는 유일한 네트워크(`docs/architecture/mvp-scope.md`). */
		val BASE_SEPOLIA = BlockchainNetwork("BASE_SEPOLIA")
	}
}
