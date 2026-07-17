package paytech.practice.pay.application.apikey

/**
 * [AuthenticateApiKeyUseCase]의 입력이다.
 *
 * @property rawApiKey `Authorization: Bearer <rawApiKey>` 헤더에서 `Bearer ` 접두어를
 * 뗀 전체 API Key 원문이다(`sk_test_<prefixToken>_<secret>` 형식,
 * `docs/architecture/identity-access-api-key.md`의 "6.3 인증 방식"). 이 계층을
 * 넘어가면 안 되고 저장되거나 로그에 남지 않는다 — `AuthenticateInternalUserCommand.password`와
 * 같은 이유([paytech.practice.pay.application.identity.AuthenticateInternalUserCommand] 참고).
 */
data class AuthenticateApiKeyCommand(
	val rawApiKey: String,
)
