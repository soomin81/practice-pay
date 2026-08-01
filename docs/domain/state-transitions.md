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

예외:

```text
SUBMITTED, DETECTED 또는 CONFIRMING → FAILED

DETECTED 또는 CONFIRMING → REORGED
```

### `REORGED` — 블록에 들어갔던 거래가 사라졌다

**조회 결과가 "없음"이라는 같은 사실이 상태에 따라 뜻이 다르다.**

| 현재 상태 | 온체인에서 못 찾음의 뜻 | 처리 |
|---|---|---|
| `SUBMITTED` | 아직 채굴되지 않음(정상 대기) | 상태를 바꾸지 않는다 |
| `DETECTED`, `CONFIRMING` | **블록에서 이미 본 거래가 사라짐** — 체인 재구성(reorg)이나 거래 교체 | 유예 후 `REORGED`, `Payment`는 `TRANSACTION_REORGED`로 `FAILED` |

- **즉시 전이하지 않고 유예를 둔다**(MVP 10분). 뒤처진 RPC 노드도 "없음"을 돌려줄 수
  있고 reorg된 거래가 다음 블록에 곧바로 다시 들어가는 것이 오히려 흔한데, `Payment`의
  `FAILED`는 종료 상태라 잘못된 판정을 되돌릴 수 없다.
- **`TRANSACTION_REORGED`만은 "돈이 오지 않았다"가 실제로 맞다** — 전송 자체가 체인에서
  없어졌으므로 수취 지갑에 남은 것이 없다([ADR-007](../decisions/ADR-007-onchain-irreversibility.md)의
  자금 위치 분류에서 "고객 지갑" 쪽이다).
- **`CONFIRMED` 이후의 reorg는 다루지 않는다.** 그 시점에는 `Payment = SUCCEEDED`이고
  `ExchangeOrder`·`SettlementReceivable`까지 만들어져 있어서, 되돌리려면 애그리게이트
  하나가 아니라 그 뒤의 개념들을 함께 뒤집는 보상 흐름이 필요하다 — ADR-007이 "확정
  이후에 사실이 바뀌는 상황"으로 함께 묶어 둔 후속 범위다. 필요 Confirm 수 12가 이
  구간의 유일한 완화책이다.

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
