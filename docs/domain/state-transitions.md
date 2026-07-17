# 상태 전이 정책

## 공통 원칙

- Aggregate별 상태를 독립적으로 관리한다.
- 상태 변경 전 현재 상태를 검증한다.
- Controller와 Repository가 상태를 직접 대입하지 않는다.
- 주요 전이는 도메인 이벤트를 생성한다.
- 종료 상태를 재사용하지 않는다.

## Payment

```text
CREATED → READY → PROCESSING → CONFIRMING → SUCCEEDED
```

예외:

```text
CREATED 또는 READY → EXPIRED
PROCESSING 또는 CONFIRMING → FAILED
```

`CONFIRMING → SUCCEEDED` 조건:

- Network 및 Chain ID 일치
- Token Contract 허용
- Receiving Wallet 일치
- Amount 충족
- Receipt 성공
- Required Confirmation 충족
- Transaction Hash 중복 아님

## CheckoutSession

```text
CREATED → OPEN → WALLET_CONNECTED → PAYMENT_SUBMITTED → COMPLETED
```

`PAYMENT_SUBMITTED` 이후 고객 취소를 허용하지 않는다.

## BlockchainTransaction

```text
SUBMITTED → DETECTED → CONFIRMING → CONFIRMED
```

예외 상태는 `FAILED`, 향후 예약 상태는 `REORGED`다.

## ExchangeOrder

운영 확장:

```text
REQUESTED → SUBMITTED → PROCESSING → COMPLETED
```

Fake Exchange MVP:

```text
REQUESTED → COMPLETED
```

## SettlementReceivable

MVP:

```text
PENDING → READY
```

향후:

```text
READY → ASSIGNED → SETTLED
```

## WebhookDelivery

```text
PENDING → DELIVERING → SUCCEEDED
```

실패 시 `RETRY_WAITING`을 거쳐 재전송하고 최대 횟수 초과 시 `FAILED` 처리한다.
