# Webhook 계약 — 가맹점 서버가 받는 알림

PG가 가맹점 서버로 보내는 서버 간 알림(Webhook)의 계약이다. **이 문서가 기준이고, 구현은 이 문서를 따른다** — `docs/architecture/checkout-api.md`와 같은 취급이다.

관련 문서: 이벤트를 만드는 흐름은 `docs/architecture/mvp-scope.md`, 전송 상태 머신은 `docs/domain/state-transitions.md`의 `WebhookDelivery`, 설정 화면은 `docs/architecture/merchant-console-api.md`.

## 1. 무엇을 언제 보내나

Webhook은 `outbox_event`에 쌓인 이벤트를 `apps:batch`의 발행 Worker(10초 주기)가 읽어 보낸다 — 결제 트랜잭션 안에서 직접 HTTP를 호출하지 않는다(Transactional Outbox).

| 이벤트 | 언제 |
|---|---|
| `payment.created` | 결제가 생성돼 결제 가능(`READY`) 상태가 됐을 때 |
| `payment.succeeded` | 온체인 전송이 확정돼 `Payment`가 `SUCCEEDED`가 됐을 때 |
| `payment.settled` | 환전이 끝나 `SettlementReceivable`이 `READY`가 됐을 때 |

**가맹점이 수신 URL을 설정하지 않았으면 아무것도 보내지 않는다** — 전송 레코드(`webhook_delivery`)조차 만들지 않는다. 이것은 오류가 아니라 정상적인 상태다.

## 2. 요청 형태

```
POST {가맹점이 설정한 수신 URL}
Content-Type: application/json
X-PracticePay-Signature: t=1785974400,v1=9f86d081884c7d65...

{"status":"READY","paymentId":"pay_...","merchantOrderId":"order-001","checkoutSessionId":"cs_..."}
```

**2xx면 성공, 그 밖의 응답이나 연결 실패는 실패다.** 실패하면 1분 간격으로 최대 5회까지 재시도하고, 소진하면 `FAILED`로 끝낸다(값의 근거는 `PublishOutboxEventUseCase`).

**같은 이벤트가 두 번 이상 도착할 수 있다** — 가맹점이 2xx를 돌려줬는데 그 응답이 유실되면 PG는 실패로 보고 다시 보낸다. 수신 측은 `paymentId`(또는 `merchantOrderId`)를 기준으로 **멱등하게** 처리해야 한다.

## 3. 서명 — 반드시 검증한다

### 3.1 왜 필요한가

서명을 검증하지 않으면 **수신 URL을 아는 누구나 `payment.succeeded`를 위조해 보낼 수 있다.** 그것을 믿은 가맹점은 받지도 않은 돈에 대해 상품을 내보내게 되므로, 손해는 PG가 아니라 **가맹점에게** 생긴다.

### 3.2 형식

```
X-PracticePay-Signature: t={유닉스 초},v1={HMAC-SHA256을 소문자 HEX로}
```

- `t` — PG가 **이 요청을 보낸 시각**(유닉스 초). 재시도할 때마다 새로 찍힌다.
- `v1` — 서명 형식 버전. 나중에 알고리즘을 바꿔야 하면 `v2`를 **함께** 실어 보내 옮겨갈 시간을 준다(지금은 `v1` 하나만 보낸다). 그래서 파싱할 때 **모르는 항목은 무시**해야 한다.

**서명 대상은 본문만이 아니라 `"{t}.{본문}"`이다.** 본문만 서명하면 그 서명이 영원히 유효해서, 가로챈 요청을 그대로 다시 보내는 **재전송 공격**을 구분할 수 없다.

### 3.3 가맹점 측 검증 절차

```
1. X-PracticePay-Signature에서 t와 v1을 꺼낸다.
2. 지금 시각과 t의 차이가 허용 범위(예: 5분)를 넘으면 거부한다.   ← 재전송 방지
3. expected = HEX(HMAC_SHA256(서명 비밀, t + "." + 원본 본문))
4. expected와 v1을 **상수 시간 비교**로 대조한다.               ← 타이밍 공격 방지
5. 일치하지 않으면 거부한다.
```

