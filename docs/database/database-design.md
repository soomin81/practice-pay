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
15. `internal_login_audit`

## 주요 Unique

결제 흐름:

- Payment: `merchant_seq + merchant_order_id`
- BlockchainTransaction: `network_code + transaction_hash`
- ExchangeOrder: `client_order_id`
- SettlementReceivable: `payment_seq`
- WebhookDelivery: `event_id + merchant_seq`
- OutboxEvent: `event_id`

계정·API Key:

- InternalUser: `login_id` (`email`도 별도로 유일)
- MerchantUser: `merchant_seq + login_id` (`merchant_seq + email`도 별도로 유일) — 가맹점 안에서만 유일하다
- AccountInvitation: `token_hash`
- MerchantApiKey: `key_prefix`
- InternalLoginAudit: `internal_login_audit_id`

## 주요 인덱스

- 결제 만료: `payment_status + expires_at`
- 로그인 감사 조회: `internal_login_audit.occurred_at`(최신순 최근 목록)
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

### InternalLoginAudit

```text
internal_login_audit_id (공개 ID, UNIQUE)
```

append-only 로그다(전이·수정 없음). `internal_user_seq`는 FK지만 **NULL 허용**이다 — 없는
`login_id`로의 로그인 시도는 대응 계정이 없어 NULL로 남기고 `attempted_login_id`만 기록한다.
`login_outcome`은 `SUCCESS`/`INVALID_CREDENTIALS`/`LOCKED` CHECK로 제한한다. `client_ip`는 요청
원격 주소(프록시 뒤 실제 IP는 MVP 범위 밖).

## 계정 생성 트랜잭션

가맹점 등록:

```text
Merchant INSERT
+ MerchantUser(OWNER, INVITED) INSERT
+ AccountInvitation INSERT
```

MVP 구현(`RegisterMerchantUseCase`, `backend/CLAUDE.md`의 "가맹점 등록 Use Case" 절)은 `OutboxEvent INSERT`를 포함하지 않는다 — 이 프로젝트에 이메일 등 초대 알림을 전달할 인프라가 없어서, 발급 계열 Use Case(`IssueInternalUserUseCase`도 동일)는 초대 Token 원문을 API 응답으로 직접 돌려주고 호출자가 수동으로 전달하는 방식을 택했다. 알림 발송 인프라가 생기면 이 경계에 `OutboxEvent`를 다시 추가한다.

API Key 발급:

```text
MerchantApiKey INSERT
+ MerchantApiKeyScope INSERT
+ 감사 이벤트 INSERT
```

API Key 원문은 DB 트랜잭션 외부에 저장하지 않으며 발급 응답에서 최초 한 번만 반환한다.
