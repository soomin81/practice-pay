# CLAUDE.md (frontend)

`frontend/`에서 작업할 때의 지침이다. 공통 결제 도메인(애그리게이트, 상태 머신, MVP 범위)은 루트 `../CLAUDE.md`를 참고한다 — 이 문서는 프론트엔드 구현 컨벤션만 다룬다.

## 현재 구현 상태

아직 여기에 프론트엔드 프로젝트가 스캐폴딩되지 않았다 — `frontend/`는 비어 있다. 아직 프레임워크도, 빌드 도구도, 실행할 명령어도 없다.

스캐폴딩을 시작할 때 참고할 만한 것: 이 프론트엔드가 고객용 Hosted Checkout 흐름을 담당하므로(`docs/architecture/mvp-scope.md`, `docs/decisions/ADR-003-hosted-checkout.md` 참고) `CheckoutSession` 상태(`CREATED → OPEN → WALLET_CONNECTED → PAYMENT_SUBMITTED → COMPLETED`, `PAYMENT_SUBMITTED` 이후 취소 불가)와 `Payment` 상태가 UI의 화면 진행을 이끈다 — 전체 상태 표는 루트 `../CLAUDE.md`를 참고한다. 금액은 백엔드의 `BIGINT` 타입에 맞춰 항상 정수로 다뤄야 한다(KRW는 원 단위, USDC는 Minor Unit) — 금액에 부동소수점을 절대 쓰지 않는다.

## 호출할 API — `docs/architecture/checkout-api.md`가 기준이다

**백엔드 소스를 읽어서 API 형태를 추론하지 않는다.** 고객 브라우저가 호출하는 5개 엔드포인트의 경로·요청·응답·오류 코드는 전부 `docs/architecture/checkout-api.md`에 정의돼 있다.

- **아직 그 API는 구현되지 않았다** — 계약을 먼저 정하고 프론트와 백엔드가 각자 맞춰가는 순서다. 백엔드에서 `apps:api-checkout`(포트 8084)이 이걸 구현한다.
- **인증이 없다.** 고객은 계정이 없고 `checkoutSessionId`가 곧 자격이다 — 로그인 화면을 만들지 않는다.
- **`chainId`/`tokenContractAddress`/`receivingWallet`을 코드에 상수로 박지 않는다.** 전부 `GET /checkout/sessions/{id}` 응답에서 받아 쓴다(토큰을 Symbol로 판단하지 않는다는 도메인 규칙이 프론트에도 적용된다).
- **USDC 금액은 문자열로 온다** — Minor Unit 정수가 JavaScript `Number`의 안전 범위를 넘을 수 있어서다. `BigInt`나 문자열로 다루고 `Number`로 변환하지 않는다.
- 상태 전이의 의미는 API 문서가 아니라 `docs/domain/state-transitions.md`에 있다 — 화면 진행은 그쪽을 따른다.

API가 구현되고 나면 OpenAPI 스펙(Spring REST Docs 기반)을 생성해 타입까지 받아오는 것을 목표로 한다 — 그때 이 절을 갱신한다.

## 명령어

아직 없다. 여기에 프로젝트가 스캐폴딩되면 빌드/개발/테스트/Lint 명령어를 이 섹션에 기록한다.
