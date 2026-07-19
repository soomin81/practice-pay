package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.apikey.ApiKeyPrefix
import paytech.practice.pay.domain.apikey.MerchantApiKey
import paytech.practice.pay.domain.apikey.MerchantApiKeyId

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

	/**
	 * `merchant_api_key_id`로 Key를 찾는다. 없으면 `null`이다.
	 *
	 * `RevokeMerchantApiKeyUseCase`가 폐기 대상을 찾는 데 쓴다 — 이 조회만으로는
	 * 호출자의 가맹점 소속을 확인하지 못하므로, 폐기 대상이 호출자와 같은
	 * 가맹점인지는 Use Case가 반환된 [MerchantApiKey.merchantId]로 직접 확인한다.
	 */
	fun findById(merchantApiKeyId: MerchantApiKeyId): MerchantApiKey?
}
