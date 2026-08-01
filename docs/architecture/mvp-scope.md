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

## 금액이 맞지 않는 입금

온체인 전송은 되돌릴 수 없어서 **결제 실패와 자금 미수령이 같은 뜻이 아니다.** 금액이 부족한
입금, 허용되지 않은 토큰 입금, 그리고 초과 지급분은 **자동으로 반환하거나 정산에 반영하지
않는다** — 수령 사실만 `blockchain_transaction`에 남기고 운영 절차로 처리한다. 부분 결제,
차액 청구, 자동 환불은 MVP 범위 밖이다. 근거와 후속 계획은
[ADR-007](../decisions/ADR-007-onchain-irreversibility.md)에 있다.
