plugins {
	id("practicepay.kotlin-common")
	id("practicepay.kotest")
	id("practicepay.mockk")
}

// 아직 이 모듈에 담을 코드가 없다 — 어떤 레이어에서도 참조할 수 있는 공용 유틸리티가
// 실제로 필요해질 때 채운다. 그때까지는 다른 modules:*에 대한 의존성을 추가하지
// 않는다(순환 의존을 막기 위해서이기도 하고, `backend/CLAUDE.md`의 "의존성은 지금
// 실제로 하는 일에만 맞춘다" 원칙과도 같다).
