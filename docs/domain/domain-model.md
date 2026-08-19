# 도메인 모델

## 설계 원칙

- 도메인은 Spring과 jOOQ에 의존하지 않는다.
- Aggregate 간에는 ID로 참조한다.
- 상태 변경은 도메인 메서드로 수행한다.
- 중요한 원시값은 Value Object로 정의한다.
- 외부 시스템은 Port 뒤에 둔다.

## Aggregate Root

### Merchant
가맹점 식별, 상태, 결제 가능 여부, Webhook 설정을 관리한다.

### Payment
주문 금액, USDC 결제 금액, 네트워크, 수취 지갑, 상태, 만료와 완료를 관리한다.

대표 행위:

```kotlin
fun ready(changedAt: Instant)
fun submit(wallet: WalletAddress, submittedAt: Instant)
fun startConfirmation(changedAt: Instant)
fun succeed(paidAt: Instant)
fun expire(expiredAt: Instant)
fun fail(reason: PaymentFailureReason, failedAt: Instant)
```

### CheckoutSession
세션 유효성, 화면 진행 상태, 연결 지갑, Redirect URL을 관리한다.

### BlockchainTransaction
Transaction Hash, 온체인 상세 정보, Confirm 수, 성공과 실패를 관리한다.

### ExchangeOrder
USDC 매도 요청, 거래소 주문번호, 체결 수량, 체결 환율, 확보 KRW를 관리한다.

### SettlementReceivable
결제 단위 정산 기준 금액, 수수료, 조정 금액, 정산 예정 금액과 정산 가능 상태를 관리한다.

### WebhookDelivery
전송 URL, Payload, 시도 횟수, 재시도 일정, 최종 상태를 관리한다.

### PaymentQuote
`Payment`에 1:1로 붙는 **불변 견적 스냅샷**이다 — 시장 환율, 적용 환율, 스프레드, 주문 금액,
USDC 결제 금액, 유효 기간을 확정 시점 그대로 고정한다.

상태도 전이 메서드도 없어서 다른 Aggregate와 달리 **공개 생성자를 가진 `data class`**다
(`payment_quote` 테이블에도 `updated_at`/`version`이 없다).

### PaymentCustomer
`Payment`에 1:1로 붙는 **구매자 정보**(이름·이메일·휴대전화)다. 결제를 읽는 모든 경로가 개인정보를
함께 끌고 오지 않도록, 그리고 보관 기간이 지나면 **이 행만 지워 파기**할 수 있도록 `payment`가 아닌
별도 테이블에 둔다 — 결제 기록과 개인정보는 수명이 다르다(ADR-008).

**이 Aggregate는 언제나 평문만 안다.** 암호화·복호화·Blind Index 계산은 전부 어댑터 바깥(Use Case
경계)에서 일어난다. 다만 **마스킹은 여기가 갖는다**(각 Value Object의 `masked`) — 마스킹은 표현
형식이 아니라 업무 규칙이라 두 콘솔이 각자 구현하면 규칙이 갈린다.

상태는 없지만 `PaymentQuote`와 달리 불변이 아니다 — 고객이 오타를 냈을 때 고칠 수 있어야 해서
`change`가 있고 `updated_at`/`version`도 갖는다.

### CustomerPiiAccessAudit
마스킹되지 않은 구매자 원본을 **누가·언제·어느 결제에서 봤는지** 남기는 append-only 기록이다.
**읽기만 하는 동작에 감사를 붙인 유일한 자료**로, 상태를 바꾸지 않아도 "봤다"는 사실 자체가
사건이기 때문이다(ADR-008). 열람한 값 자체는 남기지 않는다 — 남기면 파기가 반쪽이 된다.

### OutboxEvent
결제 완료 같은 도메인 사실을 **같은 트랜잭션에서 함께 기록**해 두고, 발행 Worker가 나중에
Webhook으로 내보내게 하는 Transactional Outbox다. 발행 상태와 재시도 일정을 관리한다.

### InternalLoginAudit / MerchantLoginAudit
내부 운영자·가맹점 관리자의 로그인 시도를 남기는 **append-only 감사 기록**이다(성공·실패·잠김,
없는 계정으로의 시도 포함, 클라이언트 IP). `PaymentQuote`와 같은 이유로 상태 전이가 없는
`data class`다. 결과 값(`LoginOutcome`)은 두 영역이 공유한다.

