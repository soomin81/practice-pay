package paytech.practice.pay.domain.checkout

/**
 * Hosted Checkout이 결제 진행 후 고객을 이동시키는 Redirect URL을 표현하는
 * Value Object다.
 *
 * DB의 `success_url`/`cancel_url` 컬럼(`VARCHAR(1000)`)과 대응한다. Redirect만으로는
 * 결제 성공을 확정하지 않는다는 규칙(`docs/domain/glossary.md`의 "Payment Complete
 * Page" 정의 참고)은 이 타입이 아니라 애플리케이션 로직의 책임이다 — 여기서는 형식만
 * 검증한다.
 *
 * @property value `http://` 또는 `https://`로 시작하는 URL 문자열.
 */
@JvmInline
value class RedirectUrl(val value: String) {

	init {
		require(value.length <= MAX_LENGTH) { "RedirectUrl은 ${MAX_LENGTH}자를 초과할 수 없습니다: $value" }
		require(value.startsWith("http://") || value.startsWith("https://")) {
			"RedirectUrl은 http:// 또는 https://로 시작해야 합니다: $value"
		}
	}

	companion object {
		/** `success_url`/`cancel_url` 컬럼의 최대 길이(`VARCHAR(1000)`)와 동일하게 맞춘 값이다. */
		private const val MAX_LENGTH = 1000
	}
}
