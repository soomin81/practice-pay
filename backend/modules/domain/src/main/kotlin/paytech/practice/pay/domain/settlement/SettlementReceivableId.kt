package paytech.practice.pay.domain.settlement

/**
 * 정산 대상을 식별하는 공개(외부 노출용) ID를 표현하는 Value Object다.
 *
 * DB의 `settlement_receivable.settlement_receivable_id` 컬럼(`VARCHAR(50)`,
 * `UNIQUE`)과 대응하며, 내부 전용 PK인 `settlement_receivable_seq`
 * (`BIGINT AUTO_INCREMENT`)와는 별개의 값이다. 실제 멱등성은 이 ID가 아니라
 * `payment_seq`(1:1)로 보장한다(`docs/database/database-design.md`의
 * "주요 Unique" 참고).
 *
 * @property value 정산 대상 공개 ID 문자열. 공백일 수 없고 [MAX_LENGTH]자를 넘을 수 없다.
 */
@JvmInline
value class SettlementReceivableId(
	val value: String,
) {
	init {
		require(value.isNotBlank()) { "SettlementReceivableId는 공백일 수 없습니다." }
		require(value.length <= MAX_LENGTH) {
			"SettlementReceivableId는 ${MAX_LENGTH}자를 초과할 수 없습니다: $value"
		}
	}

	companion object {
		/** `settlement_receivable.settlement_receivable_id` 컬럼의 최대 길이(`VARCHAR(50)`)와 동일하게 맞춘 값이다. */
		private const val MAX_LENGTH = 50
	}
}
