// Kotest(FunSpec 스타일) 테스트 프레임워크 좌표를 공통화한다 — 이 프로젝트 전체가
// JUnit5 @Test/kotlin-test가 아니라 Kotest 하나로 통일돼 있다(backend/CLAUDE.md
// "테스트" 절).
//
// 여기 버전 문자열은 `../gradle/libs.versions.toml`과 값이 같아야 한다 — Precompiled
// Script Plugin(`src/main/kotlin/*.gradle.kts`)은 그 파일이 선언하는 버전 카탈로그
// 접근자(`libs`)를 못 쓴다(build-logic 자신의 build.gradle.kts는 되지만, kotlin-dsl이
// 컴파일하는 이 스크립트들의 컴파일 classpath에는 카탈로그 접근자 클래스가 없다 —
// 알려진 Gradle 한계, 아래 다른 practicepay.*.gradle.kts도 동일).
plugins {
	id("practicepay.kotlin-common")
}

dependencies {
	testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
	testImplementation("io.kotest:kotest-assertions-core:5.9.1")
}
