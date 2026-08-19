# CLAUDE.md

이 파일은 이 저장소에서 작업할 때 Claude Code(claude.ai/code)에게 제공하는 지침이다.

이 문서는 **루트/공통** 지침 파일이다. 저장소 전체에 적용되는 내용만 다룬다. 디렉토리별 명령어와 구현 컨벤션은 다음을 참고한다:

- `backend/CLAUDE.md` — Kotlin/Spring Boot 백엔드(빌드/테스트 명령어, 헥사고날 아키텍처, jOOQ/MySQL 컨벤션). **앞으로 따를 규칙**만 담는다.
- `backend/IMPLEMENTATION-NOTES.md` — 백엔드의 **기능별 구현 판단 기록**(Use Case·Adapter를 만들며 정한 상수, 남긴 gap, 실물 검증 결과). 새 작업을 하려고 통째로 읽을 필요는 없고, 비슷한 상황의 선례를 찾을 때 본다.
- `frontend/CLAUDE.md` — 프론트엔드(실행 명령어, 앱 분리 기준, 체크아웃 화면 컨벤션). **백엔드와 달리 Docker를 쓰지 않고 호스트 Node로 돌린다.**

## 저장소 구조

이 저장소는 최상위 디렉토리 3개로 구성된 모노레포다:

- `backend/` — Kotlin/Spring Boot Gradle 프로젝트. **Gradle 빌드 루트는 저장소 루트가 아니라 이 디렉토리다** — `settings.gradle.kts`/`gradle.properties`/`gradlew`가 전부 여기 있고, Gradle 명령은 `backend/`에서 실행한다.
- `frontend/` — Vite/React 앱들. 지금은 고객용 Hosted Checkout인 `frontend/payment/` 하나뿐이다. **워크스페이스를 쓰지 않아 각 앱이 독립 프로젝트이므로, npm 명령은 저장소 루트나 `frontend/`가 아니라 앱 디렉토리에서 실행한다.**
- `docs/` — 이 프로젝트의 설계 기준 문서다("문서" 절 참고). 한글로 작성돼 있다.

저장소 전체에 걸리는 설정 파일은 루트에 둔다:

- `.gitattributes` — 모든 파일의 줄바꿈을 **LF로 고정**한다(`*.bat`만 CRLF). 각자의 `core.autocrlf` 설정에 좌우되지 않게 하려는 것이다 — 이 파일이 backend 일부만 덮고 있던 동안 실제로 작업 트리에 LF/CRLF 파일이 섞였다.
- `.gitignore` — IDE/에이전트 로컬 상태와 OS 산출물. 빌드 산출물(`build/`, `.gradle` 등)은 Gradle 프로젝트에 속하므로 `backend/.gitignore`가 담당한다.
- `.editorconfig` — `docs/`와 루트 마크다운용. `backend/.editorconfig`는 `root = true`라 backend 아래는 이 파일의 영향을 받지 않는다(그쪽은 ktlint의 탭 들여쓰기 컨벤션을 따로 갖는다) — 의도적인 분리다.

## 문서 — 도메인 로직을 구현하기 전에 읽는다

`docs/README.md`가 전체 문서를 인덱싱한다. **`docs/`는 검토를 거친 설계 기준이므로 코드로부터의 추측보다 우선한다** — 도메인은 이제 전부 구현돼 있지만(`backend/CLAUDE.md`의 "도메인 코드 컨벤션"), 코드가 곧 기준인 것은 아니다. 코드와 `docs/`가 어긋나 보이면 임의로 한쪽을 따르지 말고 어느 쪽이 낡았는지 판단해서 같은 변경에서 함께 맞춘다:

