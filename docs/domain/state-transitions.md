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
| `CONFIRMED` | 확정된 입금이 사라짐(사실상 일어나지 않는다) | **자동으로 판단하지 않는다** — 내부 운영자가 실행하면 `REORGED` + 정산 채권 `HELD`(아래) |

- **즉시 전이하지 않고 유예를 둔다**(MVP 10분). 뒤처진 RPC 노드도 "없음"을 돌려줄 수
  있고 reorg된 거래가 다음 블록에 곧바로 다시 들어가는 것이 오히려 흔한데, `Payment`의
  `FAILED`는 종료 상태라 잘못된 판정을 되돌릴 수 없다.
- **`TRANSACTION_REORGED`만은 "돈이 오지 않았다"가 실제로 맞다** — 전송 자체가 체인에서
  없어졌으므로 수취 지갑에 남은 것이 없다([ADR-007](../decisions/ADR-007-onchain-irreversibility.md)의
  자금 위치 분류에서 "고객 지갑" 쪽이다).
#### `CONFIRMED` 이후의 reorg — 되돌리지 않고 정산을 막는다

```text
CONFIRMED → REORGED   (내부 운영자가 명시적으로 실행할 때만)
```

그 시점에는 `Payment = SUCCEEDED`이고 `ExchangeOrder`·`SettlementReceivable`까지 만들어져
있다. **그것들을 되돌리지 않는다** — 대신 딸린 정산 채권을 `READY → HELD`로 막는다.

- **되돌리지 않는 이유**: 그때 12 Confirm을 확인하고 승인했고, 그 승인에 근거해 매도했다.
  상태를 뒤늦게 `FAILED`로 바꾸면 그 이력이 사라져 매도가 왜 일어났는지 설명할 수 없게 된다.
  목적은 "성공을 지우는 것"이 아니라 **돈이 나가지 않게 하는 것**이고, 그건 `HELD`로 달성된다
  (MVP의 종착점이 `SettlementReceivable = READY`이므로 그 앞을 막으면 손실 경로가 닫힌다).
- **대가**: 결제 목록에는 여전히 "결제 완료"로 남는다. 진실은 결제 상세가 말한다(온체인 거래
  `REORGED` + 정산 `HELD`). 자세한 근거와 완화책은
  [ADR-007](../decisions/ADR-007-onchain-irreversibility.md)의 같은 절에 있다.
- **자동으로 판단하지 않는다.** 확정된 거래를 계속 재조회하면 RPC 비용만 쌓인다 — 12 Confirm
  이후의 reorg는 사실상 일어나지 않고(그것이 12를 요구하는 이유다), 드물게 벌어지면 사람이
  탐색기·알림으로 알게 된다.
- `Payment`/`ExchangeOrder`의 종료 상태는 **재사용하지 않는다** — 그 예외는 Webhook 재전송
  하나로 좁게 정해 두었다(아래 "수동 재전송").

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
(PENDING | READY) → HELD
HELD → (PENDING | READY)
(PENDING | READY | HELD) → CANCELLED
```

향후:

```text
READY → ASSIGNED → SETTLED
```

**`HELD`는 "지급을 막는다"는 뜻이고 `hold_reason_code`에 사유를 남긴다.** MVP에서 실제로
쓰이는 사유는 확정 이후 reorg다(위 `BlockchainTransaction` 절) — 결제는 성공으로 남지만
그 돈이 실제로는 없으므로 정산을 막는다. `CANCELLED`가 종료 상태다.

### `HELD`에서 돌아가는 곳은 저장된 값이 정한다

**보류를 풀 때 "어느 상태로 돌아갈지"를 기억해 두지 않는다** — `exchange_order_seq`가
이미 답을 갖고 있기 때문이다:

| 조건 | 돌아가는 상태 |
|---|---|
| `exchange_order_seq`가 있다(매도가 끝났다) | `READY` |
| 없다 | `PENDING` |

`READY`는 **"매도가 확정돼 정산할 금액이 정해졌다"**는 뜻이고, 그 근거인 `ExchangeOrder`
참조가 없으면 성립하지 않는다(`SettlementReceivable`이 `require`로 직접 막는다). 그래서
직전 상태를 따로 저장하면 그 값과 `exchange_order_seq`가 어긋날 수 있는 자리만 하나 더
생긴다 — **모순이 가능한 필드를 두느니 파생하는 쪽**을 골랐다.

### 보류·해제·취소는 감사 기록을 남긴다

세 전이는 전부 사람이 판단해 실행하고 **가맹점에게 나갈 돈을 좌우한다.** 그래서
`settlement_hold_audit`에 누가·언제·무엇을·왜 했는지 append-only로 남긴다
(`internal_login_audit`과 같은 성격이다).

`hold_reason_code`는 **"지금 왜 막혀 있나"**에만 답하는 현재 상태 필드라, 풀면 `NULL`로
지워져 보류됐던 사실 자체가 사라진다 — 로그인 시도보다 결과가 무거운 행위인데 흔적이 남지
않는 것은 균형이 맞지 않는다. 상태 필드와 이력을 나눈 이유다.

## WebhookDelivery

```text
PENDING → DELIVERING → SUCCEEDED
```

실패 시 `RETRY_WAITING`을 거쳐 재전송하고 최대 횟수 초과 시 `FAILED` 처리한다.

### 수동 재전송 — 이 시스템에서 유일하게 종료 상태를 되돌리는 전이

```text
FAILED → PENDING   (내부 운영자가 명시적으로 실행할 때만)
```

**"종료 상태는 재사용하지 않는다"는 공통 규칙의 의도된 예외다.** 예외를 둔 이유:

- 자동 재시도가 소진돼 `FAILED`가 되는 원인은 대개 **가맹점 쪽 일시 장애**다. 원인이
  해소된 뒤 그 이벤트를 다시 보낼 방법이 없으면, 가맹점은 그 결제를 영영 통보받지 못한다.
- `webhook_delivery`에는 `UNIQUE (event_id, merchant_seq)`가 걸려 있어 **같은 이벤트로 새
  전송 행을 만들 수 없다**. 그 제약은 자동 재시도의 멱등성을 받치는 장치라 떼지 않는다.
  남는 길은 기존 행을 되돌리는 것뿐이다.

규칙이 막으려던 것은 **자동 흐름이 종료 상태를 슬그머니 되밟는 일**이므로, 이 전이는
그와 구분되게 만든다:

- **사람이 누를 때만** 일어난다. 폴링·재시도 같은 자동 경로에는 이 전이가 없다.
- `OutboxEvent`도 함께 `FAILED → PENDING`으로 되돌린다 — 그래야 기존 발행 Worker가
  평소와 **똑같은 경로로** 다시 보낸다(별도 전송 경로를 만들지 않는다).
- **`attempt_count`를 초기화하지 않는다.** 그 값은 "이 이벤트를 몇 번 시도했나"라는
  누적 사실이고, 0으로 되돌리면 이력이 지워진다. 그래서 재전송 한 번은 자동 재시도
  예산을 새로 주는 것이 아니라 **시도 한 번**을 뜻한다(그 한 번이 실패하면 다시 `FAILED`).

`SUCCEEDED`는 되돌리지 않는다 — 이미 전달된 것을 다시 보내는 것은 재전송이 아니라
중복 발송이고, 그건 가맹점의 멱등 처리에 기대야 할 일이지 운영자가 버튼으로 할 일이 아니다.

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
