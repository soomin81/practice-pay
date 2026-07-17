package paytech.practice.pay.application.apikey

import paytech.practice.pay.application.port.outbound.ApiKeySecretHasher
import paytech.practice.pay.application.port.outbound.MerchantApiKeyRepository
import paytech.practice.pay.application.port.outbound.MerchantRepository
import paytech.practice.pay.domain.apikey.ApiEnvironment
import paytech.practice.pay.domain.apikey.ApiKeyPrefix
import java.time.Clock

/**
 * "가맹점 API Key 인증" Use Case다(`docs/architecture/identity-access-api-key.md`의
 * "6.4 저장 정책" 권장 흐름을 그대로 구현한다):
 *
 * ```text
 * 요청 API Key 수신 → Prefix 추출 → Prefix로 후보 Key 조회
 * → 전체 Key를 서버 측 Pepper와 함께 해시 → secret_hash 비교
 * → 상태·환경·Merchant 상태 확인 → last_used_at 갱신
 * ```
 *
 * 로그인 Use Case들([paytech.practice.pay.application.identity.AuthenticateInternalUserUseCase]/
 * [paytech.practice.pay.application.identity.AuthenticateMerchantUserUseCase])과
 * 세션을 다루지 않는다는 점은 같지만, 세 가지가 다르다:
 * - 실패 잠금이 없다 — API Key는 사람이 타이핑하는 비밀번호가 아니라서 무차별
 *   대입 잠금 정책이 적용되지 않는다(`docs/`에 그런 정책이 없다).
 * - 매 요청마다(로그인처럼 한 번이 아니라) 실행된다 — `apps:api-payment`의
 *   `ApiKeyAuthenticationFilter`가 보호된 요청마다 호출한다.
 * - `MerchantApiKey.environment`가 `TEST`인지도 확인한다 — MVP는 Base
 *   Sepolia(Testnet)만 지원해서(`docs/architecture/mvp-scope.md`) `LIVE` Key는
 *   아직 발급도, 사용도 되지 않는다.
 *
 * Merchant 상태 확인(`merchant.canAcceptPayments()`)은 [paytech.practice.pay.application.payment.CreatePaymentUseCase]도
 * 자신의 이유로 다시 한다 — 이건 의도된 중복이다(Key가 원천적으로 무효면
 * 인증 단계에서부터 막고, Use Case는 Use Case대로 자기 비즈니스 규칙을 지킨다).
 */
class AuthenticateApiKeyUseCase(
	private val merchantApiKeyRepository: MerchantApiKeyRepository,
	private val merchantRepository: MerchantRepository,
	private val apiKeySecretHasher: ApiKeySecretHasher,
	private val clock: Clock,
) {
	fun execute(command: AuthenticateApiKeyCommand): AuthenticateApiKeyResult {
		val prefix = extractPrefix(command.rawApiKey) ?: throw InvalidApiKeyException()
		val apiKey =
			merchantApiKeyRepository.findByPrefix(prefix)
				?: throw InvalidApiKeyException()

		if (!apiKeySecretHasher.matches(command.rawApiKey, apiKey.secretHash)) {
			throw InvalidApiKeyException()
		}

		val now = clock.instant()

		if (!apiKey.isUsable()) {
			throw InvalidApiKeyException()
		}
		val expiresAt = apiKey.expiresAt
		if (expiresAt != null && !now.isBefore(expiresAt)) {
			throw InvalidApiKeyException()
		}
		if (apiKey.environment != ApiEnvironment.TEST) {
			throw InvalidApiKeyException()
		}

		val merchant =
			merchantRepository.findById(apiKey.merchantId)
				?: throw InvalidApiKeyException()
		if (!merchant.canAcceptPayments()) {
			throw InvalidApiKeyException()
		}

		apiKey.recordUsage(now)
		merchantApiKeyRepository.save(apiKey)

		return AuthenticateApiKeyResult(
			merchantId = apiKey.merchantId,
			merchantApiKeyId = apiKey.id,
			scopes = apiKey.scopes,
		)
	}

	/**
	 * `sk_test_<prefixToken>_<secret>`에서 `sk_test_<prefixToken>` 부분만 뗀다.
	 * `secret`이 `_`를 포함할 수 있으므로 앞 3개 세그먼트만 자르고 나머지는
	 * 통째로 남긴다(`split(limit = 4)`).
	 */
	private fun extractPrefix(rawApiKey: String): ApiKeyPrefix? {
		val parts = rawApiKey.split("_", limit = 4)
		if (parts.size != 4 || parts.any { it.isBlank() }) return null
		return runCatching { ApiKeyPrefix("${parts[0]}_${parts[1]}_${parts[2]}") }.getOrNull()
	}
}
