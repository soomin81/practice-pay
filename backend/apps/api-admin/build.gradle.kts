plugins {
	id("practicepay.spring-boot-app")
	id("practicepay.kotest")
	id("practicepay.mockk")
}

// Now has a real Use Case (AuthenticateInternalUserUseCase, modules:application)
// backed by modules:infra-persistence, so it needs jOOQ + a DataSource like
// apps:api-payment does — see backend/CLAUDE.md's Apps section.
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
