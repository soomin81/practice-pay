plugins {
	id("practicepay.spring-library")
	id("practicepay.kotest")
	id("practicepay.mockk")
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
	implementation(libs.web3j.core)

	// 테스트 전용 의존성은 없다 — `testImplementation`이 `implementation`을 상속해서
	// 위 의존성(domain/application/web3j)이 테스트 컴파일 클래스패스에도 그대로 있고,
	// Kotest/MockK는 practicepay.kotest/practicepay.mockk가 붙여준다.
}
