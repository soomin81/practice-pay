package paytech.practice.pay.batch

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * 배치 Job 실행 서버(`apps/batch`)다. 첫 실제 Job은 BlockchainTransaction
 * 감지·Confirm 폴링 Worker(`ConfirmBlockchainTransactionJobConfiguration`,
 * `docs/database/database-design.md`의 "Confirm Worker" 인덱스가 암시하는 그
 * Worker) — `ConfirmBlockchainTransactionUseCase`(`modules:application`)를
 * 주기적으로 호출한다.
 *
 * `apps:api-payment`의 `PaymentApiApplication`과 같은 이유로 `scanBasePackages`를
 * 명시한다 — 이 클래스의 패키지(`paytech.practice.pay.batch`)와
 * `modules:infra-persistence`/`modules:infra-blockchain`의 Adapter 패키지가
 * 형제 관계라 기본 컴포넌트 스캔 범위로는 서로 닿지 않는다.
 */
@EnableScheduling
@SpringBootApplication(
	scanBasePackages = [
		"paytech.practice.pay.batch",
		"paytech.practice.pay.infra.persistence.jooq",
		"paytech.practice.pay.infra.blockchain",
		// modules:infra-support에서 이 앱이 쓰는 Port 구현만 고른다
		// (PaymentApiApplication의 같은 주석 참고).
		"paytech.practice.pay.infra.support.id",
		"paytech.practice.pay.infra.support.exchange",
		"paytech.practice.pay.infra.support.webhook",
	],
)
class BatchApplication

fun main(args: Array<String>) {
	runApplication<BatchApplication>(*args)
}
