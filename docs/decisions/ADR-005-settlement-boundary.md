# ADR-005: MVP 정산 범위를 SettlementReceivable까지로 제한한다

- 상태: 승인

## 결정

MVP는 결제 단위 `SettlementReceivable`을 생성하고 `READY`로 변경하는 것까지 구현한다. 가맹점 단위 집계, 정산 배치, 원화 지급, 대사는 후속 단계로 미룬다.
