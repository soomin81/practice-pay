plugins {
	id("practicepay.spring-library")
	id("practicepay.kotest")
	id("practicepay.mockk")
}

// modules:infra-persistence(영속성)/modules:infra-blockchain(온체인)과 같은 자리의
// 세 번째 outbound Adapter 모듈이다 — 특정 외부 시스템에 묶이지 않는 자잘한 Port
// 구현(ID 생성, 비밀번호/토큰 해시, 환율)을 모은다. 원래 이 구현들은 앱마다 자기
// `support` 패키지에 복제돼 있었는데(4개 클래스가 9곳), 앱이 늘면서 복제본이 서로
// 어긋날 위험이 커져 공유 모듈로 옮겼다.
//
// modules:common이 아니라 여기인 이유: 이것들은 전부 application.port.outbound의
// Port 구현체(@Component)라서 modules:application과 Spring에 의존한다 —
// "의존성 없는 공용 유틸리티"라는 modules:common의 역할과 맞지 않고, 헥사고날
// 관점에서도 outbound Adapter라 infra-* 자리가 맞다(architecture-tests의
// HexagonalLayerTest가 정의한 Outbound Adapter 계층 `paytech.practice.pay.infra..`에
// 자동으로 포함된다는 실질적 이점도 있다).
dependencies {
	implementation(project(":modules:domain"))
	implementation(project(":modules:application"))

	// @Component/@Value로 Bean 등록 — 이 모듈에 의존하는 앱이 자신의 컴포넌트 스캔이
	// 필요한 하위 패키지까지 닿게만 하면 된다(modules:infra-persistence와 같은 배선 방식).
	implementation("org.springframework:spring-context")

	// BCryptPasswordEncoderAdapter만 쓴다 — spring-security-crypto는 웹/필터 없이
	// 해시 알고리즘만 담은 최소 모듈이라, 웹 앱이 아닌 곳에서 써도 부담이 없다.
	implementation("org.springframework.security:spring-security-crypto")
}
