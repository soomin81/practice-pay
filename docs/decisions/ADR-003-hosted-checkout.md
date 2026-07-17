# ADR-003: PG Hosted Checkout을 사용한다

- 상태: 승인

## 결정

PG가 체크아웃과 결제 완료 페이지를 제공한다. 가맹점은 Payment 생성 후 Checkout URL로 고객을 이동시키며, 최종 결제 결과는 Webhook 또는 조회 API로 확인한다.
