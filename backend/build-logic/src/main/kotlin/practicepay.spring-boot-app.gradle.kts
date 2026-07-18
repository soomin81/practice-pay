// apps:api-payment/api-admin/api-merchant/batch 4개 모두에 공통인 것만 담는다 —
// `org.springframework.boot` 플러그인이 `io.spring.dependency-management`와 함께
// 있으면 자기 버전에 맞는 spring-boot-dependencies BOM을 자동으로 가져오므로(
// practicepay.spring-bom과 달리 여기서는 mavenBom을 직접 import하지 않는다),
// jOOQ/Jackson/MySQL 드라이버 같은 좌표는 버전을 따로 적지 않아도 BOM이 맞춰준다.
//
// 앱마다 다른 부분(webmvc/security/validation 스타터는 batch에 없다,
// project(":modules:...") 의존성 그래프는 앱마다 다르다)은 일부러 여기 넣지 않고
// 각 apps/*/build.gradle.kts에 명시적으로 남긴다 — 모듈 그래프는 그 파일만 보고
// 파악할 수 있어야 한다.
//
// 버전이 붙은 좌표(kotlin-logging-jvm/springmockk/kotest-extensions-spring)의 문자열은
// `../gradle/libs.versions.toml`과 맞춘다(practicepay.kotest의 KDoc 참고 —
// Precompiled Script Plugin은 `libs` 카탈로그 접근자를 쓸 수 없다).
plugins {
	id("practicepay.kotlin-common")
	id("org.jetbrains.kotlin.plugin.spring")
	id("org.springframework.boot")
	id("io.spring.dependency-management")
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-jooq")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")
	implementation("io.github.oshai:kotlin-logging-jvm:7.0.7")
	runtimeOnly("com.mysql:mysql-connector-j")

	testImplementation("com.ninja-squad:springmockk:5.0.1")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-mysql")
	testImplementation("io.kotest.extensions:kotest-extensions-spring:1.3.0")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
