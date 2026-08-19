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
16. `merchant_login_audit`
17. `settlement_hold_audit`
18. `payment_customer`
19. `customer_pii_access_audit`

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
- MerchantLoginAudit: `merchant_login_audit_id`
- SettlementHoldAudit: `settlement_hold_audit_id`
- PaymentCustomer: `payment_seq` (결제 1건당 1건)
- CustomerPiiAccessAudit: `customer_pii_access_audit_id`

## 주요 인덱스

- 결제 만료: `payment_status + expires_at`
- 로그인 감사 조회: `internal_login_audit.occurred_at`·`merchant_login_audit.occurred_at`(최신순 최근 목록)
- 정산 보류 이력 조회: `settlement_hold_audit.settlement_receivable_seq + occurred_at`(채권 한 건의 이력)
- 구매자 검색: `payment_customer.email_index`·`phone_index`(Blind Index 정확 일치 — ADR-008)
- 개인정보 열람 감사: `customer_pii_access_audit.occurred_at`(최신순), `+ payment_seq`(결제별 열람 이력)
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

### MerchantLoginAudit

```text
merchant_login_audit_id (공개 ID, UNIQUE)
```

`InternalLoginAudit`의 가맹점판이다. 가맹점 로그인은 merchant_code로 가맹점을 먼저 확정하므로,
`merchant_seq`(없는 merchantCode 시도면 NULL)와 `merchant_user_seq`(없는 loginId 시도면 NULL)
둘 다 FK이자 NULL 허용이고, `attempted_merchant_code`/`attempted_login_id`에 시도 원문을 남긴다.
기록은 api-merchant가, 조회는 api-admin(전 가맹점)이 한다.

### SettlementHoldAudit

```text
settlement_hold_audit_id (공개 ID, UNIQUE)
```

정산 채권의 **보류·해제·취소 이력**이다. 위 두 감사 테이블과 같은 append-only 구조이지만
남기는 대상이 로그인 시도가 아니라 **돈의 흐름을 좌우한 운영 행위**다.

- `hold_action`은 `HELD`/`RELEASED`/`CANCELLED` CHECK로 제한한다.
- `reason_code`는 `HELD`일 때의 사유(`settlement_receivable.hold_reason_code`에 들어간 값과
  같다). 해제·취소에는 없다.
- `note`는 사람이 남기는 자유 메모다. **해제·취소에는 필수**다 — 자동 경로가 없는 전이라
  "왜 풀었나"에 답할 수 있는 것은 실행한 사람뿐이다.
- `internal_user_seq`는 **NOT NULL**이다. 앞의 두 감사 테이블이 NULL을 허용한 것은 "없는
  계정으로의 시도"라는 대상이 있어서인데, 여기서는 인증된 내부 운영자만 실행할 수 있어
  주체가 없는 행이 존재할 수 없다.

`settlement_receivable.hold_reason_code`와 역할이 다르다 — 그쪽은 **"지금 왜 막혀 있나"**만
답하는 현재 상태 필드라 해제하면 지워진다. 이력은 여기에만 남는다
(`docs/domain/state-transitions.md`의 "보류·해제·취소는 감사 기록을 남긴다").

### PaymentCustomer

```text
payment_seq (결제 1건당 1건, UNIQUE)
```

구매자의 이름·이메일·휴대전화다. **`payment`에 컬럼을 더하지 않고 별도 테이블에 둔다**
(ADR-008): 결제 목록·정산·환전 워커가 결제를 읽을 때 개인정보를 함께 끌고 오지 않고, 보관
기간이 지나 파기할 때 **행만 지우면 결제 기록은 그대로 남는다**(둘의 수명이 다르다).

항목마다 컬럼이 셋이다:

| 컬럼 | 내용 |
|---|---|
| `*_encrypted` | AES-256-GCM 암호문. **값마다 랜덤 IV**를 앞에 붙여 Base64로 담는다 |
| `*_masked` | 화면·엑셀·로그가 읽는 값(`홍*동`, `ab***@example.com`, `010-****-1234`) |
| `email_index` / `phone_index` | `HMAC(pepper, 정규화된 값)`의 Blind Index. **이름에는 없다** |

- **마스킹된 값을 쓸 때 함께 저장한다** — 읽을 때 마스킹하면 호출을 빠뜨리는 실수 한 번이 곧
  원문 노출이지만, 이 구조에서는 원문을 보려면 복호화를 명시적으로 호출해야 한다.
- **Blind Index가 이름에 없는 이유**는 동명이인이 흔해 검색이 신뢰할 수 없고, 두면 "같은
  이름인지"가 드러나는 대가만 남기 때문이다(ADR-008).
- 암호문 컬럼에는 인덱스를 두지 않는다 — 랜덤 IV라 정렬·비교에 의미가 없다.

### CustomerPiiAccessAudit

```text
customer_pii_access_audit_id (공개 ID, UNIQUE)
```

**마스킹되지 않은 원본을 누가 언제 어느 결제에서 봤는지**를 남기는 append-only 로그다.
`internal_user_seq`와 `payment_seq` 둘 다 `NOT NULL`이다 — 인증된 `SUPER_ADMIN`이 특정 결제를
지목해야만 열람이 성립하므로 주체나 대상이 없는 행이 존재할 수 없다.

**읽기만 하는 동작에 감사를 붙인 유일한 자료다.** 상태를 바꾸지 않아도 "봤다"는 사실 자체가
사건이기 때문이다(ADR-008).

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
