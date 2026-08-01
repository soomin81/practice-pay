# MySQL·jOOQ 영속성 설계

## 기술 기준

- MySQL 8.x
- jOOQ
- JPA/Hibernate 미사용

## 구조

```text
domain
application
adapter/outbound/persistence/jooq
generated-src/jooq
```

생성된 jOOQ Record는 Persistence Adapter 내부에서만 사용한다.

## Command와 Query

- Command Repository는 Aggregate를 저장하고 복원한다.
- 복잡한 조회는 전용 jOOQ Projection을 사용한다.

## 낙관적 잠금

주요 테이블에 다음 컬럼을 둔다.

```sql
version BIGINT NOT NULL DEFAULT 0
```

상태 UPDATE 시 식별자, 예상 현재 상태, 예상 version을 함께 조건으로 사용한다.

> **구현 노트**: 실제 구현은 "로드 시점의 예상 version"을 Adapter까지 전달하지 않는다 —
> 그러려면 도메인 애그리게이트가 `version`을 들고 다녀야 하는데, 영속성 관심사를 도메인에
> 두지 않기로 했기 때문이다. 대신 경합이 실제로 일어나는 애그리게이트
> (`Payment`/`CheckoutSession`/`MerchantApiKey`)는 **변경할 목적의 읽기에 행 잠금**을 건다
> (`SELECT ... FOR UPDATE`, 트랜잭션 안). `version` 컬럼과 UPDATE의 version 조건은 그대로
> 남겨 이중 안전장치로 쓴다. 자세한 판단은 `backend/CLAUDE.md`의 영속성 Adapter 컨벤션 참고.

## 타입 매핑

```text
KRW BIGINT ↔ Money
USDC Minor BIGINT ↔ TokenAmount
DECIMAL(24,12) ↔ BigDecimal
DATETIME(6) UTC ↔ 애플리케이션 시간 타입
VARCHAR Status ↔ Kotlin enum
```

## 트랜잭션 경계

결제 생성:

```text
Payment + PaymentQuote + CheckoutSession + OutboxEvent
```

결제 완료:

```text
BlockchainTransaction + Payment SUCCEEDED + OutboxEvent
```

환전 완료:

```text
ExchangeOrder COMPLETED + SettlementReceivable READY + OutboxEvent
```

## Code Generation

```text
Migration → MySQL Schema → jOOQ Code Generation → Compile
```

생성 코드를 직접 수정하지 않는다.

## 인증 정보 저장 규칙

- 비밀번호 원문을 저장하거나 로그에 기록하지 않는다.
- 초대 Token 원문을 저장하지 않고 Hash만 저장한다.
- API Key 원문은 최초 발급 응답에서 한 번만 반환한다.
- API Key는 `key_prefix`와 `secret_hash`로 저장한다.
- Authorization Header와 API Key Secret을 애플리케이션 로그에 남기지 않는다.
- API Key 검증은 Prefix 조회 후 Hash 비교 방식으로 구현한다.
- API Key 사용 시 Merchant 상태, Key 상태, 환경, Scope를 함께 확인한다.
- `last_used_at` 갱신은 인증 요청의 핵심 성공 여부를 방해하지 않도록 갱신 전략을 별도로 검토한다.
