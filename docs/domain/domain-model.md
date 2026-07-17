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

## 주요 Value Object

- `PaymentId`
- `MerchantId`
- `MerchantOrderId`
- `Money`
- `TokenAmount`
- `ExchangeRate`
- `WalletAddress`
- `TransactionHash`
- `ContractAddress`
- `BlockchainNetwork`
- `Asset`

## Domain Service

### PaymentAmountCalculator
KRW 주문 금액과 적용 환율로 USDC 수량을 계산한다.

### PaymentTransactionValidator
Network, Chain ID, Contract, Wallet, Amount, Receipt, Confirm, 중복 여부를 검증한다.

### SettlementAmountCalculator
Gross, Fee, Adjustment, Net Amount를 계산한다.
