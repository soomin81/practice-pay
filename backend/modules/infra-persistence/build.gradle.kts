plugins {
	id("practicepay.spring-library")
	id("practicepay.kotest")
}

dependencies {
	implementation(project(":modules:domain"))
	implementation(project(":modules:application"))
	implementation(project(":db-core"))

	implementation("org.jooq:jooq")
	// @Repository/@Component so a future Spring app module can pick these adapters
	// up via component scanning — see backend/CLAUDE.md's Architecture section.
	implementation("org.springframework:spring-context")
	// TransactionManagerAdapter wraps a Spring-managed PlatformTransactionManager.
	implementation("org.springframework:spring-tx")

	// 구매자 정보가 **덮어쓰기로 수정된 사실**만 남긴다(PaymentCustomerRepositoryAdapter) —
	// 그 사실은 설계상 DB 어디에도 남지 않아서(ADR-008: 옛 값을 보관하면 파기가 반쪽이
	// 된다) 로그가 유일한 흔적이다. 파사드만 받고 바인딩은 앱이 갖는다.
	implementation(libs.kotlinLogging.jvm)

	// `testImplementation`은 `implementation`을 상속하므로 위에 이미 선언한
	// project(":modules:domain")/(":modules:application")/(":db-core")를 여기서
	// 다시 선언하지 않는다 — 테스트 코드에서도 그대로 쓸 수 있다.
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-mysql")
	testImplementation("com.mysql:mysql-connector-j")
	// Migrations are applied with the flyway-core Java API directly, not the
	// org.flywaydb.flyway Gradle plugin — that plugin is the one that's broken on
	// Gradle 9.5.1 (see db-core/build.gradle.kts), not the flyway-core library
	// itself. Invoking Flyway.configure()...migrate() programmatically against the
	// Testcontainers instance has nothing to do with the broken Gradle task.
	testImplementation("org.flywaydb:flyway-core")
	testImplementation("org.flywaydb:flyway-mysql")
	// Spring's own transaction infra, used only to prove TransactionManagerAdapter
	// actually rolls back multiple Repository writes together.
	testImplementation("org.springframework:spring-jdbc")
	// Only for org.springframework.boot.jooq.autoconfigure.SpringTransactionProvider
	// in tests — the exact class Spring Boot's own JooqAutoConfiguration uses to
	// make DSLContext transaction-aware, so the test harness wires jOOQ the same
	// way the real app will once it's assembled. Spring Boot 4.x split jOOQ's
	// autoconfiguration out of spring-boot-autoconfigure into this dedicated module.
	testImplementation("org.springframework.boot:spring-boot-jooq")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
