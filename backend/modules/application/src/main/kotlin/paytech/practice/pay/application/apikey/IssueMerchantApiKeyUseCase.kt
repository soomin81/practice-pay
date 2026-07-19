package paytech.practice.pay.application.apikey

import paytech.practice.pay.application.port.outbound.ApiKeySecretHasher
import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.MerchantApiKeyRepository
import paytech.practice.pay.application.port.outbound.MerchantUserRepository
import paytech.practice.pay.domain.apikey.ApiEnvironment
import paytech.practice.pay.domain.apikey.ApiKeyPrefix
import paytech.practice.pay.domain.apikey.ApiKeyScope
import paytech.practice.pay.domain.apikey.MerchantApiKey
import paytech.practice.pay.domain.apikey.MerchantApiKeyId
import java.time.Clock

/**
 * "가맹점 API Key 발급" Use Case다(`docs/architecture/identity-access-api-key.md`의
 * "6.6 발급 권한": "`OWNER`, `ADMIN`은 발급할 수 있다"). `MerchantApiKey.create`는
 * 이전부터 있었다 — 이 Use Case가 그걸 실제로 처음 호출하는 자리다.
 *
 * 발급 권한을 [paytech.practice.pay.application.identity.InviteMerchantSubAccountUseCase]와
 * 같은 방식으로 확인한다 — 정적 역할 검사(`SecurityConfig`)가 아니라
 * [MerchantUserRepository]로 요청자를 다시 읽어 `canManageApiKeys()`를 동적으로
 * 호출한다(그 Use Case의 KDoc에 적은 이유와 같다: `canManageApiKeys()`가 이미
 * 도메인에 존재하는데 호출부가 없었다는 것, 그리고 발급 대상 가맹점도 같은
 * 조회로 함께 얻어야 한다는 것 — 요청 본문으로 `merchantId`를 받으면 멀티테넌시
 * 취약점이 생긴다).
 *
 * 단일 Aggregate만 저장하므로 `TransactionManager`가 필요 없다
 * (`ConnectCheckoutWalletUseCase`와 같은 이유).
 */
class IssueMerchantApiKeyUseCase(
	private val merchantUserRepository: MerchantUserRepository,
	private val merchantApiKeyRepository: MerchantApiKeyRepository,
	private val apiKeySecretHasher: ApiKeySecretHasher,
	private val idGenerator: IdGenerator,
	private val clock: Clock,
) {
	fun execute(command: IssueMerchantApiKeyCommand): IssueMerchantApiKeyResult {
		val issuer =
			checkNotNull(merchantUserRepository.findById(command.issuedByMerchantUserId)) {
				"인증된 세션의 MerchantUser(${command.issuedByMerchantUserId.value})를 찾을 수 없습니다."
			}

		if (!issuer.canManageApiKeys()) {
			throw MerchantUserCannotManageApiKeysException(
				"MerchantUser(${issuer.id.value})는 API Key를 관리할 권한이 없습니다(role=${issuer.role}, status=${issuer.status}).",
			)
		}

		require(command.scopes.isNotEmpty()) { "scopes는 비어 있을 수 없습니다." }
		require(command.scopes.all { it in MVP_SCOPES }) {
			"MVP는 다음 Scope만 발급할 수 있습니다: $MVP_SCOPES (요청: ${command.scopes})"
		}

		val prefixToken = idGenerator.newId().take(PREFIX_TOKEN_LENGTH)
		val secret = idGenerator.newId()
		val rawApiKey = "sk_test_${prefixToken}_$secret"
		val keyPrefix = ApiKeyPrefix("sk_test_$prefixToken")

		val now = clock.instant()

		val apiKey =
			MerchantApiKey.create(
				id = MerchantApiKeyId("mak_" + idGenerator.newId()),
				merchantId = issuer.merchantId,
				keyName = command.keyName,
				environment = ApiEnvironment.TEST,
				keyPrefix = keyPrefix,
				secretHash = apiKeySecretHasher.hash(rawApiKey),
				hashAlgorithm = HASH_ALGORITHM,
				scopes = command.scopes,
				createdByMerchantUserId = issuer.id,
				expiresAt = null,
				createdAt = now,
			)

		merchantApiKeyRepository.save(apiKey)

		return IssueMerchantApiKeyResult(
			merchantApiKeyId = apiKey.id,
			keyName = apiKey.keyName,
			environment = apiKey.environment,
			keyPrefix = apiKey.keyPrefix,
			scopes = apiKey.scopes,
			rawApiKey = rawApiKey,
			createdAt = apiKey.createdAt,
		)
	}

	companion object {
		/** MVP가 발급을 허용하는 Scope(`docs/`의 "6.8 Scope") — 나머지 `ApiKeyScope` 값은 스키마/도메인엔 있지만 아직 쓰지 않는다. */
		private val MVP_SCOPES = setOf(ApiKeyScope.PAYMENT_CREATE, ApiKeyScope.PAYMENT_READ)

		/** `sk_test_<prefixToken>`의 `prefixToken` 길이 — [ApiKeyPrefix]의 KDoc 예시(`sk_test_ab12cd34`)와 같다. */
		private const val PREFIX_TOKEN_LENGTH = 8

		/**
		 * `merchant_api_key.hash_algorithm`에 기록하는 값이다. `HmacApiKeySecretHasher`가
		 * 실제로 쓰는 JCE 알고리즘 이름(`HmacSHA256`)과는 다른 표기이지만, 개발 시드
		 * 데이터(`V4__seed_dev_identity_data.sql`이었던 파일, 지금은 `db/seed/`)가 이미
		 * 이 표기로 심어져 있어 그대로 맞췄다 — `ApiKeySecretHasher` Port에 알고리즘
		 * 이름을 노출하는 메서드가 없어서 Use Case가 상수로 고정한다(MVP 단순화).
		 */
		private const val HASH_ALGORITHM = "HMAC-SHA256"
	}
}
