package paytech.practice.pay.infra.persistence.jooq

import org.flywaydb.core.Flyway
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DataSourceConnectionProvider
import org.jooq.impl.DefaultConfiguration
import org.springframework.boot.jooq.autoconfigure.SpringTransactionProvider
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.transaction.PlatformTransactionManager
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * infra-persistence 통합 테스트가 공유하는 MySQL Testcontainers 인스턴스와, 실제
 * 앱이 조립됐을 때와 동일한 방식(Spring 트랜잭션을 인지하는 DSLContext)으로 구성한
 * jOOQ 설정이다.
 *
 * 컨테이너 하나를 모든 테스트 클래스가 공유한다(Testcontainers의 "싱글턴 컨테이너"
 * 패턴) — 테스트마다 컨테이너를 새로 띄우면 스위트 전체가 매우 느려진다. 명시적으로
 * `stop()`을 부르지 않는다 — Testcontainers의 Ryuk reaper가 JVM 종료 시 정리한다.
 * `org.testcontainers.mysql.MySQLContainer` import는 `backend/src/test/kotlin/.../TestcontainersConfiguration.kt`와
 * 동일한 최신 Testcontainers 모듈 구조를 따른 것이다.
 *
 * Migration은 `org.flywaydb.flyway` Gradle 플러그인이 아니라 `flyway-core` Java
 * API를 직접 호출해서 적용한다 — 이 프로젝트에서 깨진 건 그 Gradle 플러그인이지
 * (`db-core/build.gradle.kts` 참고), `flyway-core` 라이브러리 자체가 아니다.
 */
object PersistenceTestSupport {
	private val container: MySQLContainer =
		MySQLContainer(DockerImageName.parse("mysql:latest"))
			.withDatabaseName("stablecoin_payment")
			.withUsername("root")
			.withPassword("verysecret")
			.also { it.start() }

	private val dataSource: DriverManagerDataSource =
		DriverManagerDataSource().apply {
			setDriverClassName("com.mysql.cj.jdbc.Driver")
			url = container.jdbcUrl
			username = container.username
			password = container.password
		}

	val transactionManager: PlatformTransactionManager = DataSourceTransactionManager(dataSource)

	/**
	 * Spring이 관리하는 트랜잭션에 자동으로 참여하는 DSLContext다 — 실제 앱에서
	 * Spring Boot의 jOOQ 자동 구성이 만드는 Bean과 같은 방식으로 구성했다
	 * ([TransactionManagerAdapter]의 KDoc 참고).
	 */
	val dsl: DSLContext =
		DSL.using(
			DefaultConfiguration()
				.set(SQLDialect.MYSQL)
				.set(DataSourceConnectionProvider(TransactionAwareDataSourceProxy(dataSource)))
				.set(SpringTransactionProvider(transactionManager)),
		)

	init {
		Flyway
			.configure()
			.dataSource(container.jdbcUrl, container.username, container.password)
			.locations("filesystem:../../db-core/src/main/resources/db/migration")
			.load()
			.migrate()
	}
}
