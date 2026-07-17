package paytech.practice.pay.api.admin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * PG 내부 운영자용 관리 API 서버(`apps/api-admin`)다. `InternalUser`/`AccountInvitation`
 * 흐름(SUPER_ADMIN Bootstrap, 내부 운영자 발급·로그인 등, `docs/architecture/identity-access-api-key.md`)을
 * HTTP로 노출할 진입점이지만, 그 흐름의 Use Case가 아직 `modules:application`에 없어서
 * (도메인 Aggregate만 있다 — `backend/CLAUDE.md`) 지금은 부팅 가능한 최소 골격만 갖춘
 * 상태다.
 */
@SpringBootApplication
class AdminApiApplication

fun main(args: Array<String>) {
	runApplication<AdminApiApplication>(*args)
}
