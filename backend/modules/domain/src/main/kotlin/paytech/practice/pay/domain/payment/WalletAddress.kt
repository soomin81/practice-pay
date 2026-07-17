package paytech.practice.pay.domain.payment

/**
 * EVM 지갑 주소를 표현하는 Value Object다.
 *
 * `0x` 접두사 + 40자리 16진수(총 42자) 형식만 검증한다. EIP-55 checksum 검증은
 * 하지 않는다(스코프 밖). 결제의 수취 지갑(`receiving_wallet_address`)과 고객
 * 지갑(`customer_wallet_address`) 모두 이 타입을 쓴다.
 *
 * @property value `0x` 접두사를 포함한 지갑 주소 문자열.
 */
@JvmInline
value class WalletAddress(val value: String) {

	init {
		require(ADDRESS_REGEX.matches(value)) { "WalletAddress 형식이 올바르지 않습니다: $value" }
	}

	companion object {
		private val ADDRESS_REGEX = Regex("^0x[0-9a-fA-F]{40}$")
	}
}
