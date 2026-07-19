# CLAUDE.md (frontend)

`frontend/`에서 작업할 때의 지침이다. 공통 결제 도메인(애그리게이트, 상태 머신, MVP 범위)은 루트 `../CLAUDE.md`를 참고한다 — 이 문서는 프론트엔드 구현 컨벤션만 다룬다.

## 현재 구현 상태

아직 여기에 프론트엔드 프로젝트가 스캐폴딩되지 않았다 — `frontend/`는 비어 있다. 아직 프레임워크도, 빌드 도구도, 실행할 명령어도 없다.

스캐폴딩을 시작할 때 참고할 만한 것: 이 프론트엔드가 고객용 Hosted Checkout 흐름을 담당하므로(`docs/architecture/mvp-scope.md`, `docs/decisions/ADR-003-hosted-checkout.md` 참고) `CheckoutSession` 상태(`CREATED → OPEN → WALLET_CONNECTED → PAYMENT_SUBMITTED → COMPLETED`, `PAYMENT_SUBMITTED` 이후 취소 불가)와 `Payment` 상태가 UI의 화면 진행을 이끈다 — 전체 상태 표는 루트 `../CLAUDE.md`를 참고한다. 금액은 백엔드의 `BIGINT` 타입에 맞춰 항상 정수로 다뤄야 한다(KRW는 원 단위, USDC는 Minor Unit) — 금액에 부동소수점을 절대 쓰지 않는다.

## 호출할 API — `docs/architecture/checkout-api.md`가 기준이다

**백엔드 소스를 읽어서 API 형태를 추론하지 않는다.** 고객 브라우저가 호출하는 5개 엔드포인트의 경로·요청·응답·오류 코드는 전부 `docs/architecture/checkout-api.md`에 정의돼 있다.

- **API는 구현돼 있다** — `apps:api-payment`(포트 **8081**)가 제공한다. 별도 체크아웃 앱은 만들지 않았다(그 문서 2.1). 로컬에서 `gradlew.bat :apps:api-payment:bootRun`으로 띄우고, 호출 예시는 `backend/apps/api-payment/requests.http`의 "Hosted Checkout API" 절에 전 흐름이 있다.
- **CORS는 `app.checkout.allowed-origins`에 등록된 Origin만 허용한다** — 기본값은 `http://localhost:3000`, `http://localhost:5173`이다. 다른 포트로 개발 서버를 띄우면 브라우저가 막으므로 그 설정(또는 `APP_CHECKOUT_ALLOWED_ORIGINS`)에 추가해야 한다.
- **인증이 없다.** 고객은 계정이 없고 `checkoutSessionId`가 곧 자격이다 — 로그인 화면을 만들지 않는다.
- **`chainId`/`tokenContractAddress`/`receivingWallet`을 코드에 상수로 박지 않는다.** 전부 `GET /checkout/sessions/{id}` 응답에서 받아 쓴다(토큰을 Symbol로 판단하지 않는다는 도메인 규칙이 프론트에도 적용된다).
- **USDC 금액은 문자열로 온다** — Minor Unit 정수가 JavaScript `Number`의 안전 범위를 넘을 수 있어서다. `BigInt`나 문자열로 다루고 `Number`로 변환하지 않는다.
- 상태 전이의 의미는 API 문서가 아니라 `docs/domain/state-transitions.md`에 있다 — 화면 진행은 그쪽을 따른다.

## 타입은 OpenAPI 스펙에서 생성한다

백엔드가 **통과한 테스트에서 OpenAPI 스펙을 생성**한다(Spring REST Docs 기반이라 실제 응답과 어긋날 수 없다).

```
cd backend && gradlew.bat :apps:api-payment:openapi3
# → backend/apps/api-payment/build/api-spec/openapi3.yaml
```

- **스펙은 저장소에 커밋돼 있지 않다**(생성물이라 `build/` 아래). 프론트 빌드에서 위 태스크를 먼저 돌리거나, 생성된 파일을 프론트 쪽으로 복사해 쓴다.
- `openapi-generator`(또는 `openapi-typescript`)로 타입을 뽑아 쓰는 것을 권한다 — 필드명·nullable 불일치가 컴파일 단계에서 잡힌다. **특히 `payment.amount`는 스펙에 `type: string`으로 선언돼 있다**(Minor Unit이 `Number` 안전 범위를 넘을 수 있어서다) — 타입 생성기를 쓰면 이걸 실수로 숫자로 다루는 것을 막아준다.
- **오류 응답(400/404/409/410)은 스펙에 없다.** `@WebMvcTest`의 MockMvc가 컨테이너 오류 디스패치를 재현하지 않아 잘못 문서화될 위험이 있어서 의도적으로 뺐다 — 오류 코드는 `docs/architecture/checkout-api.md`의 5절이 기준이고, 그쪽은 실제 `bootRun`으로 확인한 값이다.

## 명령어

아직 없다. 여기에 프로젝트가 스캐폴딩되면 빌드/개발/테스트/Lint 명령어를 이 섹션에 기록한다.
