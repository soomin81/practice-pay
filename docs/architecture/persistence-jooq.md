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
