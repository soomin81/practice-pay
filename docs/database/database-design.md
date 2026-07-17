# 데이터베이스 상세 설계

## 공통 기준

- MySQL 8.x / InnoDB / utf8mb4
- UTC / `DATETIME(6)`
- 내부 PK: `BIGINT AUTO_INCREMENT`
- 공개 ID: Prefix가 있는 문자열
- KRW: `BIGINT`
- USDC: Minor Unit `BIGINT`
- 환율: `DECIMAL(24,12)`
- 상태: `VARCHAR`
- 낙관적 잠금: `version BIGINT`
- 삭제: `ON DELETE RESTRICT`

## MVP 테이블

1. `merchant`
2. `payment`
3. `payment_quote`
4. `checkout_session`
5. `blockchain_transaction`
6. `exchange_order`
7. `settlement_receivable`
8. `webhook_delivery`
9. `outbox_event`
10. `internal_user`
11. `merchant_user`
12. `account_invitation`
13. `merchant_api_key`
14. `merchant_api_key_scope`

## 주요 Unique

- Payment: `merchant_seq + merchant_order_id`
- BlockchainTransaction: `network_code + transaction_hash`
- ExchangeOrder: `client_order_id`
- SettlementReceivable: `payment_seq`
- WebhookDelivery: `event_id + merchant_seq`
- OutboxEvent: `event_id`

## 주요 인덱스

- 결제 만료: `payment_status + expires_at`
- Confirm Worker: `transaction_status + updated_at`
- 정산 배치 확장: `receivable_status + eligible_date + merchant_seq`
- Webhook 재시도: `delivery_status + next_retry_at`
- Outbox 발행: `event_status + next_retry_at + created_at`

## 향후 확장

- `settlement_batch`
- `settlement`
- `settlement_detail`
- `payout`
- `settlement_adjustment`
- `settlement_hold`
- `refund`
- `refund_transaction`

Payment에 정산 또는 지급 상태를 추가하지 않는다.


## 계정 및 API Key 주요 제약조건

### InternalUser

```text
internal_user_id
login_id
email
```

### MerchantUser

```text
merchant_user_id
merchant_seq + login_id
merchant_seq + email
```

### AccountInvitation

```text
account_invitation_id
token_hash
```

### MerchantApiKey

```text
merchant_api_key_id
key_prefix
```

가맹점당 복수 API Key를 허용한다.

### MerchantApiKeyScope

```text
merchant_api_key_seq + scope_code
```

## 계정 생성 트랜잭션

가맹점 등록:

```text
Merchant INSERT
+ MerchantUser(OWNER, INVITED) INSERT
+ AccountInvitation INSERT
+ OutboxEvent INSERT
```

API Key 발급:

```text
MerchantApiKey INSERT
+ MerchantApiKeyScope INSERT
+ 감사 이벤트 INSERT
```

API Key 원문은 DB 트랜잭션 외부에 저장하지 않으며 발급 응답에서 최초 한 번만 반환한다.
