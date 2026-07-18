// 11개 서브프로젝트 전체가 공유하는 최소 공통 분모다 — Kotlin 플러그인, Java 25
// toolchain, 공통 컴파일러 옵션, JUnit Platform 테스트 러너. 모듈마다 값이 달라질
// 이유가 없는 설정만 여기 둔다(값이 모듈마다 갈리는 건 각 모듈의 build.gradle.kts에
// 그대로 남긴다 — 예: Spring 관련 플러그인, 모듈별 의존성).
plugins {
	id("org.jetbrains.kotlin.jvm")
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
