package paytech.practice.pay.application.apikey

import paytech.practice.pay.application.port.outbound.MerchantApiKeyListProjection
import paytech.practice.pay.application.port.outbound.MerchantUserRepository

/**
 * "가맹점 API Key 목록 조회" Use Case다(`docs/architecture/identity-access-api-key.md`의
 * "6.6 발급 권한": `OWNER`/`ADMIN`은 "가능", `VIEWER`는 "제한적 또는 불가").
 *
 * **`VIEWER`를 명시적으로 막는다 — `docs/`의 모호한 문구("제한적 또는 불가")에
 * 대한 판단이다.** 가맹점 목록 조회(`ListMerchantsUseCase`)의 `InternalUserRole.VIEWER`는
 * KDoc이 "조회 전용"이라고 명확히 정의해서 전면 허용했지만, 여기서는 문서
 * 자체가 결론을 유보하고 있고, API Key 목록에는 `keyPrefix`/`scopes`/`lastUsedAt`
 * 같은 운영 메타데이터가 담겨 있어 더 보수적으로 판단했다 — `IssueMerchantApiKeyUseCase`/
 * `RevokeMerchantApiKeyUseCase`와 같은 `OWNER`/`ADMIN` 전용 게이트를 그대로
 * 쓴다. 그래서 `SecurityConfig`의 기존 와일드카드 규칙(`/merchant/api-keys` 아래
 * 전체를 `hasAnyRole("OWNER", "ADMIN")`으로 거는 규칙)이 새 경로를 추가하지
 * 않고도 `GET`을 이미 덮는다 — `ListMerchantsUseCase`가 `HttpMethod.POST`로
 * 메서드를 좁혀야 했던 것과 정반대 상황이다.
 *
 * 발급 권한 확인은 [IssueMerchantApiKeyUseCase]/[RevokeMerchantApiKeyUseCase]와
 * 완전히 같은 방식이다(요청자를 다시 읽어 `canManageApiKeys()`를 동적으로 확인,
 * 조회 대상 가맹점도 그 조회에서 함께 얻는다 — 멀티테넌시 방어 이유도 같다).
 *
 * `MerchantApiKeyRepository`(Command Repository)가 아니라 전용 Projection
 * (`MerchantApiKeyListProjection`)을 쓴다 — `ListMerchantsUseCase`가 세운
 * 선례와 같은 이유다.
 */
class ListMerchantApiKeysUseCase(
	private val merchantUserRepository: MerchantUserRepository,
	private val merchantApiKeyListProjection: MerchantApiKeyListProjection,
) {
	fun execute(command: ListMerchantApiKeysCommand): ListMerchantApiKeysResult {
		val querier =
			checkNotNull(merchantUserRepository.findById(command.queriedByMerchantUserId)) {
				"인증된 세션의 MerchantUser(${command.queriedByMerchantUserId.value})를 찾을 수 없습니다."
			}

		if (!querier.canManageApiKeys()) {
			throw MerchantUserCannotManageApiKeysException(
				"MerchantUser(${querier.id.value})는 API Key를 조회할 권한이 없습니다(role=${querier.role}, status=${querier.status}).",
			)
		}

		return ListMerchantApiKeysResult(apiKeys = merchantApiKeyListProjection.findByMerchantId(querier.merchantId))
	}
}