주의할 점 셋:

- **원본 본문 바이트 그대로 서명한다.** JSON을 파싱했다가 다시 직렬화하면 키 순서·공백이 달라져 서명이 맞지 않는다.
- **2번(시각 확인)을 생략하면 서명이 있어도 재전송을 막지 못한다.** 서명 자체는 여전히 유효하기 때문이다.
- **4번을 `==` 문자열 비교로 하지 않는다** — 앞자리부터 순차 비교하면 소요 시간으로 정답을 한 글자씩 좁힐 수 있다.

## 4. 서명 비밀

### 4.1 어디서 얻나

가맹점 콘솔의 **Webhook** 화면(`GET /merchant/webhook`)에서 본다. `whsec_`로 시작한다.

**API Key와 달리 몇 번이든 다시 볼 수 있다.** API Key 원문은 발급 응답에서 한 번만 주지만(`docs/architecture/persistence-jooq.md`의 "인증 정보 저장 규칙"), 서명 비밀은 그 규칙의 대상이 아니다 — 이유는 4.2.

대신 **읽을 수 있는 사람을 좁혔다**: 이 경로는 `OWNER`/`ADMIN` 전용이고 `VIEWER`는 조회조차 못 한다. 응답 자체가 자격증명이라 `GET`도 함께 막는 것이 요점이다.

### 4.2 PG는 이 비밀을 저장하지 않는다

이 시스템의 다른 자격증명(비밀번호, 초대 Token, API Key)은 전부 **검증만** 하면 되므로 Hash로 저장한다. 서명 비밀은 PG가 **직접 서명하는 데 써야** 해서 원문을 되찾을 수 있어야 하고, Hash로는 그럴 수 없다.

그렇다고 평문 컬럼에 두면 **DB 유출 하나가 곧 전 가맹점 Webhook 위조**가 된다. 그래서 저장하지 않고 서버 Pepper로부터 파생한다:

```
비밀 = "whsec_" + base64url(HMAC-SHA256(서버 Pepper, "{merchantId}:{세대}"))
```

**DB에는 비밀이 아예 없다.** 통째로 유출돼도 Pepper 없이는 아무것도 위조할 수 없고, 저장되는 `merchant.webhook_secret_version`은 세대 번호일 뿐이라 무해하다.

### 4.3 교체

콘솔의 **비밀 교체**(`POST /merchant/webhook/rotate-secret`)가 세대를 1 올린다. 파생 입력이 달라지므로 **이전 비밀은 즉시 무효**가 된다.

**되돌릴 수 없고, 겹치는 기간도 없다.** 새 비밀을 가맹점 서버에 반영하기 전까지 그 사이의 Webhook은 서명 불일치로 거부된다. 그래서 화면은 이 동작을 확인 절차 뒤에 둔다.

> **후속 범위**: 실무의 결제 게이트웨이는 대개 옛 비밀과 새 비밀을 한동안 함께 유효하게 두어(헤더에 `v1=현재,v1=직전`처럼 두 서명을 실어) 이 공백을 없앤다. MVP는 세대를 하나만 들고 있어서 "지금 유효한 비밀은 언제나 정확히 하나"다. 필요해지면 직전 세대를 함께 보관하는 방향으로 넓힌다.

## 5. 알려진 한계

- **본문에 담기는 필드가 이벤트별로 다르고, 아직 계약으로 고정돼 있지 않다** — 지금은 `OutboxEvent.payload`를 그대로 보낸다. 가맹점은 `paymentId`로 조회 API를 다시 부르는 쪽이 안전하다.
- **전송 실패는 콘솔의 결제 상세에서만 보인다** — 실패한 전송을 다시 보내는 화면은 없다.
- **IP Allowlist가 없다** — 서명이 유일한 진위 확인 수단이다.
