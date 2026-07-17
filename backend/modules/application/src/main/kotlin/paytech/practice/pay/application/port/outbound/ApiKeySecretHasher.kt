package paytech.practice.pay.application.port.outbound

/**
 * API Key Secret 해시·검증을 위한 Outbound Port다.
 *
 * [PasswordEncoder]와 의도적으로 분리했다 — 사람이 입력하는 비밀번호는 느린
 * 적응형 해시(BCrypt 등)로 검증하지만, API Key는 요청마다 검증하므로 그럴 필요가
 * 없다. `docs/architecture/identity-access-api-key.md`의 "6.4 저장 정책"은
 * "SHA-256 또는 HMAC-SHA-256 계열을 사용할 수 있으며 서버 측 비밀값(Pepper)을
 * 함께 사용한다"고 명시한다 — 어댑터가 정확히 그 방식으로 구현한다.
 */
interface ApiKeySecretHasher {
	/** 전체 API Key 원문(Prefix+Secret)을 저장 가능한 해시로 변환한다. */
	fun hash(rawApiKey: String): String

	/** 전체 API Key 원문이 저장된 해시와 일치하는지 확인한다. */
	fun matches(
		rawApiKey: String,
		hash: String,
	): Boolean
}
