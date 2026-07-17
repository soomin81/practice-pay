package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.apikey.ApiKeyPrefix
import paytech.practice.pay.domain.apikey.MerchantApiKey

/**
 * [MerchantApiKey] Aggregate를 저장·복원하는 Command Repository Outbound Port다.
 */
interface MerchantApiKeyRepository {
	/** MerchantApiKey를 저장한다(신규 발급·상태 변경·`recordUsage` 모두 이 메서드로 반영한다). */
	fun save(merchantApiKey: MerchantApiKey)

	/**
	 * `key_prefix`로 후보 Key를 찾는다. 없으면 `null`이다.
	 *
	 * API Key 인증의 첫 단계다(`docs/architecture/identity-access-api-key.md`의
	 * "6.4 저장 정책" 권장 흐름) — Prefix로 빠르게 후보를 좁힌 다음, Secret 검증은
	 * 호출부(`AuthenticateApiKeyUseCase`)가 [ApiKeySecretHasher]로 한다.
	 */
	fun findByPrefix(keyPrefix: ApiKeyPrefix): MerchantApiKey?
}
