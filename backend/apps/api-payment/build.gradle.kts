plugins {
	id("practicepay.spring-web-app")
	id("practicepay.kotest")
	id("practicepay.mockk")
	alias(libs.plugins.restdocs.apiSpec)
}

// The only app under apps/ with a real Use Case behind it so far
// (CreatePaymentUseCase, modules:application) — hence the only one wired to
// jooq + infra-persistence today (webmvc/security/validation come from
// practicepay.spring-web-app, applied by all three api-* apps).
// api-admin/api-merchant/batch stay thinner until their own Use Cases exist
// (see backend/CLAUDE.md).
//
// This is also the only app that generates an OpenAPI spec — the Hosted Checkout
// API here is the one frontend/ consumes (docs/architecture/checkout-api.md).
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
// REST Docs 기반을 고른 이유는 **스펙이 거짓말을 할 수 없기 때문**이다. 애노테이션은
// 코드와 따로 놀아도 빌드가 통과하지만, 여기서는 실제로 요청을 보내고 응답을 검증한
// 테스트만 스펙에 남는다.
//
// 산출물: build/api-spec/openapi3.yaml (gitignore 대상 — 생성물이라 커밋하지 않는다)
openapi3 {
	setServer("http://localhost:8081")
	title = "Practice Pay — Payment & Hosted Checkout API"
	description =
		"가맹점 서버용 결제 API(API Key 인증)와 고객 브라우저용 Hosted Checkout API(무인증)를 " +
		"한 앱이 제공한다. 계약 원문은 docs/architecture/checkout-api.md에 있다."
	version = "0.1.0"
	format = "yaml"
}

// openapi3 태스크는 Gradle Configuration Cache와 호환되지 않는다 — 태스크가 Jackson
// ObjectMapper를 필드로 들고 있는데 그 안의 StdDateFormat이 직렬화되지 않는다
// (restdocs-api-spec 0.20.1 기준). 이 프로젝트는 gradle.properties에서 Configuration
// Cache를 켜 두므로, 개발자가 --no-configuration-cache를 외우게 하는 대신 이 태스크
// 하나만 예외로 표시한다 — 나머지 빌드는 캐시 이점을 그대로 유지한다.
// 플러그인이 태스크를 늦게 등록해서 tasks.named("openapi3")로는 잡히지 않는다 —
// matching으로 지연 평가한다.
tasks.matching { it.name == "openapi3" || it.name == "openapi" }.configureEach {
	// **test에 의존시키지 않으면 빈 스펙이 조용히 나온다.** 스펙의 재료는 test가 만드는
	// build/generated-snippets인데, 플러그인은 그 의존을 걸어주지 않는다 — 클린 상태에서
	// openapi3만 돌리면 경로 0개짜리 12줄 YAML이 BUILD SUCCESSFUL과 함께 나온다.
	// 실제로 그렇게 나오는 것을 확인하고 추가했다.
	dependsOn(tasks.test)

	notCompatibleWithConfigurationCache(
		"restdocs-api-spec 0.20.1의 OpenApi3Task가 직렬화되지 않는 Jackson ObjectMapper를 보유한다",
	)
}
