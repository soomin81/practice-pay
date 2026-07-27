package paytech.practice.pay.domain.identity

/**
 * 가맹점 관리자 로그인 감사 기록을 식별하는 공개 ID를 표현하는 Value Object다
 * ([InternalLoginAuditId]의 가맹점판).
 *
 * DB의 `merchant_login_audit.merchant_login_audit_id` 컬럼(`VARCHAR(50)`, `UNIQUE`)과
 * 대응하며, 내부 전용 PK인 `merchant_login_audit_seq`와는 별개의 값이다.
 *
 * @property value 감사 기록 공개 ID 문자열. 공백일 수 없고 [MAX_LENGTH]자를 넘을 수 없다.
 */
@JvmInline
value class MerchantLoginAuditId(
	val value: String,
) {
	init {
		require(value.isNotBlank()) { "MerchantLoginAuditId는 공백일 수 없습니다." }
		require(value.length <= MAX_LENGTH) {
			"MerchantLoginAuditId는 ${MAX_LENGTH}자를 초과할 수 없습니다: $value"
		}
	}

	companion object {
		/** `merchant_login_audit.merchant_login_audit_id` 컬럼의 최대 길이(`VARCHAR(50)`)와 동일하다. */
		private const val MAX_LENGTH = 50
	}
}
