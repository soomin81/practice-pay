# Hosted Checkout API 설계

## 1. 목적

고객 브라우저가 호출하는 **Hosted Checkout API**의 계약을 정한다(ADR-003).
`frontend/`가 이 계약에만 의존해 체크아웃 화면을 만들 수 있도록, 백엔드 소스를 읽지
않아도 되는 수준까지 명시하는 것이 목적이다.

이 문서는 **구현보다 먼저 작성됐다.** 대상 Use Case 중 둘(`ConnectCheckoutWalletUseCase`,
`SubmitPaymentTransactionUseCase`)은 이미 `modules:application`에 있지만 어떤 앱에도
배선되지 않았고, 조회용 Use Case는 아직 없다. 즉 여기 적힌 경로·응답은 현재 코드에서
추출한 것이 아니라 **앞으로 구현할 계약**이다.

## 2. 다른 API와의 경계

| API | 호출자 | 인증 |
|---|---|---|
| `api-payment` `POST /api/v1/payments` | 가맹점 **서버** | `MerchantApiKey` Bearer |
| `api-admin` | PG 내부 운영자 브라우저 | 세션 쿠키 |
| `api-merchant` | 가맹점 운영자 브라우저 | 세션 쿠키 |
| **Checkout API(이 문서)** | **고객 브라우저** | **없음 — `checkoutSessionId`가 곧 자격** |

고객은 계정이 없다. 가맹점 서버가 `POST /api/v1/payments`로 결제를 만들면
`checkoutSessionId`를 받고, 그 ID가 담긴 체크아웃 URL로 고객을 보낸다. 그 이후 고객은
**로그인 없이** 이 API를 호출한다.

### 2.1 배포 단위 — 새 앱 `apps:api-checkout`

기존 세 앱에 얹지 않고 새 앱을 만든다(포트 `8084`).

- **`backend/CLAUDE.md`가 세운 기준("앱 하나 = 상대하는 대상 하나")을 그대로 따른 결과다.**
  가맹점 서버·내부 운영자·가맹점 운영자에 이어 **고객**이 네 번째 대상이다.