- `docs/domain/glossary.md` — 정식 용어 정의(한글/영문). 가장 먼저 읽는다. ADR와 도메인 모델은 이 용어를 전제로 한다.
- `docs/domain/domain-model.md` — 애그리게이트, 값 객체, 도메인 서비스, 그리고 도메인 코드가 프레임워크나 인프라에 의존하지 않는다는 설계 규칙(애그리게이트는 ID로만 서로 참조하고, 상태 변경은 컨트롤러/리포지토리의 직접 필드 대입이 아니라 도메인 메서드를 통해서만 이뤄진다).
- `docs/domain/state-transitions.md` — 모든 애그리게이트의 상태 머신(아래 요약 참고).
- `docs/architecture/mvp-scope.md` — MVP 포함/제외 범위와 전체 Happy Path 흐름.
- `docs/architecture/persistence-jooq.md` — 모듈 계층, jOOQ 컨벤션, 자격증명 저장 규칙(백엔드 전용 — `backend/CLAUDE.md` 참고).
- `docs/architecture/identity-access-api-key.md` — `InternalUser`/`MerchantUser`/`AccountInvitation`/`MerchantApiKey` 설계: 역할, 계정 생명주기, API Key 해시·저장, Scope.
- `docs/architecture/checkout-api.md` — 고객 브라우저가 호출하는 Hosted Checkout API 계약. **구현보다 먼저 쓴 문서이고 구현이 그것을 따라왔다** — `frontend/` 작업의 기준이므로 계약을 바꿀 때는 이 문서를 먼저 고친다. `apps:api-payment`가 구현한다(별도 앱을 만들지 않은 이유는 그 문서 2.1).
- `docs/database/database-design.md` — 전체 MySQL 스키마 설계. 실제로 적용된 스키마는 `backend/db-core/src/main/resources/db/migration/`의 Flyway 마이그레이션으로 존재한다(`backend/CLAUDE.md` 참고) — 스키마가 바뀌면 둘을 동기화한다.
- `docs/decisions/ADR-00{1..6}-*.md` — MVP 범위, MySQL+jOOQ, Hosted Checkout, Fake Exchange, 정산 경계, Identity/API Key 분리에 대한 ADR.

## 도메인: 이 시스템은 무엇인가

스테이블코인(USDC) 결제 게이트웨이 MVP다. 가맹점 고객이 외부 EVM 지갑에서 **Base Sepolia(테스트넷)** 상의 USDC를 PG Hosted Checkout 페이지를 통해 전송해 KRW 표시 주문을 결제한다. PG는 온체인 전송을 감지·Confirm하고, USDC를 **Fake Exchange**(ADR-004에 따라 Mock)로 매도해 KRW 정산채권을 만든다. 실제 지급, 정산 배치, AML/KYT, 환불은 MVP 범위에서 명시적으로 제외된다(ADR-001, ADR-005).

전체 흐름:
```
Payment 생성 → PaymentQuote 확정 → CheckoutSession 생성 → 고객 지갑 연결
→ USDC 전송 → BlockchainTransaction 감지 및 Confirm → Payment SUCCEEDED
→ 결제 완료 페이지와 Webhook → Fake Exchange 매도 → SettlementReceivable READY
```
`Payment = SUCCEEDED`, `ExchangeOrder = COMPLETED`, `SettlementReceivable = READY`가 되면 MVP가 완료된 것이다.

### 애그리게이트와 상태 머신

아래는 **`docs/domain/state-transitions.md`의 요약**이다(빠른 참조용). 전이 조건까지 필요하면 원본을 본다 — 상태 머신을 바꿀 때는 원본을 먼저 고치고 이 표를 맞춘다.

| 애그리게이트 | 상태 |
|---|---|
| `Payment`(Root) | `CREATED → READY → PROCESSING → CONFIRMING → SUCCEEDED`, `CREATED`/`READY`에서 `EXPIRED`로, `PROCESSING`/`CONFIRMING`에서 `FAILED`로 |
| `CheckoutSession` | `CREATED → OPEN → WALLET_CONNECTED → PAYMENT_SUBMITTED → COMPLETED`(`PAYMENT_SUBMITTED` 이후에는 고객 취소 불가) |
| `BlockchainTransaction` | `SUBMITTED → DETECTED → CONFIRMING → CONFIRMED`, 예외 `FAILED`, 그리고 `DETECTED`/`CONFIRMING`에서만 `REORGED`(`CONFIRMED` 이후의 reorg는 범위 밖) |
| `ExchangeOrder` | Fake Exchange(MVP): `REQUESTED → COMPLETED`. 실거래소(향후): `REQUESTED → SUBMITTED → PROCESSING → COMPLETED` |
| `SettlementReceivable` | MVP: `PENDING → READY`. 향후: `READY → ASSIGNED → SETTLED` |
| `WebhookDelivery` | `PENDING → DELIVERING → SUCCEEDED`, 실패 시 `RETRY_WAITING`을 거쳐 최대 재시도 횟수까지, 이후 `FAILED` |

`Payment → SUCCEEDED`는 추가로 다음을 요구한다: 네트워크/체인 ID 일치, 허용된 토큰 Contract, 수취 지갑 일치, 충분한 금액, Receipt 성공, 필요 Confirmation 충족, 중복 Transaction Hash 없음. 검증은 토큰 **Symbol**만으로 "이게 USDC다"라고 판단하지 않는다 — 항상 (네트워크, Contract 주소) 조합으로 검증한다.

