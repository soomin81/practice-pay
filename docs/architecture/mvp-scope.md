# MVP 범위

## 지원 기준

| 항목 | MVP |
|---|---|
| 네트워크 | Base Sepolia |
| 환경 | Testnet |
| 결제 자산 | USDC |
| 주문 통화 | KRW |
| 정산 통화 | KRW |
| 고객 지갑 | 외부 EVM 지갑 |
| 수취 지갑 | PG 수탁 — 네트워크당 공용 1개 |
| 체크아웃 | PG Hosted Checkout |
| 거래소 | Fake Exchange |
| AML/KYT | 제외 |
| 정산 배치 | 제외 |
| 실제 원화 지급 | 제외 |

## 전체 흐름

```text
Payment 생성
→ PaymentQuote 확정
→ CheckoutSession 생성
→ 고객 지갑 연결
→ USDC 전송
→ BlockchainTransaction 감지 및 Confirm
→ Payment SUCCEEDED
→ 결제 완료 페이지와 Webhook
→ Fake Exchange 매도
→ SettlementReceivable READY
```

## 계정 및 API 연동 MVP

포함:

- 내부 SUPER_ADMIN Bootstrap
- SUPER_ADMIN의 내부 운영자 계정 발급
- 내부 운영자 로그인
- 가맹점 등록 시 최초 OWNER 생성
- OWNER 또는 ADMIN의 하위 계정 발급
- 가맹점 관리자 로그인
- 가맹점별 TEST API Key 복수 발급
- API Key 폐기
- `PAYMENT_CREATE`, `PAYMENT_READ` Scope
- API Key 기반 결제 API 인증

후속:

- MFA/OTP
- SSO
- 세분화된 RBAC
- API Key IP Allowlist
- HMAC 요청 서명
- 자동 Key Rotation

## MVP 완료 경계

```text
Payment = SUCCEEDED
ExchangeOrder = COMPLETED
SettlementReceivable = READY
```

## 수취 지갑 귀속 — PG가 수탁한다

`Payment`의 수취 지갑은 **PG가 통제하는 지갑**이다(용어 정의는
[glossary](../domain/glossary.md)의 "Receiving Wallet"). **가맹점의 지갑이 아니다.**

**정산 흐름 전체가 "PG가 USDC를 갖고 있다"를 전제하기 때문이다.** `SellToFakeExchangeUseCase`는
받은 USDC를 매도해 KRW를 확보하고 그것을 가맹점에 대한 `SettlementReceivable`로 세운다. 수취
지갑이 가맹점 것이면 가맹점은 USDC를 직접 받으면서 PG로부터 KRW 채권까지 얻어 **같은 대금이
두 번 지급된다.** [ADR-007](../decisions/ADR-007-onchain-irreversibility.md)이 "우리 지갑에
들어왔다"를 자금 위치의 판단 기준으로 쓰는 것도 이 전제 위에 서 있다.

MVP는 **네트워크당 PG 지갑 하나를 모든 가맹점이 공용으로 쓴다** — 가맹점별 수취 주소를 발급하지
않는다. 그래서 입금 귀속이 주소로 나뉘지 않고, **고객이 제출한 Transaction Hash가 어느 결제의
것인지**로만 결정된다. 한 전송이 두 결제에 쓰이는 것을 막는 것은
`(network_code, transaction_hash)` UNIQUE 하나뿐이다. 가맹점별 주소 발급은 후속 범위다.

**API가 이 모델을 강제한다.** `POST /api/v1/payments`에 `receivingWallet` 필드가 없다 — 서버가
`app.payment.receiving-wallets`에서 요청된 네트워크에 맞는 주소를 꺼내 쓴다. 가맹점이 그 필드를
보내도 무시된다. 반면 `network`는 남는다: 어느 체인으로 받을지는 가맹점의 정당한 선택이고 수탁
문제와 무관하다.

설정에 그 네트워크의 지갑이 없으면 결제 생성이 **503**으로 실패한다(가맹점이 요청을 고쳐서
해결할 수 있는 것이 없으므로 4xx가 아니다). **기본값은 두지 않는다** — 실제 테스트넷 USDC가 그
주소로 전송되고 되찾을 수 없어서, 저장소에 적힌 주소가 조용히 쓰이는 것보다 실패하는 편이 낫다.

## 금액이 맞지 않는 입금

온체인 전송은 되돌릴 수 없어서 **결제 실패와 자금 미수령이 같은 뜻이 아니다.** 금액이 부족한
입금, 허용되지 않은 토큰 입금, 그리고 초과 지급분은 **자동으로 반환하거나 정산에 반영하지
않는다** — 수령 사실만 `blockchain_transaction`에 남기고 운영 절차로 처리한다. 부분 결제,
차액 청구, 자동 환불은 MVP 범위 밖이다. 근거와 후속 계획은
[ADR-007](../decisions/ADR-007-onchain-irreversibility.md)에 있다.
