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

- 백엔드는 아직 Spring Initializr 스켈레톤 단계이며, 계획된 모듈 구조(`apps/`, `modules/*`, `db-core/`, `architecture-tests/`)는 `settings.gradle.kts`에 연결되지 않은 빈 디렉터리입니다.
- 프론트엔드는 아직 시작 전입니다.

## 기술 스택

- Kotlin 2.3.21 / Java 25 (toolchain) / Spring Boot 4.1.0 / Gradle Kotlin DSL
- MySQL 8.x + jOOQ (JPA/Hibernate 미사용)
- Base Sepolia 테스트넷 / USDC
- 테스트: Kotest(FunSpec), MockK, ArchUnit

## 시작하기

모든 명령은 `backend/` 디렉터리에서 실행합니다 (Windows: `gradlew.bat`).

```bash
gradlew.bat build     # 빌드 (컴파일 + 테스트)
gradlew.bat bootRun    # 로컬 실행 (MySQL 필요)
gradlew.bat test       # 전체 테스트 실행
```

로컬 MySQL은 `backend/compose.yaml`(`docker compose up`)로 띄우거나, 테스트 시에는 Testcontainers가 자동으로 컨테이너를 띄웁니다. 자세한 명령과 규칙은 [backend/CLAUDE.md](backend/CLAUDE.md)를 참고하세요.

## 문서

- [docs/README.md](docs/README.md) — 도메인 용어 사전, 도메인 모델, 상태 전이 정책, 아키텍처, DB 설계, ADR로 이어지는 문서 인덱스
- [CLAUDE.md](CLAUDE.md) / [backend/CLAUDE.md](backend/CLAUDE.md) / [frontend/CLAUDE.md](frontend/CLAUDE.md) — 코딩 에이전트(Claude Code)를 위한 공통·영역별 작업 지침
- [AGENTS.md](AGENTS.md) — Codex 등 다른 코딩 에이전트를 위한 진입점 (루트 `CLAUDE.md`를 단일 기준으로 사용)

도메인 규칙, 상태, DB, API, 아키텍처를 변경할 때는 구현과 `docs/`, 마이그레이션, 테스트를 함께 갱신합니다.