## 주요 Value Object

- `PaymentId`
- `MerchantId`
- `MerchantOrderId`
- `Money` — KRW(Minor Unit 없이 원 단위 `BIGINT`)
- `SignedMoney` — 음수를 허용해야 하는 KRW(정산 조정액, 환전 손익)
- `TokenAmount` — USDC Minor Unit
- `ExchangeRate`
- `WalletAddress`
- `TransactionHash`
- `ContractAddress`
- `ChainId`
- `BlockchainNetwork`
- `Asset`
- `HttpUrl` — Webhook/Redirect URL
- `CustomerName`/`CustomerEmail`/`CustomerPhone` — 구매자 정보. 각자 `masked`를 갖고, 이메일·휴대전화는
  Blind Index의 기준이 되는 `normalized`도 갖는다

계정 영역은 `LoginId`/`Email`/`AccountStatus`를 내부 운영자와 가맹점 사용자가 **공유**하고,
역할(`InternalUserRole`/`MerchantUserRole`)은 값이 달라 공유하지 않는다.

## Domain Service

### PaymentTransactionValidator
Network, Chain ID, Contract, Wallet, Amount, Receipt를 검증한다.

**물리적으로는 `modules:application`에 있다** — 검증 대상인 `OnChainTransaction`이
`BlockchainClient` Port의 반환 타입이라 `modules:domain`에 둘 수 없다(의존 방향은
`application → domain`으로만 흐른다). 부수효과 없는 순수 함수라는 성격은 그대로다.
Confirm 충족 여부와 Transaction Hash 중복은 여기서 보지 않는다 — 이유는 그 클래스의 KDoc 참고.

> **금액 계산에는 별도 도메인 서비스를 두지 않았다.** KRW→USDC 환산은 `CreatePaymentUseCase`가,
> 정산 금액(Gross/Fee/Adjustment/Net)은 `SellToFakeExchangeUseCase`가 계산한다 — 계산식이
> 한 Use Case에서만 쓰여서 서비스로 분리할 이유가 없었다. 대신 **`SettlementReceivable`이
> `net = gross - fee + adjustment` 공식을 `require`로 직접 검증**해, 계산 주체가 어디든 잘못된
> 금액 조합으로는 애그리게이트를 만들 수 없게 한다. 두 번째 호출부가 생기면 그때 서비스로 뽑는다.

# 계정 및 API 연동 Aggregate

## InternalUser

책임:

- 내부 운영자 로그인 식별
- 내부 역할과 계정 상태
- 로그인 실패와 잠금
- 내부 계정 발급자 감사 정보

핵심 정책:

- 최초 SUPER_ADMIN은 Bootstrap으로 생성한다.
- SUPER_ADMIN만 내부 사용자 계정을 발급한다.
- 종료된 계정은 재활성화하지 않는다.

## MerchantUser

책임:

- 가맹점 관리자 로그인 식별
- 소속 Merchant
- 가맹점 역할과 계정 상태
- 하위 계정 초대 및 발급
- API Key 발급·폐기 권한 판단

핵심 정책:

- 가맹점 등록 시 최초 OWNER를 생성한다.
- OWNER 또는 ADMIN은 같은 가맹점의 하위 사용자만 생성한다.
- ADMIN은 OWNER를 생성하거나 OWNER 권한을 변경할 수 없다.
- 최소 하나의 활성 OWNER를 유지한다.

## AccountInvitation

책임:

- 초대 대상 계정 연결
- 1회성 Token Hash 관리
- 만료와 수락 상태 관리

## MerchantApiKey

책임:

- Merchant 소유의 서버 간 인증 Key
- TEST/LIVE 환경 구분
- Secret Hash 검증 정보
- 발급·폐기와 마지막 사용 시각
- API 호출 Scope

핵심 정책:

- 사용자 계정이 아니라 Merchant에 귀속한다.
- Key 원문은 최초 한 번만 표시한다.
- 폐기된 Key는 재활성화하지 않는다.
- 가맹점당 복수 Key를 허용한다.
