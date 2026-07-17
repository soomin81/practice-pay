package paytech.practice.pay.domain.shared

/**
 * `http://` 또는 `https://`로 시작하는 URL을 표현하는 Value Object다.
 *
 * DB의 `success_url`/`cancel_url`(CheckoutSession), `destination_url`
 * (WebhookDelivery), `webhook_url`(Merchant, 향후) 같은 `VARCHAR(1000)` 컬럼과
 * 대응한다 — 이 URL로 무엇을 하는지(고객 브라우저 Redirect인지, 서버 간 Webhook
 * 전송인지)는 각 Aggregate의 문맥이 결정하고, 이 타입은 형식만 검증한다. Redirect만
 * 으로는 결제 성공을 확정하지 않는다는 규칙(`docs/domain/glossary.md`의 "Payment
 * Complete Page" 정의 참고)도 이 타입이 아니라 애플리케이션 로직의 책임이다.
 *
 * @property value `http://` 또는 `https://`로 시작하는 URL 문자열.
 */
@JvmInline
value class HttpUrl(
	val value: String,
) {
	init {
		require(value.length <= MAX_LENGTH) { "HttpUrl은 ${MAX_LENGTH}자를 초과할 수 없습니다: $value" }
		require(value.startsWith("http://") || value.startsWith("https://")) {
			"HttpUrl은 http:// 또는 https://로 시작해야 합니다: $value"
		}
	}

	companion object {
		/** 관련 컬럼(`success_url`/`cancel_url`/`destination_url`)의 최대 길이(`VARCHAR(1000)`)와 동일하게 맞춘 값이다. */
		private const val MAX_LENGTH = 1000
	}
}
