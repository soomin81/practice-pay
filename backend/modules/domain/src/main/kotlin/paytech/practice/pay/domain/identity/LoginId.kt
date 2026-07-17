package paytech.practice.pay.domain.identity

/**
 * 로그인 계정의 아이디를 표현하는 Value Object다.
 *
 * DB의 `internal_user.login_id`/`merchant_user.login_id` 컬럼(`VARCHAR(100)`)과
 * 대응한다. [InternalUser]는 전체 시스템에서, [MerchantUser]는 같은 가맹점
 * 내에서 유일해야 한다(`docs/architecture/identity-access-api-key.md`의
 * "멱등성과 유일성" 참고).
 *
 * @property value 로그인 아이디 문자열. 공백일 수 없고 [MAX_LENGTH]자를 넘을 수 없다.
 */
@JvmInline
value class LoginId(val value: String) {

	init {
		require(value.isNotBlank()) { "LoginId는 공백일 수 없습니다." }
		require(value.length <= MAX_LENGTH) { "LoginId는 ${MAX_LENGTH}자를 초과할 수 없습니다: $value" }
	}

	companion object {
		/** `login_id` 컬럼의 최대 길이(`VARCHAR(100)`)와 동일하게 맞춘 값이다. */
		private const val MAX_LENGTH = 100
	}
}
