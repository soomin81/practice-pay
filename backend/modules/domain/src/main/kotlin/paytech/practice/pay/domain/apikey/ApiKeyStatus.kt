package paytech.practice.pay.domain.apikey

/**
 * [MerchantApiKey]의 상태를 표현한다.
 *
 * 정상 폐기: `ACTIVE → REVOKED`
 * 만료: `ACTIVE → EXPIRED`
 *
 * `REVOKED`, `EXPIRED`는 종료 상태이며 재사용하지 않는다 — 새 Key를 발급한다.
 *
 * @see docs/domain/state-transitions.md
 */
enum class ApiKeyStatus {
	ACTIVE,
	REVOKED,
	EXPIRED,
}
