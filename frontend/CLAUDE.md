# CLAUDE.md (frontend)

`frontend/`에서 작업할 때의 지침이다. 공통 결제 도메인(애그리게이트, 상태 머신, MVP 범위)은 루트 `../CLAUDE.md`를 참고한다 — 이 문서는 프론트엔드 구현 컨벤션만 다룬다.

## 현재 구현 상태

아직 여기에 프론트엔드 프로젝트가 스캐폴딩되지 않았다 — `frontend/`는 비어 있다. 아직 프레임워크도, 빌드 도구도, 실행할 명령어도 없다.

스캐폴딩을 시작할 때 참고할 만한 것: 이 프론트엔드가 고객용 Hosted Checkout 흐름을 담당하므로(`docs/architecture/mvp-scope.md`, `docs/decisions/ADR-003-hosted-checkout.md` 참고) `CheckoutSession` 상태(`CREATED → OPEN → WALLET_CONNECTED → PAYMENT_SUBMITTED → COMPLETED`, `PAYMENT_SUBMITTED` 이후 취소 불가)와 `Payment` 상태가 UI의 화면 진행을 이끈다 — 전체 상태 표는 루트 `../CLAUDE.md`를 참고한다. 금액은 백엔드의 `BIGINT` 타입에 맞춰 항상 정수로 다뤄야 한다(KRW는 원 단위, USDC는 Minor Unit) — 금액에 부동소수점을 절대 쓰지 않는다.

## 명령어

아직 없다. 여기에 프로젝트가 스캐폴딩되면 빌드/개발/테스트/Lint 명령어를 이 섹션에 기록한다.
