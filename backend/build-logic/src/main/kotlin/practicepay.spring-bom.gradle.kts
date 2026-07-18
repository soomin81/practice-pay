// db-core/infra-persistence/infra-blockchain이 공유하는 Spring Boot BOM import다 —
// 이 세 모듈은 실제 앱(`org.springframework.boot` 플러그인)이 아니라 라이브러리라
// Spring Boot 플러그인 자체는 적용하지 않지만, jOOQ/Spring 관련 좌표 버전을 실제
// 앱이 쓰는 Spring Boot 버전과 항상 맞추기 위해 BOM만 가져온다.
//
// 버전 문자열은 `../gradle/libs.versions.toml`과 맞춘다(practicepay.kotest의 KDoc
// 참고 — Precompiled Script Plugin은 `libs` 카탈로그 접근자를 쓸 수 없다).
plugins {
	id("io.spring.dependency-management")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.0")
	}
}
