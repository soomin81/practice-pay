package paytech.practice.pay.domain.customer

/**
 * 개인정보 원본 열람 기록 한 건을 식별하는 공개 ID를 표현하는 Value Object다.
 *
 * DB의 `customer_pii_access_audit.customer_pii_access_audit_id` 컬럼(`VARCHAR(50)`,
 * `UNIQUE`)과 대응하며, 내부 전용 PK인 `customer_pii_access_audit_seq`
 * (`BIGINT AUTO_INCREMENT`)와는 별개의 값이다.
 *
 * 접두어 `cpa_`는 ID를 만드는 Use Case가 붙인다 — `SettlementHoldAuditId`처럼 VO에 상수로
 * 두는 것은 그쪽 KDoc이 적어 둔 예외(발급하는 Use Case가 여러 패키지에 흩어져 있다)이고,
 * 여기에는 그 사정이 없다.
 *
 * @property value 이력 공개 ID 문자열. 공백일 수 없고 [MAX_LENGTH]자를 넘을 수 없다.
 */
@JvmInline
value class CustomerPiiAccessAuditId(
	val value: String,
) {
	init {
		require(value.isNotBlank()) { "CustomerPiiAccessAuditId는 공백일 수 없습니다." }
		require(value.length <= MAX_LENGTH) {
			"CustomerPiiAccessAuditId는 ${MAX_LENGTH}자를 초과할 수 없습니다: $value"
		}
	}

	companion object {
		/** `customer_pii_access_audit.customer_pii_access_audit_id` 컬럼의 최대 길이(`VARCHAR(50)`)와 동일하다. */
		private const val MAX_LENGTH = 50
	}
}
