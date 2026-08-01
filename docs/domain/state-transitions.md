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
- Amount 충족(`받은 금액 >= 결제 금액` — 초과분은 결제를 막지 않는다)
- Receipt 성공
- Required Confirmation 충족
- Transaction Hash 중복 아님

**`FAILED`가 "돈이 오지 않았다"를 뜻하지 않는다.** 온체인 전송은 되돌릴 수 없어서, 검증
실패 중 일부(금액 부족·허용되지 않은 토큰)는 **자금이 이미 PG 수취 지갑에 들어온 상태**다.
MVP는 이런 입금을 자동으로 반환하거나 정산에 반영하지 않고 수령 사실만
`blockchain_transaction`에 남긴다 — 판단 근거와 후속 계획은
[ADR-007](../decisions/ADR-007-onchain-irreversibility.md)에 있다.

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

# InternalUser 및 MerchantUser

## 상태

- `INVITED`
- `ACTIVE`
- `LOCKED`
- `SUSPENDED`
- `TERMINATED`

## 활성화

```text
INVITED
→ ACTIVE
```

조건:

- 유효한 초대
- 초대 만료 전
- 비밀번호 설정 완료

## 잠금과 해제

```text
ACTIVE
→ LOCKED
→ ACTIVE
```

## 운영 중지

```text
ACTIVE
→ SUSPENDED
→ ACTIVE
```

## 종료

```text
ACTIVE 또는 SUSPENDED
→ TERMINATED
```

`TERMINATED`는 종료 상태다.

# AccountInvitation

## 상태

- `PENDING`
- `ACCEPTED`
- `EXPIRED`
- `REVOKED`

정상 흐름:

```text
PENDING
→ ACCEPTED
```

예외:

```text
PENDING
→ EXPIRED

PENDING
→ REVOKED
```

# MerchantApiKey

## 상태

- `ACTIVE`
- `REVOKED`
- `EXPIRED`

정상 폐기:

```text
ACTIVE
→ REVOKED
```

만료:

```text
ACTIVE
→ EXPIRED
```

`REVOKED`, `EXPIRED`는 종료 상태다. 재사용하지 않고 새로운 Key를 발급한다.
