plugins {
	kotlin("jvm") version "2.3.21"
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

// modules:infra-persistence가 결제 흐름의 영속성 쪽 outbound Port를 구현하는 것과
// 같은 자리에서, 이 모듈은 Base Sepolia 온체인 조회(BlockchainTransaction 감지·
// Confirm, `docs/architecture/mvp-scope.md`의 전체 흐름 참고)를 구현할 자리다.
// 아직 modules:application에 그 Port(예: 온체인 클라이언트)가 없어서 이 모듈에
// 담을 코드도, modules:domain/application에 대한 의존성도 없다 — 그 Use Case가
// 생기면 infra-persistence의 build.gradle.kts와 같은 모양(domain+application
// 의존성, 필요한 온체인 클라이언트 라이브러리)으로 채운다.
dependencies {
	testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
	testImplementation("io.kotest:kotest-assertions-core:5.9.1")
	testImplementation("io.mockk:mockk:1.14.3")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
