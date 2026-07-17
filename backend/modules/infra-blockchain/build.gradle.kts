plugins {
	kotlin("jvm") version "2.3.21"
	// modules:infra-persistence와 같은 이유로 적용한다 — Spring Boot는 인터페이스를
	// 구현한 Bean이라도 기본적으로 CGLIB(서브클래싱) 프록시를 쓰는데(`spring.aop.
	// proxy-target-class=true`), Kotlin 클래스가 기본 `final`이라 그대로 두면
	// `Cannot subclass final class ...`로 죽는다. 이 모듈의 Adapter(`Web3jBlockchainClient`)
	// 자신은 `@Transactional` 등 AOP Advice가 없지만, 이 모듈이 나중에 Spring
	// 컨텍스트에 함께 올라갈 다른 Bean(`TransactionManagerAdapter` 등)이 있는
	// 앱에서는 컨텍스트 전체가 `proxy-target-class=true`로 동작해 이 모듈의
	// `@Component`도 똑같이 영향을 받는다.
	kotlin("plugin.spring") version "2.3.21"
	id("io.spring.dependency-management") version "1.1.7"
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

// db-core/modules:infra-persistence와 같은 이유로 Spring Boot BOM을 그대로 쓴다 —
// spring-context 버전을 이 모듈이 따로 정하지 않고, 실제로 이 Bean들을 부팅할 앱이
// 쓰는 Spring Boot 버전과 항상 맞아떨어지게 한다.
dependencyManagement {
	imports {
		mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.0")
	}
}

// modules:infra-persistence가 결제 흐름의 영속성 쪽 outbound Port를 구현하는 것과
// 같은 자리에서, 이 모듈은 BlockchainClient Port(modules:application)를 web3j로
// 구현한다 — Base Sepolia는 OP-Stack L2라 표준 EVM JSON-RPC(eth_getTransactionReceipt,
// eth_blockNumber, eth_chainId)만으로 충분하다.
dependencies {
	implementation(project(":modules:domain"))
	implementation(project(":modules:application"))

	// @Component로 Spring 컨텍스트에 Bean으로 등록되어, 이 모듈에 의존하는 앱이
	// 자신의 컴포넌트 스캔이 infra.blockchain까지 닿게만 하면 되도록 한다
	// (modules:infra-persistence의 jOOQ Adapter와 같은 배선 방식).
	implementation("org.springframework:spring-context")
	implementation("org.web3j:core:4.14.0")

	testImplementation(project(":modules:domain"))
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
