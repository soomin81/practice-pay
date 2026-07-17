# ADR-001: 초기 범위를 End-to-End 결제 MVP로 제한한다

- 상태: 승인

## 결정

Base Sepolia USDC Hosted Checkout 결제를 구현하고 다음 상태까지를 MVP로 한다.

```text
Payment = SUCCEEDED
ExchangeOrder = COMPLETED
SettlementReceivable = READY
```

정산 배치, 실제 지급, AML/KYT, 환불, 실거래소, 다중 네트워크는 후속 단계로 미룬다.
