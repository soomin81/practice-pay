package paytech.practice.pay.domain.blockchain

/**
 * 토큰 계약(Contract) 주소를 표현하는 Value Object다.
 *
 * `0x` 접두사 + 40자리 16진수(총 42자) 형식만 검증한다. EIP-55 checksum 검증은
 * 하지 않는다(스코프 밖, `WalletAddress`와 동일한 정책). DB의
 * `token_contract_address` 컬럼(`VARCHAR(100)`)과 대응한다.
 *
 * `WalletAddress`와 형식은 같지만 의도적으로 별도 타입으로 둔다 — 지갑 주소와
 * 계약 주소를 타입 레벨에서 섞어 쓰지 못하게 막기 위해서다. "Token Symbol만으로
 * 자산을 판단하지 않는다"는 규칙에 따라 실제 USDC 여부는 Network와 이 Contract
 * 주소의 조합으로 검증한다(`docs/domain/glossary.md`의 Token Contract Address 정의).
 *
 * @property value `0x` 접두사를 포함한 계약 주소 문자열.
 */
@JvmInline
value class ContractAddress(
	val value: String,
) {
	init {
		require(ADDRESS_REGEX.matches(value)) { "ContractAddress 형식이 올바르지 않습니다: $value" }
	}

	companion object {
		private val ADDRESS_REGEX = Regex("^0x[0-9a-fA-F]{40}$")
	}
}
