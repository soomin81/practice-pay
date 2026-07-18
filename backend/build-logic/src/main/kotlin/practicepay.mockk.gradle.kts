// MockK 좌표를 공통화한다 — 이 프로젝트는 Mockito가 아니라 MockK로 통일돼 있다
// (backend/CLAUDE.md "테스트" 절). 실제 MySQL/RPC 통합 테스트를 쓰는 모듈
// (infra-persistence/db-core)이나 Mock 없이 ArchUnit만 쓰는 모듈(architecture-tests)은
// 이 플러그인을 적용하지 않는다.
//
// 버전 문자열은 `../gradle/libs.versions.toml`과 맞춘다(practicepay.kotest의 KDoc
// 참고 — Precompiled Script Plugin은 `libs` 카탈로그 접근자를 쓸 수 없다).
plugins {
	id("practicepay.kotlin-common")
}

dependencies {
	testImplementation("io.mockk:mockk:1.14.3")
}
