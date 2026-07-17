package paytech.practice.pay.domain.blockchain

/**
 * 블록체인 거래를 식별하는 공개(외부 노출용) ID를 표현하는 Value Object다.
 *
 * DB의 `blockchain_transaction.blockchain_transaction_id` 컬럼(`VARCHAR(50)`,
 * `UNIQUE`)과 대응하며, 내부 전용 PK인 `blockchain_transaction_seq`
 * (`BIGINT AUTO_INCREMENT`)와는 별개의 값이다. 실제 중복 방지는 이 ID가 아니라
 * `network_code + transaction_hash` 조합으로 한다(`docs/domain/glossary.md`의
 * Transaction Hash 정의 참고).
 *
 * @property value 블록체인 거래 공개 ID 문자열. 공백일 수 없고 [MAX_LENGTH]자를 넘을 수 없다.
 */
@JvmInline
value class BlockchainTransactionId(
	val value: String,
) {
	init {
		require(value.isNotBlank()) { "BlockchainTransactionId는 공백일 수 없습니다." }
		require(value.length <= MAX_LENGTH) {
			"BlockchainTransactionId는 ${MAX_LENGTH}자를 초과할 수 없습니다: $value"
		}
	}

	companion object {
		/** `blockchain_transaction.blockchain_transaction_id` 컬럼의 최대 길이(`VARCHAR(50)`)와 동일하게 맞춘 값이다. */
		private const val MAX_LENGTH = 50
	}
}
