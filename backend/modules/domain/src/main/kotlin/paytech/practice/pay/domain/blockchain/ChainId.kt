package paytech.practice.pay.domain.blockchain

/**
 * EVM Chain ID를 표현하는 Value Object다.
 *
 * DB의 `chain_id` 컬럼(`BIGINT`)과 대응한다. `Payment → SUCCEEDED` 검증에서
 * "Network 및 Chain ID 일치"를 확인할 때 `network_code`와 함께 쓰인다
 * (`docs/domain/state-transitions.md` 참고).
 *
 * @property value Chain ID 정수값. 0보다 커야 한다.
 */
@JvmInline
value class ChainId(val value: Long) {

	init {
		require(value > 0) { "ChainId는 0보다 커야 합니다: $value" }
	}
}
