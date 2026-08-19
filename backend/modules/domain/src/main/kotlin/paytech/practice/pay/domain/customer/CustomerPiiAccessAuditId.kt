package paytech.practice.pay.domain.customer

/**
 * 개인정보 원본 열람 기록 한 건을 식별하는 공개 ID를 표현하는 Value Object다.
 *
 * DB의 `customer_pii_access_audit.customer_pii_access_audit_id` 컬럼(`VARCHAR(50)`,
 * `UNIQUE`)과 대응하며, 내부 전용 PK인 `customer_pii_access_audit_seq`
 * (`BIGINT AUTO_INCREMENT`)와는 별개의 값이다.
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
		/**
		 * 공개 ID 접두어다.
		 *
		 * `SettlementHoldAuditId`와 같은 이유로 VO가 들고 있는다 — 원본 열람은 결제 상세와
		 * 검색 결과 등 **여러 진입점에서 일어날 수 있어서**, 각 Use Case가 문자열을 직접
		 * 적으면 같은 테이블에 접두어가 섞인다.
		 */
		const val PREFIX = "cpa_"

		/** `customer_pii_access_audit.customer_pii_access_audit_id` 컬럼의 최대 길이(`VARCHAR(50)`)와 동일하다. */
		private const val MAX_LENGTH = 50
	}
}
