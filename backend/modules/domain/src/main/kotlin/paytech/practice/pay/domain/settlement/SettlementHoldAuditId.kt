package paytech.practice.pay.domain.settlement

/**
 * 정산 보류 이력 한 건을 식별하는 공개 ID를 표현하는 Value Object다.
 *
 * DB의 `settlement_hold_audit.settlement_hold_audit_id` 컬럼(`VARCHAR(50)`, `UNIQUE`)과
 * 대응하며, 내부 전용 PK인 `settlement_hold_audit_seq`(`BIGINT AUTO_INCREMENT`)와는 별개의
 * 값이다.
 *
 * @property value 이력 공개 ID 문자열. 공백일 수 없고 [MAX_LENGTH]자를 넘을 수 없다.
 */
@JvmInline
value class SettlementHoldAuditId(
	val value: String,
) {
	init {
		require(value.isNotBlank()) { "SettlementHoldAuditId는 공백일 수 없습니다." }
		require(value.length <= MAX_LENGTH) {
			"SettlementHoldAuditId는 ${MAX_LENGTH}자를 초과할 수 없습니다: $value"
		}
	}

	companion object {
		/**
		 * 공개 ID 접두어다.
		 *
		 * **다른 애그리게이트와 달리 접두어를 여기 둔다** — 보류·해제·취소 Use Case가 서로
		 * 다른 패키지(`application.payment`/`application.settlement`)에 흩어져 있어서, 각자
		 * 문자열을 적으면 한쪽만 바뀌어도 같은 테이블에 두 가지 접두어가 섞인다.
		 */
		const val PREFIX = "sha_"

		/** `settlement_hold_audit.settlement_hold_audit_id` 컬럼의 최대 길이(`VARCHAR(50)`)와 동일하다. */
		private const val MAX_LENGTH = 50
	}
}
