plugins {
	id("practicepay.spring-web-app")
	id("practicepay.kotest")
	id("practicepay.mockk")
}

// The only app under apps/ with a real Use Case behind it so far
// (CreatePaymentUseCase, modules:application) — hence the only one wired to
// jooq + infra-persistence today (webmvc/security/validation come from
// practicepay.spring-web-app, applied by all three api-* apps).
// api-admin/api-merchant/batch stay thinner until their own Use Cases exist
// (see backend/CLAUDE.md).
dependencies {
	implementation(project(":modules:domain"))
	implementation(project(":modules:application"))
	implementation(project(":modules:infra-persistence"))
}
