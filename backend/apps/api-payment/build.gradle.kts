plugins {
	id("practicepay.spring-boot-app")
	id("practicepay.kotest")
	id("practicepay.mockk")
}

// The only app under apps/ with a real Use Case behind it so far
// (CreatePaymentUseCase, modules:application) — hence the only one wired to
// webmvc + jooq + infra-persistence today. api-admin/api-merchant/batch stay
// thinner until their own Use Cases exist (see backend/CLAUDE.md).
dependencies {
	implementation(project(":modules:domain"))
	implementation(project(":modules:application"))
	implementation(project(":modules:infra-persistence"))

	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")

	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
}
