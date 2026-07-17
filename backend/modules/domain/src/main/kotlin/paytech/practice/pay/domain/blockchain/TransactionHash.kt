package paytech.practice.pay.domain.blockchain

/**
 * 온체인 거래 해시를 표현하는 Value Object다.
 *
 * `0x` 접두사 + 64자리 16진수(총 66자, 32바이트) 형식만 검증한다. DB의
 * `transaction_hash` 컬럼(`VARCHAR(150)`)과 대응하며, `network_code + transaction_hash`
 * 조합으로 중복 거래를 방지하는 데 쓰인다(`docs/domain/glossary.md`의 Transaction Hash
 * 정의, `docs/database/database-design.md`의 "주요 Unique" 참고).
 *
 * @property value `0x` 접두사를 포함한 거래 해시 문자열.
 */
@JvmInline
value class TransactionHash(val value: String) {

	init {
		require(HASH_REGEX.matches(value)) { "TransactionHash 형식이 올바르지 않습니다: $value" }
	}

	companion object {
		private val HASH_REGEX = Regex("^0x[0-9a-fA-F]{64}$")
	}
}
