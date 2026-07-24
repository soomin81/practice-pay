plugins {
	id("practicepay.spring-web-app")
	id("practicepay.kotest")
	id("practicepay.mockk")
	alias(libs.plugins.restdocs.apiSpec)
}

// Now has a real Use Case (AuthenticateMerchantUserUseCase, modules:application)
// backed by modules:infra-persistence, so it needs jOOQ + a DataSource like
// apps:api-admin does — see backend/CLAUDE.md's Apps section
// (webmvc/security/validation come from practicepay.spring-web-app).
//
// It also generates an OpenAPI spec now — the merchant console API here is what
// frontend/merchant consumes (docs/architecture/merchant-console-api.md), the same
// contract-first discipline api-payment uses for the Hosted Checkout API.
dependencies {
	implementation(project(":modules:domain"))
	implementation(project(":modules:application"))
	implementation(project(":modules:infra-persistence"))
	implementation(project(":modules:infra-support"))

	testImplementation(libs.springBoot.starterRestdocs)
	testImplementation(libs.springRestdocs.mockmvc)
	testImplementation(libs.restdocsApiSpec.mockmvc)
}

// 통과한 테스트에서 OpenAPI 스펙을 만든다 — 애노테이션 기반(springdoc)이 아니라
// REST Docs 기반을 고른 이유는 **스펙이 거짓말을 할 수 없기 때문**이다(api-payment의
// build.gradle.kts와 같은 판단). 산출물: build/api-spec/openapi3.yaml (gitignore 대상).
openapi3 {
	setServer("http://localhost:8083")
	title = "Practice Pay — Merchant Console API"
	description =
		"가맹점 운영자용 콘솔 API(세션 쿠키 인증)다. 로그인·세션 복원·로그아웃과 API Key 발급/목록/폐기를 " +
		"제공한다. 브라우저 대면 계약(CSRF/CORS 포함)의 원문은 docs/architecture/merchant-console-api.md에 있다."
	version = "0.1.0"
	format = "yaml"
}

// openapi3 태스크는 Gradle Configuration Cache와 호환되지 않고(restdocs-api-spec 0.20.1의
// OpenApi3Task가 직렬화되지 않는 Jackson ObjectMapper를 보유), test에 의존시키지 않으면
// 스니펫이 없어 빈 스펙이 조용히 나온다 — api-payment의 build.gradle.kts와 같은 함정·같은 처리다.
tasks.matching { it.name == "openapi3" || it.name == "openapi" }.configureEach {
	dependsOn(tasks.test)

	notCompatibleWithConfigurationCache(
		"restdocs-api-spec 0.20.1의 OpenApi3Task가 직렬화되지 않는 Jackson ObjectMapper를 보유한다",
	)
}
