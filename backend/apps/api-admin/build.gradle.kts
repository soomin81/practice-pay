plugins {
	id("practicepay.spring-web-app")
	id("practicepay.kotest")
	id("practicepay.mockk")
	alias(libs.plugins.restdocs.apiSpec)
}

// Now has a real Use Case (AuthenticateInternalUserUseCase, modules:application)
// backed by modules:infra-persistence, so it needs jOOQ + a DataSource like
// apps:api-payment does — see backend/CLAUDE.md's Apps section
// (webmvc/security/validation come from practicepay.spring-web-app).
//
// It also generates an OpenAPI spec now — the internal operator console API here is what
// frontend/admin consumes (docs/architecture/admin-console-api.md), the same
// contract discipline api-payment/api-merchant use.
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
// REST Docs 기반을 고른 이유는 **스펙이 거짓말을 할 수 없기 때문**이다(api-payment/
// api-merchant의 build.gradle.kts와 같은 판단). 산출물: build/api-spec/openapi3.yaml.
openapi3 {
	setServer("http://localhost:8082")
	title = "Practice Pay — Admin Console API"
	description =
		"PG 내부 운영자용 콘솔 API(세션 쿠키 인증)다. 로그인·세션 복원·로그아웃과 가맹점 등록/목록, " +
		"내부 운영자 계정 발급을 제공한다. 브라우저 대면 계약(CSRF/CORS 포함)의 원문은 " +
		"docs/architecture/admin-console-api.md에 있다."
	version = "0.1.0"
	format = "yaml"
}

// openapi3 태스크는 Configuration Cache와 호환되지 않고(restdocs-api-spec 0.20.1의
// OpenApi3Task가 직렬화되지 않는 Jackson ObjectMapper를 보유), test에 의존시키지 않으면
// 스니펫이 없어 빈 스펙이 조용히 나온다 — 다른 두 앱과 같은 함정·같은 처리다.
tasks.matching { it.name == "openapi3" || it.name == "openapi" }.configureEach {
	dependsOn(tasks.test)

	notCompatibleWithConfigurationCache(
		"restdocs-api-spec 0.20.1의 OpenApi3Task가 직렬화되지 않는 Jackson ObjectMapper를 보유한다",
	)
}
