plugins {
	id("practicepay.spring-web-app")
	id("practicepay.kotest")
	id("practicepay.mockk")
}

// Now has a real Use Case (AuthenticateMerchantUserUseCase, modules:application)
// backed by modules:infra-persistence, so it needs jOOQ + a DataSource like
// apps:api-admin does — see backend/CLAUDE.md's Apps section
// (webmvc/security/validation come from practicepay.spring-web-app).
dependencies {
	implementation(project(":modules:domain"))
	implementation(project(":modules:application"))
	implementation(project(":modules:infra-persistence"))
	implementation(project(":modules:infra-support"))
}