- **인증 모델이 근본적으로 다르다.** `api-payment`는 `STATELESS` + Bearer, `api-admin`/
  `api-merchant`는 세션 쿠키인데, 이 API는 자격증명이 없다. 한 앱의 `SecurityConfig`에
  서로 다른 인증 모델을 섞으면 규칙 순서에 따라 조용히 어긋난다 — 실제로 이 저장소는
  `/error` 경로에서 그 종류의 사고를 한 번 겪었다(`backend/CLAUDE.md`의 "테스트가 잡지
  못하는 층").
- 고객 대면이라 **트래픽 성격과 공개 노출 범위가 다르다.** 독립적으로 스케일·장애
  격리되어야 할 가능성이 가장 높은 표면이다.

## 3. 인증과 접근 통제

**`checkoutSessionId`가 인증 수단(capability token)이다.** 알고 있다는 사실 자체가
권한이므로 다음을 지킨다.

- **추측 불가능해야 한다** — `UuidIdGenerator`(UUID v4) 기반 ID를 그대로 쓴다. 순번·
  짧은 코드를 쓰지 않는다.
- **모든 엔드포인트는 경로의 세션 하나에만 작용한다.** 목록 조회 엔드포인트를 만들지
  않는다(하나를 알아도 다른 세션을 열거할 수 없어야 한다).
- **존재하지 않는 세션과 접근 권한 없는 세션을 구분해 노출하지 않는다** — 둘 다 `404`다
  (`RevokeMerchantApiKeyUseCase`가 다른 가맹점 Key를 `404`로 가리는 것과 같은 이유).
- **종료 상태(`COMPLETED`/`EXPIRED`/`CANCELLED`) 세션도 조회는 허용한다** — 완료·만료
  화면을 그려야 하기 때문이다. 다만 변경 엔드포인트는 `409`로 막는다.
- CSRF: 세션 쿠키를 쓰지 않으므로 해당 없다(`api-payment`와 같은 근거).

**알려진 gap**: URL이 유출되면(어깨너머, 브라우저 히스토리, Referer) 제3자가 결제
화면에 접근할 수 있다. MVP는 짧은 만료(`CheckoutSession.expiresAt`)로만 완화한다 —
일회용 토큰 교환이나 가맹점 측 재확인은 후속 범위다.

## 4. 엔드포인트

기준 경로는 `/checkout`이다. 응답은 모두 `application/json`.

| 메서드 | 경로 | 용도 |
|---|---|---|
| `GET` | `/checkout/sessions/{checkoutSessionId}` | 체크아웃 화면 렌더용 전체 정보 |
| `GET` | `/checkout/sessions/{checkoutSessionId}/status` | 상태 폴링(경량) |
| `POST` | `/checkout/sessions/{checkoutSessionId}/wallet` | 고객 지갑 연결 |
| `POST` | `/checkout/sessions/{checkoutSessionId}/transaction` | 전송한 Transaction Hash 제출 |
| `POST` | `/checkout/sessions/{checkoutSessionId}/cancel` | 고객 취소 |

### 4.1 `GET /checkout/sessions/{checkoutSessionId}`

체크아웃 페이지를 그리는 데 필요한 모든 것을 한 번에 준다.

```json
{
  "checkoutSessionId": "cs_9f2c1a...",
  "checkoutSessionStatus": "CREATED",
  "expiresAt": "2026-07-19T10:30:00Z",
  "successUrl": "https://merchant.example.com/order/1001/done",
  "cancelUrl": "https://merchant.example.com/order/1001/cancel",
  "connectedWallet": null,
  "order": {
    "orderName": "테스트 상품",
    "orderAmount": 50000,
    "orderCurrency": "KRW"
  },
  "payment": {
    "paymentId": "pay_3b81...",
    "paymentStatus": "READY",
    "asset": "USDC",
    "amount": "72992701",
    "tokenDecimals": 6,
    "network": "BASE_SEPOLIA",
    "chainId": 84532,
    "tokenContractAddress": "0x036CbD53842c5426634e7929541eC2318f3dCF7e",
    "receivingWallet": "0xAbC1...",
    "requiredConfirmationCount": 12
  },
  "quote": {
    "appliedRate": "1370.250000000000",
    "quotedAt": "2026-07-19T10:00:00Z",
    "expiresAt": "2026-07-19T10:30:00Z"
  }
}
```

- **`amount`는 Minor Unit 정수를 문자열로 준다.** `72992701` = `72.992701 USDC`.
  문자열인 이유는 JavaScript `Number`가 안전하게 다루는 정수 범위를 토큰 금액이
  넘을 수 있어서다 — 18-decimals 토큰에서 실제로 `Long` 범위를 넘겨 터진 사례가 이미
  있다(`backend/CLAUDE.md`의 "테스트가 잡지 못하는 층"). `orderAmount`(KRW)는 원 단위
  정수라 안전해서 숫자로 준다.
- **`chainId`/`tokenContractAddress`/`receivingWallet`/`requiredConfirmationCount`는
  반드시 이 응답에서 받아 쓴다.** 프론트가 상수로 박아두지 않는다 — 토큰을 Symbol로
  판단하지 않고 항상 (네트워크, Contract 주소) 조합으로 다룬다는 도메인 규칙
  (`docs/domain/domain-model.md`)이 프론트에도 그대로 적용된다. 출처는 백엔드의
  `PaymentNetworkConfig`다.
- **이 `GET`은 상태를 바꾸지 않는다.** `CheckoutSession.open()`(`CREATED → OPEN`)은
  호출하지 않는다 — 조회는 `GET`으로 남기고, 고객이 실제로 처음 행동하는 순간(지갑
  연결)에 `open()`을 함께 처리한다는 기존 판단을 따른다
  (`backend/IMPLEMENTATION-NOTES.md`의 "체크아웃 지갑 연결" 절).
- `cancelUrl`은 가맹점이 주지 않았으면 `null`이다.

### 4.2 `GET /checkout/sessions/{checkoutSessionId}/status`

`CONFIRMING` 구간에서 폴링하는 용도다. 전체 조회와 나누는 이유는, Base의 블록 주기
(~2초)에 필요 Confirm 수 12를 곱하면 **폴링이 10회 이상 반복**되는데 그때마다 견적·
주문 정보까지 다시 보낼 이유가 없어서다.

```json
{
  "checkoutSessionStatus": "PAYMENT_SUBMITTED",
  "paymentStatus": "CONFIRMING",
  "confirmationCount": 5,
  "requiredConfirmationCount": 12,
  "transactionHash": "0x7f3a...",
  "failureReason": null,
  "redirectUrl": null
}
```

- **`redirectUrl`은 `paymentStatus`가 `SUCCEEDED`가 됐을 때만 채워진다**(= `successUrl`).
  프론트가 리다이렉트 시점을 스스로 판단하지 않고 서버 신호를 따르게 하려는 것이다.
- `failureReason`은 `FAILED`일 때 `PaymentFailureReason` Enum 값이다. 고객에게 그대로
  노출하지 말고 프론트가 안내 문구로 번역한다.
- 권장 폴링 주기 **3초**, 최대 5분. 그 뒤로는 폴링을 멈추고 새로고침을 안내한다.

### 4.3 `POST /checkout/sessions/{checkoutSessionId}/wallet`

`ConnectCheckoutWalletUseCase`를 노출한다.

```json
{ "walletAddress": "0xCustomer..." }
```

응답:

```json
{
  "checkoutSessionId": "cs_9f2c1a...",
  "checkoutSessionStatus": "WALLET_CONNECTED",
  "connectedWallet": "0xCustomer..."
}
```

- `CREATED` 상태면 `open()`을 먼저 호출한 뒤 연결한다(Use Case가 이미 그렇게 한다).
- **지갑 재연결은 지원하지 않는다** — 이미 `WALLET_CONNECTED`면 `409`. 도메인에 재연결
  메서드가 없다(알려진 gap).

### 4.4 `POST /checkout/sessions/{checkoutSessionId}/transaction`

`SubmitPaymentTransactionUseCase`를 노출한다. 고객 지갑이 USDC 전송을 브로드캐스트한
뒤 그 Hash를 제출하는 자리다.

```json
{ "transactionHash": "0x7f3a..." }
```

응답:

```json
{
  "blockchainTransactionId": "btx_5c19...",
  "checkoutSessionId": "cs_9f2c1a...",
  "checkoutSessionStatus": "PAYMENT_SUBMITTED",
  "paymentId": "pay_3b81...",
  "paymentStatus": "PROCESSING"
}
```

- **같은 결제로 같은 Hash를 다시 제출하면 멱등하게 같은 결과를 준다**(재전송·중복 클릭
  대응). 다른 결제의 Hash면 `409`.
- 성공 응답은 "결제가 됐다"가 아니라 **"제출을 접수했다"**는 뜻이다. 이후 확정은
  `apps:batch`의 Confirm Worker가 처리하므로, 프론트는 여기서 곧바로 4.2 폴링으로
  넘어간다.

### 4.5 `POST /checkout/sessions/{checkoutSessionId}/cancel`

본문 없음. 응답은 `checkoutSessionStatus: "CANCELLED"`와 `redirectUrl`(= `cancelUrl`,
없으면 `null`).

- **`PAYMENT_SUBMITTED` 이후에는 취소할 수 없다** — `409`. 도메인의
  `CheckoutSession.isBeforePaymentSubmitted()`가 이 경계를 이미 갖고 있다.

## 5. 오류 응답

형식은 기존 세 앱의 `ErrorResponse`와 같게 맞춘다.

| 상태 | 언제 |
|---|---|
| `400` | 요청 본문 검증 실패, Value Object `require` 실패(예: 지갑 주소 형식) |
| `404` | 세션이 없거나 접근할 수 없음(둘을 구분하지 않는다) |
| `409` | 현재 상태에서 허용되지 않는 전이(지갑 재연결, 제출 후 취소, 다른 결제의 Hash) |
| `410` | 세션·견적 만료 |

**`410`은 이 API에서 처음 쓰는 상태 코드다.** 만료는 "잘못된 요청"이 아니라 "유효했지만
지금은 끝난 자원"이라 `409`와 구분해서 프론트가 만료 전용 화면을 그릴 수 있게 한다.

## 6. 프론트엔드가 따르는 흐름

```text
GET  /checkout/sessions/{id}                 화면 렌더 (금액·네트워크·수취 지갑)
  ↓ 고객이 지갑 연결
POST /checkout/sessions/{id}/wallet          → WALLET_CONNECTED
  ↓ 프론트가 ERC-20 transfer 트랜잭션 구성·전송 (지갑이 서명)
POST /checkout/sessions/{id}/transaction     → PAYMENT_SUBMITTED / PROCESSING
  ↓ 3초 간격 폴링
GET  /checkout/sessions/{id}/status          → CONFIRMING → SUCCEEDED
  ↓ redirectUrl 수신
successUrl로 이동
```

화면 전환을 이끄는 것은 이 API가 아니라 **상태 머신**이다 — 전체 전이와 조건은
`docs/domain/state-transitions.md`를 따른다. 프론트는 응답의 상태 값을 그대로 신뢰하고
자체적으로 다음 상태를 추론하지 않는다.

## 7. 구현에 필요한 것

이 계약을 만족시키려면 백엔드에 다음이 필요하다.

- **새 앱 `apps:api-checkout`**(포트 8084) — `practicepay.spring-web-app` 적용.
- **새 조회 Use Case**: 4.1/4.2를 위한 읽기 전용 Use Case. Command Repository가 아니라
  전용 Projection을 쓴다(`MerchantListProjection`이 세운 선례) — `CheckoutSession` +
  `Payment` + `PaymentQuote` + 최신 `BlockchainTransaction`을 한 번에 조인해야 해서
  Aggregate 복원으로는 맞지 않는다.
- **기존 Use Case 2개 배선**: `ConnectCheckoutWalletUseCase`,
  `SubmitPaymentTransactionUseCase` — 구현은 이미 있고 컨트롤러·Bean만 없다.
- **취소 Use Case**: `CheckoutSession.cancel()`을 호출하는 Use Case가 아직 없다.
- **`confirmationCount` 노출 경로**: 지금은 `BlockchainTransaction`이 들고 있고 Confirm
  Worker만 갱신한다. Projection이 이 값을 읽어야 한다.

## 8. 아직 정하지 않은 것

- **체크아웃 페이지 URL 자체**(`https://.../checkout/{id}` 같은 사용자 대면 경로)와 그
  페이지를 누가 서빙하는지 — `frontend/`가 스캐폴딩될 때 정한다.
- **`PaymentQuote` 만료 시 재견적** — 견적이 만료된 세션을 되살릴지, 결제를 새로
  만들게 할지. MVP는 `410`으로 끝낸다.
- **Webhook과의 순서 보장** — 고객이 `successUrl`에 도착하는 시점과 가맹점이 Webhook을
  받는 시점의 선후는 보장하지 않는다. 가맹점은 Webhook을 기준으로 삼아야 한다.
