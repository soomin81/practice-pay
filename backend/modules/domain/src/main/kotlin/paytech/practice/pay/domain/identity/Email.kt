package paytech.practice.pay.domain.identity

/**
 * 계정의 이메일 주소를 표현하는 Value Object다.
 *
 * DB의 `internal_user.email`/`merchant_user.email` 컬럼(`VARCHAR(320)`, RFC 5321의
 * 최대 이메일 길이)과 대응한다. 형식 검증은 `@`를 포함하는지만 확인하는 최소한의
 * 수준으로 둔다 — 전체 RFC 5322 형식을 검증하는 정규식은 복잡하고 깨지기 쉬워서
 * 스코프 밖으로 뒀다. 실제 도달 가능성 확인(이메일 인증 발송 등)은 애플리케이션
 * 계층의 책임이다.
 *
 * @property value 이메일 주소 문자열.
 */
@JvmInline
value class Email(val value: String) {

	init {
		require(value.length <= MAX_LENGTH) { "Email은 ${MAX_LENGTH}자를 초과할 수 없습니다: $value" }
		require(value.isNotBlank() && value.contains("@")) { "Email 형식이 올바르지 않습니다: $value" }
	}

	companion object {
		/** `email` 컬럼의 최대 길이(`VARCHAR(320)`)와 동일하게 맞춘 값이다. */
		private const val MAX_LENGTH = 320
	}
}
