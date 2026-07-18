package paytech.practice.pay.api.admin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * PG 내부 운영자용 관리 API 서버(`apps/api-admin`)다. `InternalUser`/`AccountInvitation`
 * 흐름(SUPER_ADMIN Bootstrap, 내부 운영자 발급·로그인 등, `docs/architecture/identity-access-api-key.md`)을
 * HTTP로 노출하는 진입점이며, 그중 로그인(`POST /admin/login`)이 `AuthenticateInternalUserUseCase`로
 * 구현된 첫 흐름이다. 나머지(내부 운영자 발급 등)는 아직 Use Case가 없다.
 *
 * `apps:api-payment`의 `PaymentApiApplication`과 같은 이유로 `scanBasePackages`를
 * 명시한다 — 이 클래스의 패키지(`paytech.practice.pay.api.admin`)와
 * `modules:infra-persistence`의 Adapter 패키지(`paytech.practice.pay.infra.persistence.jooq`)가
 * 형제 관계라 기본 컴포넌트 스캔 범위로는 서로 닿지 않는다.
 */
@SpringBootApplication(
	scanBasePackages = [
		"paytech.practice.pay.api.admin",
		"paytech.practice.pay.infra.persistence.jooq",
		// modules:infra-support에서 이 앱이 쓰는 Port 구현만 고른다
		// (PaymentApiApplication의 같은 주석 참고 — 환율 Provider는 이 앱에 필요 없다).
		"paytech.practice.pay.infra.support.id",
		"paytech.practice.pay.infra.support.security",
	],
)
class AdminApiApplication

fun main(args: Array<String>) {
	runApplication<AdminApiApplication>(*args)
}
