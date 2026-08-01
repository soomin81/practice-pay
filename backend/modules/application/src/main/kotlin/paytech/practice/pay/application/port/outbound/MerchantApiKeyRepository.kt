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
	 * [findByPrefix]와 같지만 **행 잠금을 잡고** 읽는다(`SELECT ... FOR UPDATE`) — 인증이
	 * `recordUsage()`로 Key를 바꿔 다시 저장하므로 그 경로가 쓴다. **반드시 트랜잭션 안에서
	 * 불러야 한다**([PaymentRepository.findByIdForUpdate]의 KDoc 참고).
	 *
	 * 이게 없으면 **폐기가 되돌려질 수 있다**: 인증은 매 요청마다 Key를 저장하는데 그 UPDATE는
	 * 상태 컬럼까지 자기 사본 값으로 쓴다 — 관리자가 폐기하는 사이 in-flight 인증이 저장되면
	 * 상태가 `ACTIVE`로 되돌아간다.
	 */
	fun findByPrefixForUpdate(keyPrefix: ApiKeyPrefix): MerchantApiKey?

	/**
	 * `merchant_api_key_id`로 Key를 찾는다. 없으면 `null`이다.
	 *
	 * `RevokeMerchantApiKeyUseCase`가 폐기 대상을 찾는 데 쓴다 — 이 조회만으로는
	 * 호출자의 가맹점 소속을 확인하지 못하므로, 폐기 대상이 호출자와 같은
	 * 가맹점인지는 Use Case가 반환된 [MerchantApiKey.merchantId]로 직접 확인한다.
	 */
	fun findById(merchantApiKeyId: MerchantApiKeyId): MerchantApiKey?
}
