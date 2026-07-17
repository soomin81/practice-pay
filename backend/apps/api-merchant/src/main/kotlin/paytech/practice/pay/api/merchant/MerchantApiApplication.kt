package paytech.practice.pay.api.merchant

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 가맹점 관리자용 API 서버(`apps/api-merchant`)다. `MerchantUser`/`MerchantApiKey`
 * 흐름(가맹점 등록·OWNER 생성, 하위 계정 발급, API Key 발급·폐기,
 * `docs/architecture/identity-access-api-key.md`)을 HTTP로 노출할 진입점이지만,
 * 그 흐름의 Use Case가 아직 `modules:application`에 없어서(도메인 Aggregate만
 * 있다 — `backend/CLAUDE.md`) 지금은 부팅 가능한 최소 골격만 갖춘 상태다.
 */
@SpringBootApplication
class MerchantApiApplication

fun main(args: Array<String>) {
	runApplication<MerchantApiApplication>(*args)
}