그 밖의 애그리게이트: `Merchant`, `Payment`에 1:1로 붙는 불변 `PaymentQuote` 스냅샷(시장 환율, 적용 환율, 스프레드, 금액, 유효 기간), 그리고 역시 `Payment`에 1:1로 붙는 `PaymentCustomer`(구매자 이름·이메일·휴대전화 — 상태가 없고, 파기할 수 있도록 별도 테이블에 둔다. ADR-008).

모든 애그리게이트에 공통되는 규칙: 모든 전이 전에 상태를 검증하고, 컨트롤러/리포지토리의 직접 필드 대입으로 전이하지 않으며, 종료 상태는 재사용하지 않는다.

**"종료 상태 재사용 금지"의 유일한 예외는 `WebhookDelivery`/`OutboxEvent`의 수동 재전송**(`FAILED → PENDING`)이다 — 내부 운영자가 명시적으로 실행할 때만 일어나고 자동 경로에는 없다. 근거와 제약은 `docs/domain/state-transitions.md`의 "수동 재전송" 절에 있다. 새로 종료 상태를 되돌리고 싶어지면 그 절의 판단 기준(자동 흐름과 구분되는가, 다른 길이 없는가)을 먼저 통과시킨다.

### 핵심 도메인 구분

- `Order`(가맹점의 상품/서비스 주문)와 `Payment`(그 주문에 대한 PG의 결제 단위/Aggregate Root)는 서로 다른 개념이다 — 혼동하지 않는다.
- `Settlement`(향후 가맹점 단위로 `SettlementReceivable`을 모으는 개념)와 `Payout`(향후 실제 KRW 계좌 송금)은 **MVP에 구현하지 않는다** — `SettlementReceivable`이 `READY`에 도달하는 것이 MVP의 종착점이다(ADR-005).
- `payment` 레코드에 정산/지급 상태를 절대 추가하지 않는다 — 정산 상태는 오직 `SettlementReceivable`과 그 후속 개념에만 있다.

### Identity & Access

의도적으로 생명주기를 공유하지 않는 세 개의 독립된 자격증명 영역이 있다(ADR-006): `InternalUser`(PG 내부 관리자 로그인; 역할 `SUPER_ADMIN`/`OPERATOR`/`VIEWER`), `MerchantUser`(가맹점별 관리자 로그인; 역할 `OWNER`/`ADMIN`/`VIEWER`), `MerchantApiKey`(결제 API용 서버 간 자격증명 — 개별 사용자 계정이 아니라 `Merchant`가 소유해서 직원이 바뀌어도 살아남는다). `AccountInvitation`은 `InternalUser`/`MerchantUser` 계정을 활성화하는 1회성 토큰 흐름이다. 전체 설계(계정 상태, API Key 해시·저장, Scope)는 `docs/architecture/identity-access-api-key.md`와 ADR-006에 있다.

MVP는 SUPER_ADMIN Bootstrap, 내부 사용자 발급/로그인, 최초 OWNER를 포함한 가맹점 등록, OWNER/ADMIN 하위 계정 발급, `PAYMENT_CREATE`/`PAYMENT_READ` Scope로 제한된 TEST 환경 API Key 발급/폐기를 포함한다. MFA/OTP, SSO, 세분화된 RBAC, API Key IP Allowlist, HMAC 요청 서명은 후속 범위로 미룬다.

## 작업 절차

도메인/상태/DB/API/아키텍처 작업을 구현하기 전에 `docs/`의 관련 문서를 먼저 확인한다("문서" 절 참고) — `docs/`가 우선한다. 변경이 도메인 규칙, 상태 머신, DB 스키마, API, 아키텍처에 영향을 준다면 구현·문서·마이그레이션·테스트를 같은 변경에서 함께 갱신한다 — `docs/`가 코드와 어긋나게 두지 않는다.

이 저장소는 다른 코딩 에이전트(예: Codex)도 같은 `docs/`를 기준으로 함께 사용한다 — 에이전트가 따르는 컨벤션은 서로 충돌하는 로컬 규칙을 새로 만들지 말고 문서화된 내용과 일관되게 유지한다.

Git 커밋 메시지(제목과 본문)는 한글로 작성한다 — 이 프로젝트가 학습용 프로젝트라 코드의 KDoc과 검증 메시지도 한글로 쓰는 것과 같은 맥락이다(`backend/CLAUDE.md` 참고). 커밋 메시지 안의 코드 식별자, 파일 경로, 기술 용어는 평소대로 영문을 유지하고, 문장만 한글로 쓴다.
