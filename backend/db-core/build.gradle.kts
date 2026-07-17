// NOTE: the official `org.flywaydb.flyway` Gradle plugin (latest published: 11.8.2)
// is not used here — its task execution still calls the removed Gradle API
// `JavaPluginConvention` and fails on this project's Gradle 9.5.1
// (https://github.com/flyway/flyway/issues/3798, unresolved upstream as of this
// writing). Migrations under src/main/resources/db/migration are still plain
// Flyway-format SQL files; they're applied manually to the local dev DB for now
// (see backend/CLAUDE.md), and will be picked up automatically by Spring Boot's
// own Flyway autoconfiguration once the app module has a DataSource — that path
// doesn't go through this Gradle plugin at all.
plugins {
	kotlin("jvm") version "2.3.21"
	id("io.spring.dependency-management") version "1.1.7"
	id("org.jooq.jooq-codegen-gradle") version "3.20.3"
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

// Reuses the same Spring Boot BOM as the root project so the jOOQ/MySQL driver
// versions used here stay in lockstep with the versions the running app uses.
dependencyManagement {
	imports {
		mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.0")
	}
}

dependencies {
	implementation("org.jooq:jooq")

	jooqCodegen("com.mysql:mysql-connector-j")
}

// Local dev DB started via `docker compose up` from backend/compose.yaml.
// useUnicode/characterEncoding force a UTF-8 client connection for this codegen
// connection specifically. (The actual cause of an earlier round of mojibake in
// the generated Korean COMMENTs was a `mysql` CLI import without
// --default-character-set=utf8mb4, not this JDBC connection — but forcing UTF-8
// here too is cheap insurance.)
val dbCoreJdbcUrl = "jdbc:mysql://localhost:3306/stablecoin_payment?useUnicode=true&characterEncoding=UTF-8"
val dbCoreUser = "root"
val dbCorePassword = "verysecret"

jooq {
	configuration {
		jdbc {
			driver = "com.mysql.cj.jdbc.Driver"
			url = dbCoreJdbcUrl
			user = dbCoreUser
			password = dbCorePassword
		}
		generator {
			name = "org.jooq.codegen.KotlinGenerator"
			database {
				name = "org.jooq.meta.mysql.MySQLDatabase"
				inputSchema = "stablecoin_payment"
				// Flyway's own bookkeeping table — not part of the application schema.
				excludes = "flyway_schema_history"
			}
			target {
				packageName = "paytech.practice.pay.dbcore.jooq"
				directory = "build/generated-src/jooq/main"
			}
		}
	}
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

// The official jOOQ Gradle plugin does not wire its output directory into the
// Kotlin source set or the compile task graph on its own — both must be added
// explicitly.
sourceSets {
	main {
		kotlin {
			srcDir(layout.buildDirectory.dir("generated-src/jooq/main"))
		}
	}
}

tasks.named("compileKotlin") {
	dependsOn("jooqCodegen")
}

// jOOQ-generated code (SCREAMING_SNAKE_CASE table/field constants, long generated
// lines, etc.) is never hand-edited ("생성 코드를 직접 수정하지 않는다") and
// shouldn't be linted or reformatted like the rest of the codebase.
ktlint {
	filter {
		exclude { entry -> entry.file.path.contains("generated-src") }
	}
}

// The generated-src directory is included in the main source set above, so
// Gradle's task-input validation requires every ktlint task that reads it to
// have an explicit dependency on the task that produces it (jooqCodegen) —
// otherwise ktlint could run before/without the generated code existing.
tasks.matching { it.name.startsWith("runKtlint") || it.name.startsWith("ktlint") }.configureEach {
	dependsOn("jooqCodegen")
}
