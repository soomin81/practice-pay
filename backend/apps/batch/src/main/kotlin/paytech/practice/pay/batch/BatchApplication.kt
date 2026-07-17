package paytech.practice.pay.batch

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 배치 Job 실행 서버(`apps/batch`)다. 향후 `OutboxEvent` 발행 Worker처럼
 * 스케줄로 도는 처리(`docs/architecture/persistence-jooq.md`의 "Async 부수효과는
 * Outbox 패턴을 통한다")를 담을 진입점이지만, 아직 Job이 하나도 없어서 지금은
 * 부팅 가능한 최소 골격만 갖춘 상태다.
 */
@SpringBootApplication
class BatchApplication

fun main(args: Array<String>) {
	runApplication<BatchApplication>(*args)
}
