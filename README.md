# practice-pay

스테이블코인(USDC) 결제 게이트웨이 MVP. 가맹점 주문(KRW)을 고객이 외부 EVM 지갑에서 Base Sepolia 테스트넷 USDC로 결제하면, PG가 온체인 거래를 확인하고 Fake Exchange를 통해 원화 정산 대상을 생성하는 흐름을 구현합니다.

## 결제 흐름 (MVP)

```text
가맹점 결제 생성
→ PG Hosted Checkout
→ 고객 지갑 연결
→ Base Sepolia USDC 전송
→ 온체인 거래 검증
→ 결제 완료 페이지
→ 가맹점 Redirect 및 Webhook
→ Fake Exchange 환전
→ SettlementReceivable READY
```

MVP 완료 경계: `Payment = SUCCEEDED`, `ExchangeOrder = COMPLETED`, `SettlementReceivable = READY`. 정산 배치, 실제 원화 지급, 환불, AML/KYT, 다중 네트워크는 이후 단계로 미룹니다. 자세한 범위는 [docs/architecture/mvp-scope.md](docs/architecture/mvp-scope.md)와 [ADR-001](docs/decisions/ADR-001-mvp-scope.md)을 참고하세요.

내부 운영자·가맹점 관리자 계정과 가맹점 결제 API Key도 MVP 범위입니다(SUPER_ADMIN Bootstrap, 가맹점 등록 시 OWNER 생성, TEST API Key 발급/폐기). MFA/SSO, 세분화된 RBAC, API Key IP Allowlist·HMAC 서명은 후속 단계입니다. 자세한 설계는 [docs/architecture/identity-access-api-key.md](docs/architecture/identity-access-api-key.md)와 [ADR-006](docs/decisions/ADR-006-identity-api-key-separation.md)을 참고하세요.

## 저장소 구조

```text
backend/    Kotlin + Spring Boot 백엔드 (실제 코드가 있는 유일한 디렉터리)
frontend/   프론트엔드 (아직 스캐폴딩 전)
docs/       도메인 용어, 아키텍처, DB 설계, ADR 등 설계 문서 (단일 기준 소스)
```

백엔드는 12개 Gradle 서브프로젝트로 구성된 멀티모듈 프로젝트입니다.

```text
backend/
  apps/                독립 배포되는 Spring Boot 앱 4개 (api-payment / api-admin / api-merchant / batch)
  modules/             제품 라이브러리 — 헥사고날 계층 (domain → application → infra-*)
  db-core/             DB 스키마(Flyway) 소유 + jOOQ 코드 생성
  architecture-tests/  ArchUnit 아키텍처 규칙 (테스트 전용)
  build-logic/         Gradle Convention Plugin (Composite Build)
```

최상위 디렉터리는 "Gradle 모듈이냐"가 아니라 **역할**로 나뉩니다 — 판별 기준은 [backend/CLAUDE.md](backend/CLAUDE.md)에 정리돼 있습니다. 프론트엔드는 아직 시작 전입니다.

## 기술 스택

- Kotlin 2.3.21 / Java 25 (toolchain) / Spring Boot 4.1.0 / Gradle Kotlin DSL
- MySQL + jOOQ (JPA/Hibernate 미사용), 스키마 마이그레이션은 Flyway
- Base Sepolia 테스트넷 / USDC
- 테스트: Kotest(FunSpec), MockK, ArchUnit

## 시작하기

필요한 것: Docker, JDK 25(Gradle toolchain이 자동으로 받아오므로 미리 설치하지 않아도 됩니다).
모든 명령은 `backend/` 디렉터리에서 실행합니다 (Windows: `gradlew.bat`, 그 외: `./gradlew`).

### 처음 한 번 — 로컬 환경 세팅

```bash
cd backend

# 1) MySQL 기동
docker compose up -d

# 2) 스키마 적용 (Flyway)
#    앱을 띄우면 자동으로 적용되지만, jOOQ 코드 생성이 테이블을 먼저 필요로 하고
#    앱 빌드는 그 생성 결과에 의존하므로 최초 1회는 앱 없이 적용합니다.
docker compose run --rm flyway migrate

# 3) 개발용 시드 (선택) — 로그인/API Key로 실제 요청을 해보려면 필요합니다.
#    순서가 있습니다: 첫 파일이 만드는 가맹점에 두 번째 파일이 계정을 얹습니다.
docker exec -i backend-mysql-1 mysql --default-character-set=utf8mb4 -uroot -pverysecret stablecoin_payment < db-core/src/main/resources/db/seed/seed_dev_data.sql
docker exec -i backend-mysql-1 mysql --default-character-set=utf8mb4 -uroot -pverysecret stablecoin_payment < db-core/src/main/resources/db/seed/seed_dev_identity_data.sql

# 4) 빌드 (jOOQ 코드 생성이 먼저 실행된 뒤 컴파일 + 테스트)
gradlew.bat build
```

### 이후 일상 개발

```bash
gradlew.bat build                          # 전체 빌드 (컴파일 + 테스트)
gradlew.bat test                           # 전체 테스트
gradlew.bat :apps:api-payment:bootRun      # 결제 API 실행 (8081)
```

앱은 네 개이고 각각 독립 실행됩니다 — `:apps:api-payment`(8081), `:apps:api-admin`(8082), `:apps:api-merchant`(8083), `:apps:batch`(웹 서버 없음, 폴링 Worker). `gradlew.bat bootRun`처럼 앱을 지정하지 않으면 네 앱을 한꺼번에 띄우려 하므로, 항상 위처럼 앱을 지정합니다.

- **스키마는 앱이 부팅할 때 자동으로 적용됩니다**(`spring-boot-starter-flyway`). 새 마이그레이션을 추가했다면 앱을 다시 띄우기만 하면 됩니다. 단 `mysql` CLI로 스키마를 직접 적용하지는 마세요 — Flyway 이력이 남지 않아 다음 기동이 거부됩니다.
- **시드는 자동 적용되지 않습니다.** 운영에 개발용 계정이 실리지 않도록 `db/seed/`로 분리해 뒀기 때문입니다(위 3번을 직접 실행해야 합니다).
- **테스트는 위 세팅과 무관하게 돕니다** — Testcontainers가 MySQL 컨테이너를 자동으로 띄우고 마이그레이션도 직접 적용합니다.
- 각 앱의 `requests.http`(IntelliJ HTTP Client)로 로그인·결제 생성 요청을 바로 실행해볼 수 있습니다.

자세한 명령과 규칙은 [backend/CLAUDE.md](backend/CLAUDE.md)를 참고하세요.

## 문서

- [docs/README.md](docs/README.md) — 도메인 용어 사전, 도메인 모델, 상태 전이 정책, 아키텍처, DB 설계, ADR로 이어지는 문서 인덱스
- [CLAUDE.md](CLAUDE.md) / [backend/CLAUDE.md](backend/CLAUDE.md) / [frontend/CLAUDE.md](frontend/CLAUDE.md) — 코딩 에이전트(Claude Code)를 위한 공통·영역별 작업 지침
- [AGENTS.md](AGENTS.md) — Codex 등 다른 코딩 에이전트를 위한 진입점 (루트 `CLAUDE.md`를 단일 기준으로 사용)

도메인 규칙, 상태, DB, API, 아키텍처를 변경할 때는 구현과 `docs/`, 마이그레이션, 테스트를 함께 갱신합니다.
