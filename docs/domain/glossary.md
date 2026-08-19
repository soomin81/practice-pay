# 도메인 용어 사전

각 항목의 **코드** 줄은 그 개념이 코드와 DB에서 갖는 **정식 이름**이다. 같은 개념에 새 이름을
붙이지 않으려고 적어 둔다 — 새 코드를 쓰기 전에 여기서 이름을 먼저 찾고, 목록에 없는 개념이면
**이 문서에 추가한 뒤** 쓴다.

**계층을 넘을 때 단어와 어순을 바꾸지 않는다**(`backend/CLAUDE.md`의 "이름은 계층을 넘어도
그대로다"). DB는 `snake_case`, Kotlin과 JSON은 `lowerCamelCase`이고, 그 변환 말고는 손대지 않는다.

## Merchant — 가맹점
스테이블코인 결제 서비스를 사용하는 사업자 또는 서비스 단위다.

**코드** — `Merchant`(Aggregate) · `MerchantId` · `MerchantCode` · `MerchantStatus` · `merchant` 테이블

## Customer — 고객
외부 지갑으로 스테이블코인을 결제하는 사용자다. MVP에서는 PG 고객 계정을 관리하지 않는다.

**코드** — **전용 타입이 없다**(계정이 없는 역할이라서다). 코드에 나타나는 것은 언제나 더 좁은
개념이다 — 지갑은 `Payment.customerWallet`, 연락 수단은 `PaymentCustomer`.

## PaymentCustomer — 구매자 정보
결제 한 건에 붙는 구매자의 이름·이메일·휴대전화다(1:1). **Customer(고객)와 구분한다** — 저쪽은
"결제하는 사람"이라는 역할이고 이쪽은 그 사람의 연락 수단을 담은 Aggregate다. 체크아웃 화면에서
고객이 직접 입력하며, 가맹점 서버를 거치지 않는다(ADR-008).

**코드** — `PaymentCustomer`(Aggregate) · `PaymentCustomerId` · `CustomerName`/`CustomerEmail`/`CustomerPhone` ·
`payment_customer` 테이블 · 저장 형태는 `EncryptedPaymentCustomer`(암호문만 담는다)

## Masking — 마스킹
개인정보의 일부를 가려 표시하는 것이다(`홍*동`, `ab***@example.com`, `010-****-1234`). 이 저장소는
**읽을 때가 아니라 쓸 때** 마스킹해서 별도 컬럼에 함께 저장한다 — 목록·상세·엑셀이 복호화 경로를
아예 타지 않게 하려는 것이다. 규칙은 표현 형식이 아니라 업무 규칙이라 도메인이 갖는다.

**코드** — 계산은 `CustomerName.masked`(각 VO의 `masked`) · 저장·전달은 `nameMasked`/`emailMasked`/`phoneMasked` ·
`payment_customer.customer_name_masked`. **`maskedName` 같은 어순은 쓰지 않는다** — DB 컬럼이
`customer_name_masked`이고 계층을 넘어도 어순을 바꾸지 않는다.

## Blind Index — 블라인드 인덱스
암호화된 값을 **정확 일치로 찾기 위한** `HMAC(pepper, 정규화된 값)`이다. 암호문은 값마다 랜덤 IV를
써서 검색할 수 없기 때문에 따로 둔다. 이메일·휴대전화에만 있고 이름에는 없으며, 부분 검색은 되지
않는다(ADR-008).

**코드** — `PiiBlindIndexer.index(normalizedValue)` · `HmacPiiBlindIndexer`(구현) ·
`emailIndex`/`phoneIndex` · `payment_customer.customer_email_index` · 입력은 각 VO의 `normalized`

## Order — 주문
가맹점의 상품 또는 서비스 주문이다. Payment와 동일한 개념이 아니다.

**코드** — `MerchantOrderId`(가맹점이 준 주문 번호) · `Payment.merchantOrderId`/`orderName`/`orderAmount` ·
`payment.merchant_order_id`. **`Order`라는 타입은 없다** — PG가 소유하는 개념이 아니라서다.

## Payment — 결제
가맹점 주문에 대한 PG 비즈니스 결제 단위이자 핵심 Aggregate Root다.

**코드** — `Payment`(Aggregate Root) · `PaymentId` · `PaymentStatus` · `PaymentFailureReason` · `payment` 테이블

## PaymentQuote — 결제 견적
KRW 주문 금액을 USDC 수량으로 변환할 때 사용한 시장 환율, 적용 환율, 스프레드, 금액, 유효시간의 불변 스냅샷이다.

**코드** — `PaymentQuote` · `PaymentQuoteId` · `marketRate`/`appliedRate`/`spreadRate`(타입 `ExchangeRate`) ·
`payment_quote` 테이블

## Hosted Checkout — PG 호스팅 체크아웃
PG가 직접 제공하는 고객 결제 페이지다.

**코드** — 경로 `/checkout/sessions/**` · `apps:api-payment`의 `api.payment.checkout.web` 패키지 ·
Use Case는 `application.checkout` · 화면은 `frontend/payment`

## CheckoutSession — 체크아웃 세션
특정 Payment를 처리하기 위한 유효시간이 제한된 결제 세션이다.

**코드** — `CheckoutSession` · `CheckoutSessionId` · `CheckoutSessionStatus` · `checkout_session` 테이블
(상태 컬럼은 `checkout_status`)

## Payment Complete Page — 결제 완료 페이지
PG가 결제 결과를 고객에게 표시하는 페이지다. Redirect만으로 결제 성공을 확정하지 않는다.

**코드** — 돌아갈 곳은 `CheckoutSession.successUrl`/`cancelUrl`(타입 `HttpUrl`) ·
`checkout_session.success_url`. 상태 조회 응답의 `redirectUrl`은 **다른 개념이다** — 설정된 주소가
아니라 "지금 보낼 곳"이고, 결제가 `SUCCEEDED`일 때만 채워진다.

## Payment Asset — 결제 자산
고객이 실제로 전송하는 디지털 자산이다. MVP는 USDC다.

**코드** — `Asset`(enum) · `Payment.paymentAsset` · `payment.payment_asset_code`

## Order Currency — 주문 통화
가맹점 주문 가격 통화다. MVP는 KRW다.

**코드** — `payment.order_currency`(항상 `"KRW"`). **도메인에는 통화 필드가 없다** — `Money`가 곧
KRW를 뜻한다(MVP가 KRW→USDC 한 쌍만 지원한다).

## Settlement Currency — 정산 통화
가맹점 정산 통화다. MVP는 KRW다.

**코드** — `settlement_receivable.settlement_currency`(항상 `"KRW"`) · 금액 타입은 `Money`/`SignedMoney`

## Blockchain Network — 블록체인 네트워크
토큰 전송이 실행되는 네트워크다. MVP는 Base Sepolia다.

**코드** — `BlockchainNetwork`(enum, `BASE_SEPOLIA`) · `Payment.network` · `payment.network_code` ·
체인 ID는 `ChainId`

## Customer Wallet — 고객 지갑
고객이 체크아웃에 연결하는 외부 지갑이다.

**코드** — `Payment.customerWallet`(타입 `WalletAddress`) · `payment.customer_wallet_address`.
`CheckoutSession.connectedWallet`(`checkout_session.connected_wallet_address`)은 **세션에 연결된
지갑**이라 다른 필드다 — 결제에 귀속되는 시점이 서로 다르다.

## Receiving Wallet — 수취 지갑
고객 USDC를 수취하는 PG 관리 지갑이다. **가맹점의 지갑이 아니다** — 정산이 "PG가 USDC를 보유한
뒤 KRW로 바꿔 가맹점에 채권을 세운다"는 구조라서다. 귀속 모델과 현재 API의 gap은
[MVP 범위](../architecture/mvp-scope.md)의 "수취 지갑 귀속" 절에 있다.

**코드** — `Payment.receivingWallet` · `payment.receiving_wallet_address` ·
설정은 `ReceivingWalletRegistry` + `app.payment.receiving-wallets.*`

## Token Contract Address — 토큰 계약 주소
네트워크에서 토큰을 식별하는 Contract 주소다. Symbol이 아니라 Network와 Contract 조합으로 검증한다.

**코드** — `ContractAddress` · `BlockchainTransaction.tokenContractAddress` ·
`blockchain_transaction.token_contract_address` · 기대값은 `PaymentNetworkConfig.expectedUsdcContractAddress`

## Minor Unit — 최소 단위
USDC 수량을 정수로 저장하는 단위다. `72.992701 USDC = 72,992,701`이다.

**코드** — `TokenAmount.amountMinor` · `payment.payment_amount_minor` ·
`blockchain_transaction.amount_minor`. **JSON에서는 문자열로 나간다**(JavaScript `Number` 안전 범위 초과).

## BlockchainTransaction — 블록체인 거래
온체인 자산 전송이다. Payment와 별도 상태를 가진다.

**코드** — `BlockchainTransaction` · `BlockchainTransactionId` · `BlockchainTransactionStatus` ·
`TransactionType` · `blockchain_transaction` 테이블(상태 컬럼은 `transaction_status`)

## Transaction Hash — 거래 해시
네트워크 거래 식별자다. `networkCode + transactionHash`로 중복을 방지한다.

**코드** — `TransactionHash` · `BlockchainTransaction.transactionHash` · `blockchain_transaction.transaction_hash`

## Confirmation — 블록 확인
거래가 포함된 이후 추가 블록에 의해 확정성이 증가하는 정도다.

**코드** — `BlockchainTransaction.confirmationCount`/`requiredConfirmationCount` ·
`blockchain_transaction.confirmation_count` · 기준값은 `PaymentNetworkConfig.REQUIRED_CONFIRMATION_COUNT`

## Fake Exchange — 가짜 거래소
MVP에서 시장 가격, USDC 매도, 주문 상태와 가상 KRW 확보 결과를 제공하는 모의 시스템이다.

**코드** — 환율은 `ExchangeRateProvider` ← `FakeExchangeRateProvider` · 매도는 `SellToFakeExchangeUseCase` ·
`exchange_order.exchange_provider_code`

## ExchangeOrder — 거래소 주문
USDC를 KRW로 매도하는 주문이다. 결제 적용 환율과 실제 체결 환율은 분리한다.

**코드** — `ExchangeOrder` · `ExchangeOrderId` · `ExchangeOrderStatus` · `ClientOrderId` · `OrderSide` ·
`averageExecutionRate`(체결 환율 — `PaymentQuote.appliedRate`와 다르다) · `exchange_order` 테이블

## SettlementReceivable — 정산 대상
향후 정산 배치가 소비할 결제 단위 정산 원천 데이터다. MVP 최종 상태는 READY다.

**코드** — `SettlementReceivable` · `SettlementReceivableId` · `SettlementReceivableStatus` ·
`settlement_receivable` 테이블(상태 컬럼은 `receivable_status`)

## Gross Amount — 정산 기준 금액
수수료와 조정 전 주문 금액이다.

**코드** — `SettlementReceivable.grossAmount`(타입 `Money`) · `settlement_receivable.gross_amount`

## Fee Amount — 수수료 금액
정산 금액에서 차감하는 PG 수수료다.

**코드** — `SettlementReceivable.feeAmount`(타입 `Money`) · 요율은 `feeRate` ·
`settlement_receivable.fee_amount`/`fee_rate`

## Adjustment Amount — 조정 금액
정산 시 추가 가산 또는 차감하는 금액이다. MVP 기본값은 0이다.

**코드** — `SettlementReceivable.adjustmentAmount`(타입 `SignedMoney` — 음수를 허용해야 한다) ·
`settlement_receivable.adjustment_amount`

## Net Amount — 정산 예정 금액
`grossAmount - feeAmount + adjustmentAmount`다.

**코드** — `SettlementReceivable.netAmount` · `settlement_receivable.net_amount` ·
집계는 `totalNetAmount`(보류·취소를 뺀 값 — `SettlementReceivableStatus.isOnPayoutPath`)

## Settlement — 정산
SettlementReceivable을 가맹점 단위로 집계하는 향후 Aggregate다.

**코드** — **아직 없다**(ADR-005의 범위 밖). `settlement_receivable.assigned_settlement_seq` 자리만
비워 뒀다. 만들 때 이 이름을 쓴다.

## Settlement Batch — 정산 배치
READY 정산 대상을 집계 정산에 편입하는 향후 배치다.

**코드** — **아직 없다.** `apps:batch`에 만들 때 이 이름을 쓴다.

## Payout — 지급
확정된 원화 정산액을 가맹점 계좌로 송금하는 업무다.

**코드** — **아직 없다.** `payment` 레코드에 지급 상태를 절대 추가하지 않는다(루트 `CLAUDE.md`).

## Webhook — 웹훅
PG가 가맹점 서버로 비동기 결과를 통지하는 방식이다.

**코드** — `WebhookDelivery` · `WebhookDeliveryStatus` · `webhook_delivery` 테이블(상태 컬럼은
`delivery_status`) · 전송은 `WebhookSender`, 서명은 `WebhookSigner`

## Outbox Event — 아웃박스 이벤트
DB 트랜잭션과 비동기 이벤트 전달 사이의 유실을 막기 위해 저장하는 이벤트다.

**코드** — `OutboxEvent` · `OutboxEventStatus` · `EventId` · `outbox_event` 테이블(상태 컬럼은
`event_status`, 공개 식별자는 `event_id`)

## InternalUser — 내부 운영자

PG 내부 관리자 화면에 로그인하는 사용자 계정이다.

MVP 역할:

- `SUPER_ADMIN`
- `OPERATOR`
- `VIEWER`

내부 운영자 계정은 `SUPER_ADMIN`만 발급할 수 있다.

**코드** — `InternalUser` · `InternalUserId` · `InternalUserRole` · `AccountStatus`(가맹점 사용자와 공유) ·
`internal_user` 테이블(역할 컬럼은 `role_code`, 상태는 `user_status`) · 로그인 감사는 `InternalLoginAudit`

## MerchantUser — 가맹점 사용자

특정 가맹점의 관리자 화면에 로그인하는 사용자 계정이다.

MVP 역할:

- `OWNER`
- `ADMIN`
- `VIEWER`

가맹점 등록 시 최초 `OWNER`를 함께 생성하고, OWNER 또는 ADMIN이 같은 가맹점의 하위 계정을 발급한다.

**코드** — `MerchantUser` · `MerchantUserId` · `MerchantUserRole` · `merchant_user` 테이블 ·
로그인 감사는 `MerchantLoginAudit`. 역할 enum은 `InternalUserRole`과 **공유하지 않는다**(값이 다르다).

## AccountInvitation — 계정 초대

내부 운영자 또는 가맹점 사용자가 본인의 비밀번호를 설정하고 계정을 활성화하기 위한 1회성 초대다.

초대 토큰 원문은 저장하지 않고 Hash만 저장한다.

**코드** — `AccountInvitation` · `AccountInvitationId` · `AccountInvitationStatus` · `InvitationAccountType` ·
`account_invitation` 테이블(`token_hash`) · 해시는 `InvitationTokenHasher`

## MerchantApiKey — 가맹점 API Key

가맹점 서버가 스테이블코인 결제 시스템의 결제 API를 호출할 때 사용하는 서버 간 인증 자격증명이다.

소유자는 Merchant이며, 발급한 MerchantUser는 감사 정보로 기록한다.

원문은 최초 한 번만 표시하고 DB에는 Prefix와 Hash만 저장한다.

**코드** — `MerchantApiKey` · `MerchantApiKeyId` · `ApiKeyStatus` · `ApiEnvironment` ·
`merchant_api_key` 테이블(`secret_hash`) · 해시는 `ApiKeySecretHasher`

## API Key Prefix — API Key 접두부

API Key 후보를 빠르게 조회하고 관리자 화면에서 Key를 식별하기 위한 공개 부분이다.

예:

```text
sk_test_ab12cd34
```

**코드** — `ApiKeyPrefix` · `merchant_api_key.key_prefix`

## API Key Scope — API Key 권한 범위

API Key가 호출할 수 있는 API 범위를 나타낸다.

MVP:

- `PAYMENT_CREATE`
- `PAYMENT_READ`

**코드** — `ApiKeyScope`(enum) · `merchant_api_key_scope` 테이블(`scope_code`) ·
Spring Security 권한으로는 `SCOPE_PAYMENT_CREATE`처럼 접두어가 붙는다
