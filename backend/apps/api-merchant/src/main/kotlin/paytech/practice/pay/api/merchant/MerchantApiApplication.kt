package paytech.practice.pay.api.merchant

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 가맹점 관리자용 API 서버(`apps/api-merchant`)다. `MerchantUser`/`MerchantApiKey`
 * 흐름(가맹점 등록·OWNER 생성, 하위 계정 발급, API Key 발급·폐기,
 * `docs/architecture/identity-access-api-key.md`)을 HTTP로 노출하는 진입점이다
 * (`apps:api-admin`의 `AdminApiApplication`과 같은 이유로 `scanBasePackages`를
 * 명시한다). 로그인(`POST /merchant/login`), 초대 수락, 하위 계정 발급
 * (`POST /merchant/merchant-users`)이 구현돼 있다 — API Key 발급·폐기는 아직 없다.
 */
@SpringBootApplication(
	scanBasePackages = [
		"paytech.practice.pay.api.merchant",
		"paytech.practice.pay.infra.persistence.jooq",
		// modules:infra-support에서 이 앱이 쓰는 Port 구현만 고른다 — 로그인(PasswordEncoder)/
		// 초대(InvitationTokenHasher)는 security 하위 패키지에, 하위 계정 발급이 새로
		// 만드는 ID는 id 하위 패키지에 있다.
		"paytech.practice.pay.infra.support.security",
		"paytech.practice.pay.infra.support.id",
	],
)
class MerchantApiApplication

fun main(args: Array<String>) {
	runApplication<MerchantApiApplication>(*args)
}
