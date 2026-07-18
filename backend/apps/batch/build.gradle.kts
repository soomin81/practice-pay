plugins {
	id("practicepay.spring-boot-app")
	id("practicepay.kotest")
	id("practicepay.mockk")
}

// 첫 실제 Job(BlockchainTransaction 감지·Confirm 폴링 Worker, backend/CLAUDE.md
// 참고)이 생기면서 jOOQ + DataSource + modules:application/infra-persistence/
// infra-blockchain이 필요해졌다 — apps:api-payment/api-admin과 같은 이유. 여전히
// 웹 앱은 아니다 — spring-boot-starter-web*는 없다.
dependencies {
	implementation(project(":modules:domain"))
	implementation(project(":modules:application"))
	implementation(project(":modules:infra-persistence"))
	implementation(project(":modules:infra-support"))
	implementation(project(":modules:infra-blockchain"))

	implementation("org.springframework.boot:spring-boot-starter-batch")

	testImplementation("org.springframework.boot:spring-boot-starter-batch-test")
}
