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
