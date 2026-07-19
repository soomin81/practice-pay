package paytech.practice.pay.api.merchant.web

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import paytech.practice.pay.api.merchant.security.MerchantUserPrincipal
import paytech.practice.pay.application.apikey.IssueMerchantApiKeyCommand
import paytech.practice.pay.application.apikey.IssueMerchantApiKeyUseCase
import paytech.practice.pay.application.apikey.ListMerchantApiKeysCommand
import paytech.practice.pay.application.apikey.ListMerchantApiKeysUseCase
import paytech.practice.pay.application.apikey.RevokeMerchantApiKeyCommand
import paytech.practice.pay.application.apikey.RevokeMerchantApiKeyUseCase
import paytech.practice.pay.domain.apikey.ApiKeyScope
import paytech.practice.pay.domain.apikey.MerchantApiKeyId

/**
 * 가맹점 API Key 발급·폐기·목록 조회 API(`docs/architecture/identity-access-api-key.md`의
 * "6.6 발급 권한": `OWNER`/`ADMIN`은 셋 다 "가능")를 노출하는 inbound Adapter다.
 *
 * `SecurityConfig`가 `OWNER`/`ADMIN` 역할만 이 경로를 호출할 수 있게 정적으로
 * 걸러내지만, `ACTIVE` 상태 확인까지는 각 Use Case가 요청자의 `MerchantUser`를
 * 다시 읽어서 한다(`IssueMerchantApiKeyUseCase`/`RevokeMerchantApiKeyUseCase`/
 * `ListMerchantApiKeysUseCase`의 KDoc 참고) — `MerchantSubAccountController`와
 * 같은 구조다.
 *
 * **목록 조회(`GET`)는 새 `SecurityConfig` 규칙이 필요 없었다.** `POST /admin/merchants`에
 * `GET`을 더했을 때는 `HttpMethod`로 메서드를 좁혀야 했지만(`api-admin`의
 * `MerchantController`/`SecurityConfig` 참고), 여기서는 `docs/`의 "6.6"이
 * `VIEWER`의 목록 조회 권한을 "제한적 또는 불가"로 모호하게 남겨서 발급/폐기와
 * 같은 `OWNER`/`ADMIN` 전용 게이트를 그대로 쓰기로 판단했다(`ListMerchantApiKeysUseCase`의
 * KDoc 참고) — 그래서 기존 와일드카드 규칙이 `GET`도 이미 덮는다.
 */
@RestController
@RequestMapping("/merchant/api-keys")
class MerchantApiKeyController(
	private val issueMerchantApiKeyUseCase: IssueMerchantApiKeyUseCase,
	private val revokeMerchantApiKeyUseCase: RevokeMerchantApiKeyUseCase,
	private val listMerchantApiKeysUseCase: ListMerchantApiKeysUseCase,
) {
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	fun issue(
		@Valid @RequestBody request: IssueMerchantApiKeyRequest,
		@AuthenticationPrincipal principal: MerchantUserPrincipal,
	): IssueMerchantApiKeyResponse {
		val command =
			IssueMerchantApiKeyCommand(
				keyName = request.keyName,
				scopes = request.scopes.map { ApiKeyScope.valueOf(it) }.toSet(),
				issuedByMerchantUserId = principal.merchantUserId,
			)

		val result = issueMerchantApiKeyUseCase.execute(command)

		return IssueMerchantApiKeyResponse(
			merchantApiKeyId = result.merchantApiKeyId.value,
			keyName = result.keyName,
			environment = result.environment.name,
			keyPrefix = result.keyPrefix.value,
			scopes = result.scopes.map { it.name },
			rawApiKey = result.rawApiKey,
			createdAt = result.createdAt,
		)
	}

	@DeleteMapping("/{merchantApiKeyId}")
	fun revoke(
		@PathVariable merchantApiKeyId: String,
		@AuthenticationPrincipal principal: MerchantUserPrincipal,
	): RevokeMerchantApiKeyResponse {
		val command =
			RevokeMerchantApiKeyCommand(
				merchantApiKeyId = MerchantApiKeyId(merchantApiKeyId),
				revokedByMerchantUserId = principal.merchantUserId,
			)

		val result = revokeMerchantApiKeyUseCase.execute(command)

		return RevokeMerchantApiKeyResponse(
			merchantApiKeyId = result.merchantApiKeyId.value,
			revokedAt = result.revokedAt,
		)
	}

	@GetMapping
	fun list(
		@AuthenticationPrincipal principal: MerchantUserPrincipal,
	): ListMerchantApiKeysResponse {
		val command = ListMerchantApiKeysCommand(queriedByMerchantUserId = principal.merchantUserId)

		val result = listMerchantApiKeysUseCase.execute(command)

		return ListMerchantApiKeysResponse(
			apiKeys =
				result.apiKeys.map { summary ->
					MerchantApiKeySummaryResponse(
						merchantApiKeyId = summary.merchantApiKeyId.value,
						keyName = summary.keyName,
						environment = summary.environment.name,
						keyPrefix = summary.keyPrefix.value,
						scopes = summary.scopes.map { it.name },
						status = summary.status.name,
						createdAt = summary.createdAt,
						lastUsedAt = summary.lastUsedAt,
						revokedAt = summary.revokedAt,
					)
				},
		)
	}
}
