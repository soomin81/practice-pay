// Test-only module: no src/main, just ArchUnit rules run against the compiled
// classes of other modules (pulled in below as testImplementation deps).
//
// 규칙이 검사하는 모듈은 전부 여기 있어야 한다 — ArchUnit은 대상 클래스가 하나도
// 없으면 규칙을 조용히 통과시키기 때문이다(HexagonalLayerTest의 "every layer must
// actually be imported" 가드가 이 실수를 잡는다).
plugins {
	id("practicepay.kotlin-common")
	id("practicepay.kotest")

	// 검사 대상 모듈들이 Spring Boot BOM으로 버전을 받는 좌표(`org.jooq:jooq`,
	// `org.springframework:spring-context` 등)를 transitive 의존성으로 끌고 오는데,
	// BOM이 없으면 그 좌표들이 버전 없이 도착해 resolve에 실패한다 — 규칙을 돌리려면
	// 대상 클래스가 classpath에 있어야 하므로 여기서도 같은 BOM을 적용한다.
	id("practicepay.spring-bom")
}

dependencies {
	testImplementation(project(":modules:domain"))
	testImplementation(project(":modules:application"))
	testImplementation(project(":modules:infra-persistence"))
	testImplementation(project(":modules:infra-blockchain"))

	// inbound Adapter(계층 방향·네이밍 규칙의 대상). 이 네 앱은 Spring Boot 앱이지만
	// 여기서는 실행하지 않고 컴파일된 클래스만 읽는다.
	testImplementation(project(":apps:api-payment"))
	testImplementation(project(":apps:api-admin"))
	testImplementation(project(":apps:api-merchant"))
	testImplementation(project(":apps:batch"))

	testImplementation(libs.archunit)
}
