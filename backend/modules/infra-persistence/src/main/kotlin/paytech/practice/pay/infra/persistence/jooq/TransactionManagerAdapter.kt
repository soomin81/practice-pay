package paytech.practice.pay.infra.persistence.jooq

import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import paytech.practice.pay.application.port.outbound.TransactionManager

/**
 * Spring이 관리하는 트랜잭션으로 [TransactionManager] Port를 구현한다.
 *
 * Spring Boot의 jOOQ 자동 구성(`spring-boot-starter-jooq`)은 `PlatformTransactionManager`
 * Bean이 있으면 애플리케이션의 `DSLContext` Bean을 `SpringTransactionProvider`로
 * 구성한다 — 그 결과 이 클래스가 시작한 트랜잭션 안에서 실행되는 모든 jOOQ 쿼리(어떤
 * Repository Adapter가 실행했든, 그 Adapter가 주입받은 `DSLContext`가 이 클래스가
 * 받은 것과 같은 Bean이기만 하면)가 자동으로 같은 트랜잭션에 참여한다. 그래서 각
 * Repository Adapter에 트랜잭션 중인 `DSLContext`를 별도로 전달할 필요가 없다.
 */
@Component
class TransactionManagerAdapter(
	platformTransactionManager: PlatformTransactionManager,
) : TransactionManager {
	private val transactionTemplate = TransactionTemplate(platformTransactionManager)

	override fun <T> runInTransaction(block: () -> T): T = transactionTemplate.execute { block() }
}
