package paytech.practice.pay.domain.identity

/**
 * 내부 운영자 로그인 감사 기록을 식별하는 공개 ID를 표현하는 Value Object다.
 *
 * DB의 `internal_login_audit.internal_login_audit_id` 컬럼(`VARCHAR(50)`, `UNIQUE`)과
 * 대응하며, 내부 전용 PK인 `internal_login_audit_seq`(`BIGINT AUTO_INCREMENT`)와는 별개의
 * 값이다.
 *
 * @property value 감사 기록 공개 ID 문자열. 공백일 수 없고 [MAX_LENGTH]자를 넘을 수 없다.
 */
@JvmInline
value class InternalLoginAuditId(
	val value: String,
) {
	init {
		require(value.isNotBlank()) { "InternalLoginAuditId는 공백일 수 없습니다." }
		require(value.length <= MAX_LENGTH) {
			"InternalLoginAuditId는 ${MAX_LENGTH}자를 초과할 수 없습니다: $value"
		}
	}

	companion object {
		/** `internal_login_audit.internal_login_audit_id` 컬럼의 최대 길이(`VARCHAR(50)`)와 동일하다. */
		private const val MAX_LENGTH = 50
	}
}
