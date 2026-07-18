// convention plugin(practicepay.*)을 제공하는 포함된 빌드다 — 다른 어떤 블록보다도
// 먼저 와야 하는 Gradle 제약이라 파일 맨 위에 둔다(backend/CLAUDE.md의
// "build-logic" 절 참고).
pluginManagement {
	includeBuild("build-logic")
}

rootProject.name = "practice-pay"

include("modules:domain")
include("modules:application")
include("modules:infra-persistence")
include("modules:common")
include("modules:infra-blockchain")
include("modules:infra-support")
include("db-core")
include("architecture-tests")
include("apps:api-payment")
include("apps:api-admin")
include("apps:api-merchant")
include("apps:batch")
