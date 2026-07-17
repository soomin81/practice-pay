package paytech.practice.pay.api.merchant

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 가맹점 관리자용 API 서버(`apps/api-merchant`)다. `MerchantUser`/`MerchantApiKey`
 * 흐름(가맹점 등록·OWNER 생성, 하위 계정 발급, API Key 발급·폐기,
 * `docs/architecture/identity-access-api-key.md`)을 HTTP로 노출하는 진입점이며,
 * 그중 로그인(`POST /merchant/login`)이 `AuthenticateMerchantUserUseCase`로
 * 구현된 첫 흐름이다(`apps:api-admin`의 `AdminApiApplication`과 같은 이유로
 * `scanBasePackages`를 명시한다). 나머지(가맹점 등록, 하위 계정 발급, API Key 등)는
 * 아직 Use Case가 없다.
 */
@SpringBootApplication(
	scanBasePackages = [
		"paytech.practice.pay.api.merchant",
		"paytech.practice.pay.infra.persistence.jooq",
	],
)
class MerchantApiApplication

fun main(args: Array<String>) {
	runApplication<MerchantApiApplication>(*args)
}
