package paytech.practice.pay.application.apikey

import paytech.practice.pay.application.port.outbound.MerchantApiKeyRepository
import paytech.practice.pay.application.port.outbound.MerchantUserRepository
import java.time.Clock

/**
 * "가맹점 API Key 폐기" Use Case다(`docs/architecture/identity-access-api-key.md`의
 * "6.6 발급 권한": "`OWNER`, `ADMIN`은 폐기할 수 있다"). `MerchantApiKey.revoke`는
 * 이전부터 있었다 — 이 Use Case가 그걸 실제로 처음 호출하는 자리다.
 *
 * 발급 권한 확인은 [IssueMerchantApiKeyUseCase]와 완전히 같다(요청자를 다시 읽어
 * `canManageApiKeys()`를 동적으로 확인) — 그 KDoc 참고.
 *
 * **폐기 대상이 요청자와 같은 가맹점 소속인지 반드시 확인한다.** [MerchantApiKeyRepository.findById]는
 * ID만으로 조회하므로 다른 가맹점의 Key도 그대로 돌려준다 — 그래서 이 Use Case가
 * `apiKey.merchantId == issuer.merchantId`를 직접 검사한다. 불일치와 존재하지 않음을
 * 같은 [MerchantApiKeyNotFoundException]으로 가린다(그 예외의 KDoc 참고 — 다른
 * 가맹점의 Key ID 존재 여부를 무차별 대입으로 탐색하지 못하게 한다).
 *
 * **`MerchantApiKey.revoke()`를 부르기 전에 `isUsable()`을 먼저 확인한다.** 이미
 * `REVOKED`/`EXPIRED`인 Key를 그대로 다시 부르면 도메인의 `checkTransition`이
 * `IllegalStateException`을 던지는데, 이 프로젝트는 그런 예외를 raw로 HTTP까지
 * 새게 두지 않는다(`MerchantApiKeyNotActiveException`의 KDoc 참고).
 *
 * 단일 Aggregate만 저장하므로 `TransactionManager`가 필요 없다([IssueMerchantApiKeyUseCase]와 같다).
 */
class RevokeMerchantApiKeyUseCase(
	private val merchantUserRepository: MerchantUserRepository,
	private val merchantApiKeyRepository: MerchantApiKeyRepository,
	private val clock: Clock,
) {
	fun execute(command: RevokeMerchantApiKeyCommand): RevokeMerchantApiKeyResult {
		val revoker =
			checkNotNull(merchantUserRepository.findById(command.revokedByMerchantUserId)) {
				"인증된 세션의 MerchantUser(${command.revokedByMerchantUserId.value})를 찾을 수 없습니다."
			}

		if (!revoker.canManageApiKeys()) {
			throw MerchantUserCannotManageApiKeysException(
				"MerchantUser(${revoker.id.value})는 API Key를 관리할 권한이 없습니다(role=${revoker.role}, status=${revoker.status}).",
			)
		}

		val apiKey =
			merchantApiKeyRepository.findById(command.merchantApiKeyId)
				?: throw MerchantApiKeyNotFoundException("MerchantApiKey(${command.merchantApiKeyId.value})를 찾을 수 없습니다.")
		if (apiKey.merchantId != revoker.merchantId) {
			throw MerchantApiKeyNotFoundException("MerchantApiKey(${command.merchantApiKeyId.value})를 찾을 수 없습니다.")
		}
		if (!apiKey.isUsable()) {
			throw MerchantApiKeyNotActiveException("MerchantApiKey(${apiKey.id.value})는 이미 ${apiKey.status} 상태입니다.")
		}

		val now = clock.instant()
		apiKey.revoke(revoker.id, now)
		merchantApiKeyRepository.save(apiKey)

		return RevokeMerchantApiKeyResult(
			merchantApiKeyId = apiKey.id,
			revokedAt = checkNotNull(apiKey.revokedAt),
		)
	}
}
