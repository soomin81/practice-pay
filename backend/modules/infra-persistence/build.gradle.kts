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

	testImplementation(project(":modules:domain"))
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
