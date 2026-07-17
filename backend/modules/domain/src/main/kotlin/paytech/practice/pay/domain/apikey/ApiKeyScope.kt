package paytech.practice.pay.domain.apikey

/**
 * API Key가 호출할 수 있는 API 범위를 표현한다.
 *
 * MVP는 `PAYMENT_CREATE`, `PAYMENT_READ`만 발급한다. 나머지는 스키마의
 * `merchant_api_key_scope.scope_code` CHECK 제약이 이미 값을 나열해 두고 있어
 * enum에도 포함하지만 MVP에서는 쓰지 않는다
 * (`docs/architecture/identity-access-api-key.md`의 "Scope" 참고).
 */
enum class ApiKeyScope {
	PAYMENT_CREATE,
	PAYMENT_READ,
	REFUND_CREATE,
	REFUND_READ,
	SETTLEMENT_READ,
}
