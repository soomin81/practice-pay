# 도메인 용어 사전

## Merchant — 가맹점
스테이블코인 결제 서비스를 사용하는 사업자 또는 서비스 단위다.

## Customer — 고객
외부 지갑으로 스테이블코인을 결제하는 사용자다. MVP에서는 PG 고객 계정을 관리하지 않는다.

## Order — 주문
가맹점의 상품 또는 서비스 주문이다. Payment와 동일한 개념이 아니다.

## Payment — 결제
가맹점 주문에 대한 PG 비즈니스 결제 단위이자 핵심 Aggregate Root다.

## PaymentQuote — 결제 견적
KRW 주문 금액을 USDC 수량으로 변환할 때 사용한 시장 환율, 적용 환율, 스프레드, 금액, 유효시간의 불변 스냅샷이다.

## Hosted Checkout — PG 호스팅 체크아웃
PG가 직접 제공하는 고객 결제 페이지다.

## CheckoutSession — 체크아웃 세션
특정 Payment를 처리하기 위한 유효시간이 제한된 결제 세션이다.

## Payment Complete Page — 결제 완료 페이지
PG가 결제 결과를 고객에게 표시하는 페이지다. Redirect만으로 결제 성공을 확정하지 않는다.

## Payment Asset — 결제 자산
고객이 실제로 전송하는 디지털 자산이다. MVP는 USDC다.

## Order Currency — 주문 통화
가맹점 주문 가격 통화다. MVP는 KRW다.

## Settlement Currency — 정산 통화
가맹점 정산 통화다. MVP는 KRW다.

## Blockchain Network — 블록체인 네트워크
토큰 전송이 실행되는 네트워크다. MVP는 Base Sepolia다.

## Customer Wallet — 고객 지갑
고객이 체크아웃에 연결하는 외부 지갑이다.

## Receiving Wallet — 수취 지갑
고객 USDC를 수취하는 PG 관리 지갑이다.

## Token Contract Address — 토큰 계약 주소
네트워크에서 토큰을 식별하는 Contract 주소다. Symbol이 아니라 Network와 Contract 조합으로 검증한다.

## Minor Unit — 최소 단위
USDC 수량을 정수로 저장하는 단위다. `72.992701 USDC = 72,992,701`이다.

## BlockchainTransaction — 블록체인 거래
온체인 자산 전송이다. Payment와 별도 상태를 가진다.

## Transaction Hash — 거래 해시
네트워크 거래 식별자다. `networkCode + transactionHash`로 중복을 방지한다.

## Confirmation — 블록 확인
거래가 포함된 이후 추가 블록에 의해 확정성이 증가하는 정도다.

## Fake Exchange — 가짜 거래소
MVP에서 시장 가격, USDC 매도, 주문 상태와 가상 KRW 확보 결과를 제공하는 모의 시스템이다.

## ExchangeOrder — 거래소 주문
USDC를 KRW로 매도하는 주문이다. 결제 적용 환율과 실제 체결 환율은 분리한다.

## SettlementReceivable — 정산 대상
향후 정산 배치가 소비할 결제 단위 정산 원천 데이터다. MVP 최종 상태는 READY다.

## Gross Amount — 정산 기준 금액
수수료와 조정 전 주문 금액이다.

## Fee Amount — 수수료 금액
정산 금액에서 차감하는 PG 수수료다.

## Adjustment Amount — 조정 금액
정산 시 추가 가산 또는 차감하는 금액이다. MVP 기본값은 0이다.

## Net Amount — 정산 예정 금액
`grossAmount - feeAmount + adjustmentAmount`다.

## Settlement — 정산
SettlementReceivable을 가맹점 단위로 집계하는 향후 Aggregate다.

## Settlement Batch — 정산 배치
READY 정산 대상을 집계 정산에 편입하는 향후 배치다.

## Payout — 지급
확정된 원화 정산액을 가맹점 계좌로 송금하는 업무다.

## Webhook — 웹훅
PG가 가맹점 서버로 비동기 결과를 통지하는 방식이다.

## Outbox Event — 아웃박스 이벤트
DB 트랜잭션과 비동기 이벤트 전달 사이의 유실을 막기 위해 저장하는 이벤트다.
