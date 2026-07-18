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
		// modules:infra-support에서 이 앱이 쓰는 Port 구현만 고른다 — 로그인(PasswordEncoder)과
		// 초대 수락(InvitationTokenHasher) 둘 다 security 하위 패키지에 있다.
		// 이 앱은 아직 ID를 새로 만드는 Use Case가 없어서 infra.support.id는 넣지 않는다.
		"paytech.practice.pay.infra.support.security",
	],
)
class MerchantApiApplication

fun main(args: Array<String>) {
	runApplication<MerchantApiApplication>(*args)
}
