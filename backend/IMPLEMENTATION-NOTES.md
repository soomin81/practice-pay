# 기능별 구현 기록 (backend)

이 문서는 **Use Case·Adapter를 하나씩 구현하면서 내린 설계 판단의 기록**이다. `backend/CLAUDE.md`에서 분리해 나왔다 — 그쪽은 "앞으로 작업할 때 따르는 규칙"만 담고, 이 문서는 "그때 왜 그렇게 판단했는지"를 담는다.

## 세 문서의 역할 구분

| 문서 | 담는 것 | 판별 기준 |
|---|---|---|
| `docs/` | 검토된 설계 기준(도메인·상태·스키마·ADR) | 구현과 무관하게 참인 것. **충돌하면 `docs/`가 우선한다** |
| `backend/CLAUDE.md` | 작업 규칙(명령어·컨벤션·함정) | 다음 작업자가 **따라야 할** 것 |
| 이 문서 | 기능별 판단 근거와 검증 결과 | 이미 끝난 작업에 대한 **설명**. 읽지 않아도 새 작업은 할 수 있다 |

**재사용 가능한 규칙이 나오면 이 문서가 아니라 `CLAUDE.md`에 쓴다.** 여기 있는 내용은 특정 기능에 묶인 판단이라, 새 기능을 만들 때 반드시 읽어야 하는 것은 아니다 — 비슷한 상황을 만났을 때 선례로 찾아보는 용도다.

## 새 절을 추가할 때

- Use Case/Adapter 하나당 한 절(`##`)로, **구현 순서대로 뒤에 붙인다.**
- `docs/`에 없어서 직접 정한 값(상수·경로·정책)과 그 이유를 남긴다.
- **알려진 gap은 명시한다** — 나중에 "왜 이건 안 했지"를 다시 추론하지 않도록.
- 실물 검증(`bootRun` + `curl`, 실제 RPC)을 했다면 무엇을 어떻게 확인했는지 남긴다. 자동화 테스트가 잡지 못하는 층의 근거가 된다(`CLAUDE.md`의 "테스트가 잡지 못하는 층").
- 커밋 메시지와 내용이 겹치는 것은 정상이다. 커밋은 그 시점의 변경을 설명하고, 이 문서는 현재 코드 기준으로 계속 갱신된다 — 코드가 바뀌면 해당 절도 고친다.

---

## "체크아웃 지갑 연결" Use Case(`ConnectCheckoutWalletUseCase`, `application.checkout`)

고객이 체크아웃 페이지에서 외부 EVM 지갑을 연결하는 시점을 구현한다. `SubmitPaymentTransactionUseCase`가 "이미 `WALLET_CONNECTED`인 CheckoutSession"을 전제하고 시작했던 지점을 이 Use Case가 그보다 앞서 채운다 — 지금까지 만든 결제 흐름 Use Case 중 시간순으로 가장 이르다.

- **처음으로 `application.payment`가 아니라 `application.checkout` 패키지를 새로 만들었다.** `CheckoutSession`만 다루고 `Payment`/`BlockchainTransaction`은 건드리지 않아서, `Identity` Use Case들이 `application.identity`에 따로 있는 것과 같은 이유로 아그리게이트별 패키지로 분리했다 — 앞선 세 Use Case가 전부 `application.payment`에 있었던 건 전부 `Payment`가 걸린 다중 Aggregate 트랜잭션이었기 때문이지, "결제 관련은 다 `payment` 패키지"라는 규칙이 아니다.
- **단일 Aggregate Use Case라 `TransactionManager`가 필요 없다** — `CheckoutSessionRepository.save` 한 번으로 끝난다. `Repository`/`Command`/`Result`/`Use Case` 넷만 있으면 되는, 지금까지 중 가장 단순한 슬라이스다.
- **`CREATED` 상태였으면 `open()`을 먼저 호출한 뒤 `connectWallet()`으로 넘어간다.** `CheckoutSession.open()`을 부르는 별도의 "체크아웃 페이지 조회" Use Case/API는 만들지 않았다 — 페이지 조회는 상태를 바꾸지 않는 `GET`으로 남겨두는 게 REST 관례에 맞고, 고객이 실제로 처음 행동을 취하는 순간(지갑 연결)을 `open()`이 뜻하는 "체크아웃 페이지를 열었다"로 간주하는 쪽을 택했다 — `docs/`에 이 판단의 근거는 없다(추론한 설계 판단).
- **`CheckoutSessionNotFoundException`을 `application.payment`에서 `application.checkout`으로 옮겼다.** 원래 `SubmitPaymentTransactionUseCase`를 만들 때 그 패키지에 넣었는데, 이 Use Case도 똑같이 필요해지면서 위치가 어색해졌다 — 두 Use Case 다 이 패키지를 import해서 쓴다.
- **지갑 재연결(다른 지갑으로 바꾸기)은 도메인에 없다** — `WALLET_CONNECTED` 이후 다시 호출하면 `CheckoutSession.connectWallet()`의 `checkTransition`이 그대로 `IllegalStateException`을 던진다. 새 도메인 메서드가 필요한 범위 밖 기능이라 손대지 않았다.
- **테스트**: `ConnectCheckoutWalletUseCaseTest`(단위, CREATED에서 한 번에 연결/이미 OPEN인 경우/존재하지 않는 세션/이미 WALLET_CONNECTED인 경우/CANCELLED인 경우).

## "BlockchainTransaction 생성" Use Case(`SubmitPaymentTransactionUseCase`)

고객 지갑이 USDC 전송을 브로드캐스트한 뒤, 체크아웃 프런트엔드가 그 Transaction Hash를 PG에 제출하는 시점을 구현한다. `ConfirmBlockchainTransactionUseCase`가 "이미 있는 BlockchainTransaction을 다시 확인하는 폴링"이라면, 이 Use Case는 그 BlockchainTransaction을 최초로 만드는 자리다 — `ConfirmBlockchainTransactionUseCase`의 KDoc이 범위 밖으로 남겨뒀던 지점을 채운다. 같은 자리(`application.payment`)에 있다.

- **네 번째 트랜잭션 경계를 새로 정의했다**: `BlockchainTransaction(SUBMITTED) + CheckoutSession(PAYMENT_SUBMITTED) + Payment(PROCESSING)`. `docs/architecture/persistence-jooq.md`가 명시한 세 경계(결제 생성/결제 완료/환전 완료) 중 어디에도 해당하지 않는다 — 이 Use Case가 세 Aggregate가 "고객이 결제를 제출했다"는 하나의 사실을 함께 반영해야 한다고 판단해서 원자적으로 묶었다. `OutboxEvent`는 포함하지 않는다 — 문서가 Outbox를 명시한 경계는 "결제 생성"과 "결제 완료" 둘뿐이라, 여기서 Webhook을 새로 만들어내지 않는다(알려진 gap).
- **고객 지갑 연결(`CheckoutSession.connectWallet`, `OPEN → WALLET_CONNECTED`)은 범위 밖이다.** 이 Use Case는 `CheckoutSession.connectedWallet`이 이미 채워져 있다고 전제하고 그대로 재사용한다 — 지갑 연결 자체는 별도 Use Case가 먼저 처리해야 한다(아직 없음).
- **중복 제출은 멱등하게 처리하고, Hash 재사용은 명시적으로 막는다.** 같은 `(network, transactionHash)`로 이미 `BlockchainTransaction`이 있으면, 같은 Payment의 것이면 새로 만들지 않고 기존 결과를 그대로 돌려주고(재전송/중복 클릭 대응), 다른 Payment의 것이면 `DuplicateTransactionHashException`을 던진다 — `uk_blockchain_network_hash` Unique 제약과 대응하는 애플리케이션 레벨 확인이다. `ConfirmBlockchainTransactionUseCase`의 `PaymentTransactionValidator`가 "중복 여부는 여기서 다시 확인하지 않는다"고 미뤄뒀던 게 바로 이 지점이다.
- **새 공용 상수 `PaymentNetworkConfig`를 도입했다** — 네트워크별 Chain ID, 허용 USDC Contract 주소, 필요 Confirm 수(`REQUIRED_CONFIRMATION_COUNT = 12`, `docs/`에 값이 없어 고정한 MVP 상수)를 한 곳에 모았다. 원래 `ConfirmBlockchainTransactionUseCase`가 Contract 주소를 자기 것으로 갖고 있었는데, 이 Use Case도 "제출 시점의 기대값"으로 같은 값이 필요해져서 공용으로 뺐다 — 두 Use Case가 각자 상수를 들고 있으면 나중에 값이 어긋날 위험이 있었다.
- **새 Repository Port 메서드 둘을 추가했다**: `CheckoutSessionRepository.findById`(기존엔 `findByPaymentId`뿐이었다 — 이 Use Case는 체크아웃 프런트엔드가 아는 `checkoutSessionId`로 시작한다), `BlockchainTransactionRepository.findByNetworkAndTransactionHash`(중복 확인용).
- **테스트**: `SubmitPaymentTransactionUseCaseTest`(단위, 정상 생성/저장된 필드 확인/같은 결제 재제출 멱등성/다른 결제의 Hash 재사용 차단/존재하지 않는 CheckoutSession/WALLET_CONNECTED가 아닌 상태), `CheckoutSessionRepositoryAdapterTest` + `BlockchainTransactionRepositoryAdapterTest`의 새 조회 메서드 케이스(Testcontainers MySQL 통합).

## "BlockchainTransaction 감지·Confirm" Use Case(`ConfirmBlockchainTransactionUseCase`)

`docs/architecture/mvp-scope.md`의 전체 흐름 중 `USDC 전송 → BlockchainTransaction 감지 및 Confirm → Payment SUCCEEDED → 결제 완료 페이지와 Webhook` 구간과, `docs/architecture/persistence-jooq.md`가 정의한 "결제 완료" 트랜잭션 경계(`BlockchainTransaction + Payment SUCCEEDED + OutboxEvent`)를 구현한다. `CreatePaymentUseCase`와 같은 자리(`application.payment`)에 있다 — Payment 생명주기를 이어가는 Use Case라서다.

- **이미 존재하는 `BlockchainTransaction` 하나를 대상으로 한 폴링 한 번이다.** `BlockchainTransaction`을 처음 만드는 것(고객이 제출한 Transaction Hash를 `SUBMITTED`로 기록하는 것)은 이 Use Case의 범위 밖이다 — 별도 Use Case가 필요하고 아직 없다. 이 Use Case 자체도 반복하지 않는다 — `docs/database/database-design.md`의 "Confirm Worker" 인덱스가 암시하는 대로, 향후 `apps:batch`의 Worker가 대상 목록을 뽑아 하나씩 호출하는 것을 전제로 설계했다(그 Worker도 범위 밖).
- **상태 전이는 한 번의 실행 안에서 여러 단계를 연달아 지나갈 수 있다.** `SUBMITTED`인 채로 폴링했는데 이미 필요한 Confirm 수를 넘겼으면, 한 번의 호출로 `detect()` → `startConfirming()` → `recordConfirmation()` → `confirm()`까지 이어진다(각 Aggregate 메서드의 `checkTransition`이 순서를 그대로 강제하니 안전하다). `BlockchainTransaction.detect()`가 호출되는 바로 그 순간 `Payment.startConfirmation()`도 함께 호출한다 — `Payment.startConfirmation`의 KDoc이 "온체인 거래가 감지되어 Confirm 대기 상태로 전이한다"고 명시하므로, 검증 통과 여부와 무관하게 "감지" 자체가 이 전이의 조건이라고 해석했다.
- **새 Domain Service `PaymentTransactionValidator`를 `modules:domain`이 아니라 `modules:application`에 뒀다.** `docs/domain/domain-model.md`는 "Domain Service"로 분류하지만(Network/Chain ID/Contract/Wallet/Amount/Receipt 검증), 검증 대상인 `OnChainTransaction`이 `BlockchainClient` Port(`modules:application`)의 반환 타입이라 의존 방향상(`application → domain`만 가능) `modules:domain`에 둘 수 없다. 도메인 순수성 원칙(부수효과 없는 순수 함수, Spring/jOOQ 미의존)은 그대로 지키고 물리적 위치만 옮겼다 — `PaymentTransactionValidator.kt`의 KDoc에 이 판단 이유를 그대로 남겼다. `modules:domain`에 미러 타입을 새로 만들어 순수성을 지키는 대안도 검토했지만, `OnChainTransaction`/`OnChainTokenTransfer`가 이미 Port 경계에 맞게 설계돼 있어서 중복 타입을 만드는 비용이 더 크다고 판단했다.
- **검증하지 않는 것 둘**: Confirm 수 부족은 실패가 아니라 "다음 폴링을 기다리는 정상 대기"라 `PaymentTransactionValidator`가 아니라 이 Use Case가 직접 `confirmationCount`를 비교해서 처리한다. 중복 Transaction Hash 여부는 `uk_blockchain_network_hash` Unique 제약이 `BlockchainTransaction` 생성 시점에 이미 보장했다고 보고 여기서 다시 확인하지 않는다(그 생성 Use Case는 범위 밖이라 이 Use Case가 참조할 근거 데이터도 없다).
- **`Payment`는 허용 Contract 주소를 갖고 있지 않다** — `Asset`(예: `USDC`)은 순수 표시용 코드일 뿐 Contract 주소와 무관하다(`Asset.kt`의 KDoc: "Token Symbol만으로 자산을 판단하지 않는다"). 그래서 `PaymentNetworkConfig`(위 "BlockchainTransaction 생성" 절 참고, `SubmitPaymentTransactionUseCase`와 공유하는 상수)가 네트워크별 허용 USDC Contract 주소를 갖는다. Base Sepolia 값(`0x036CbD53842c5426634e7929541eC2318f3dCF7e`)은 Circle 공식 문서(`developers.circle.com/stablecoins/usdc-contract-addresses`)에서 그대로 가져왔다.
- **`WalletAddress`/`ContractAddress` 비교는 대소문자를 무시한다.** 두 Value Object 모두 EIP-55 Checksum 검증을 하지 않고(`WalletAddress.kt`의 KDoc) `equals`가 문자열 그대로 비교라, 그대로 `==`로 비교하면 같은 주소인데 대소문자가 다르다는 이유로 검증에 실패할 수 있다 — `PaymentTransactionValidator`는 `.value.equals(..., ignoreCase = true)`로 비교한다.
- **`BlockchainTransactionRepository` Port를 새로 만들었다**(`save`/`findById`만 — 지금 필요한 것만). `PaymentRepository`에도 `findById`를 추가했다(기존엔 `findByMerchantOrderId`뿐이었다 — 이 Use Case가 `BlockchainTransaction.paymentId`로 `Payment`를 찾아야 해서 필요해졌다).
- **`BlockchainTransactionRepositoryAdapter`**(`modules:infra-persistence`)는 `PaymentRepositoryAdapter`와 같은 모양·같은 낙관적 잠금 한계를 가진다.
- **성공 시에만 `OutboxEvent`를 남긴다** — `docs/architecture/persistence-jooq.md`가 명시한 "결제 완료" 경계(`BlockchainTransaction + Payment SUCCEEDED + OutboxEvent`)가 `Payment SUCCEEDED`를 특정하고 있어서, 실패 경로(`payment.fail()`)에서는 Webhook용 `OutboxEvent`를 만들지 않는다 — 가맹점에게 실패도 알려주는 게 더 나을 수 있지만, 문서에 없는 걸 새로 만들지 않는 쪽을 택했다(알려진 gap으로 남긴다).
- **테스트**: `PaymentTransactionValidatorTest`(단위, 정상/Receipt 실패/Network 불일치/Contract 불허/Wallet 불일치/Amount 부족/대소문자 무시/초과 금액 케이스), `ConfirmBlockchainTransactionUseCaseTest`(단위, 미검출/Confirm 부족/즉시 Confirm 완료/재개된 CONFIRMING 폴링/Receipt 실패/검증 실패/존재하지 않는 ID/이미 종료 상태), `BlockchainTransactionRepositoryAdapterTest` + `PaymentRepositoryAdapterTest`의 `findById` 케이스(Testcontainers MySQL 통합).

## `api-payment`의 결제 생성 컨트롤러

`POST /api/v1/payments`(`docs/architecture/identity-access-api-key.md`의 "대표 사용 API")가 `CreatePaymentUseCase`를 HTTP로 노출하는 첫 inbound Adapter다. 패키지는 `api.payment.web`(컨트롤러/요청·응답 DTO/예외 핸들러), `api.payment.config`(Use Case를 Bean으로 조립하는 Composition Root), `api.payment.security`(API Key 인증 Filter)로 나눴다. 한때 `api.payment.support`에 outbound port 구현을 뒀지만 전부 `modules:infra-support`로 옮겨서 지금은 없다 — 앱은 Port를 구현하지 않는다(`CLAUDE.md`의 "공용 Port 구현" 절 참고).

- **`UseCaseConfiguration`**: `CreatePaymentUseCase`는 `modules:application`에 있고 그 모듈은 Spring에 의존하지 않아서 `@Component`를 직접 달 수 없다 — 그래서 이 `@Configuration` 클래스가 outbound port Bean들을 주입받아 `@Bean` 메서드로 대신 조립한다. 앞으로 Use Case가 늘어나면 이 클래스에 `@Bean` 메서드를 추가한다(Use Case 하나마다 별도 Configuration 클래스를 만들 필요는 없다).
- **`IdGenerator`/`ExchangeRateProvider`의 구현이 없었다** — 둘 다 영속성 관심사가 아니라서 `modules:infra-persistence`가 구현하지 않았다. `support.UuidIdGenerator`(UUID 기반)와 `support.FakeExchangeRateProvider`(고정 환율, `docs/decisions/ADR-004-fake-exchange.md`의 Fake Exchange를 대표)를 이 앱 안에 직접 만들어 채웠다 — 둘 다 다른 앱이 필요로 하게 되면 그때 공유 위치로 옮길 수 있는, 지금은 이 정도로 충분한 임시 구현이라고 KDoc에 명시했다.
- **`PaymentApiExceptionHandler`**(`@RestControllerAdvice`)가 `application`/`domain` 예외를 HTTP 상태로 옮긴다: `MerchantNotFoundException` → 404, `MerchantCannotAcceptPaymentsException` → 409, Value Object의 `init { require(...) }` 검증 실패(`IllegalArgumentException`) → 400, `@Valid` 실패(`MethodArgumentNotValidException`) → 400. 이 매핑은 inbound Adapter의 책임이다 — Use Case나 Value Object는 HTTP를 전혀 모른다.
- **`merchantId`는 요청 본문이 아니라 인증된 `MerchantApiKey`에서 온다** — 아래 "`api-payment`의 API Key 인증" 참고. 처음 이 컨트롤러를 만들 때는 API Key 인증이 없어서 `merchantId`를 요청 본문에 직접 받는 임시 gap이 있었는데, 이제 해소됐다.
- **테스트**: `PaymentControllerTest`는 `@WebMvcTest(PaymentController::class)`로 웹 계층만 띄운다(DB 없음) — `CreatePaymentUseCase`는 `com.ninja-squad:springmockk`의 `@MockkBean`으로 Mock했다(`CLAUDE.md`의 "테스트" 절 참고). `@Autowired` 필드 주입이 필요해서 이 파일만 `FunSpec() { init { ... } }` 형태를 쓴다. 여기에 더해 실제 `bootRun` + `curl`로 시딩된 `mrc_test_001` 가맹점을 상대로 결제 생성 → 멱등 재요청(같은 `paymentId` 반환, 중복 행 없음) → DB 직접 조회까지 한 번 수동으로 검증했다(자동화된 테스트로 남기지는 않음).

## `api-payment`의 API Key 인증

`docs/architecture/identity-access-api-key.md`의 "6.4 저장 정책" 권장 흐름을 그대로 구현한다: `Authorization: Bearer sk_test_<prefixToken>_<secret>` 수신 → Prefix 추출 → Prefix로 후보 Key 조회 → 전체 Key를 서버 측 Pepper와 함께 해시 → `secret_hash` 비교 → 상태·환경·Merchant 상태 확인 → `last_used_at` 갱신. `AuthenticateInternalUserUseCase`/`AuthenticateMerchantUserUseCase`(자격증명 검증 → 신원 반환)와 같은 모양이지만, 로그인이 아니라 **보호된 요청마다** 실행된다는 점이 다르다 — 실패 잠금도 없다(사람이 타이핑하는 비밀번호가 아니라서).

- **API Key 형식**: `key_prefix`(예: `sk_test_ab12cd34`, `ApiKeyPrefix`의 KDoc 예시) 뒤에 `_<secret>`을 붙인 게 전체 Key다. `AuthenticateApiKeyUseCase.extractPrefix`는 `_`로 최대 4조각까지만 자른다(`split(limit = 4)`) — `secret`이 `_`를 포함해도 깨지지 않는다.
- **`ApiKeySecretHasher`를 `PasswordEncoder`와 의도적으로 분리했다** — 사람 비밀번호는 BCrypt 같은 느린 적응형 해시가 맞지만, API Key는 매 요청 검증이라 그럴 필요가 없다. 문서가 명시한 대로 `HmacApiKeySecretHasher`(`modules:infra-support`의 `infra.support.apikey`, 원래는 `apps:api-payment` 안에 있었다)가 HMAC-SHA-256 + 서버 측 Pepper로 구현한다. Pepper는 `application.yaml`의 `app.api-key.pepper`에서 오고, 지금 값은 `db-core`의 `verysecret` DB 비밀번호와 같은 성격의 로컬 개발용 평문 placeholder다 — 실제 배포 전 환경변수/Secret Manager로 옮겨야 한다. 해시 비교는 타이밍 공격을 막기 위해 `String.equals` 대신 `MessageDigest.isEqual`(상수 시간 비교)로 한다.
- **`MerchantApiKeyRepositoryAdapter`(`modules:infra-persistence`)는 이 프로젝트에서 처음으로 자식 컬렉션 테이블을 다루는 Adapter다.** `MerchantApiKey.scopes`는 `merchant_api_key_scope`(복합 PK, 자기 생명주기 없는 값 컬렉션)에 저장된다. 도메인에 Scope를 바꾸는 메서드가 없어서(발급 시 정해지면 끝) `save`의 INSERT 경로에서만 Scope 행을 쓰고, UPDATE 경로(`revoke`/`expire`/`recordUsage`)는 건드리지 않는다.
- **인증은 Filter가 한다, 컨트롤러가 아니다.** `ApiKeyAuthenticationFilter`(`OncePerRequestFilter`)가 `Authorization` 헤더를 읽어 매 요청 `AuthenticateApiKeyUseCase`를 부르고, 성공하면 이번 요청의 `SecurityContext`에 `UsernamePasswordAuthenticationToken(principal = ApiKeyPrincipal(merchantId, merchantApiKeyId), authorities = ["SCOPE_<ApiKeyScope>", ...])`를 심는다. 실패해도 예외를 던지지 않고 `SecurityContext`만 비운 채 다음 필터로 넘긴다 — 그 뒤 `authorizeHttpRequests`가 401/403을 결정한다.
- **`/error`는 세 API 앱 모두 `permitAll`이다** — 컨테이너가 오류 응답을 만들 때 도는 ERROR 디스패치 경로인데, 여기에 인증을 요구하면 실제 오류가 전부 401로 가려진다(인증 필터는 `OncePerRequestFilter` 기본값상 ERROR 디스패치에서 실행되지 않아 `SecurityContext`가 비어 있다). 잘못된 요청 본문은 여기에 더해 `HttpMessageNotReadableException` 핸들러가 `/error` 경로를 아예 타지 않고 `ErrorResponse` 형식으로 400을 반환한다. 인증 실패 자체는 그대로 401이다 — `/error`를 열어도 인가가 우회되지 않는 것은 실제 `bootRun`으로 확인했다(`CLAUDE.md`의 "테스트가 잡지 못하는 층" 절 참고).
- **`SecurityConfig`**: `POST /api/v1/payments`에 `hasAuthority("SCOPE_PAYMENT_CREATE")`를 요구한다. `SessionCreationPolicy.STATELESS`로 세션을 아예 안 만든다 — `apps:api-admin`/`apps:api-merchant`의 세션 쿠키 로그인과 근본적으로 다른 인증 방식이라서다. **여기서 CSRF를 끄는 건 admin/merchant처럼 "아직 안 켠 gap"이 아니라 애초에 필요 없다** — CSRF는 브라우저가 쿠키를 자동으로 실어 보내는 상황을 노리는 공격인데, 이 앱은 세션 쿠키를 쓰지 않는 순수 Bearer 토큰 인증이라 공격 대상 자체가 성립하지 않는다.
- **`ApiKeyAuthenticationEntryPoint`**가 인증 실패 401 응답을 `PaymentApiExceptionHandler`와 같은 `ErrorResponse` JSON 형식으로 통일한다 — 없으면 Spring Security 기본 엔트리 포인트가 다른 형식을 준다.
- **`PaymentController`는 `merchantId`를 `@AuthenticationPrincipal ApiKeyPrincipal`에서 받는다** — 요청 본문에는 더 이상 없다.
- **테스트**: `AuthenticateApiKeyUseCaseTest`(단위, 정상/형식 오류/Prefix 미존재/Secret 불일치/폐기/만료/`LIVE` 환경/Merchant 상태 불가를 전부 커버), `MerchantApiKeyRepositoryAdapterTest`(Testcontainers MySQL 통합, Scope 왕복까지 확인), `PaymentControllerTest`는 `@Import(SecurityConfig::class)`로 실제 인가 규칙까지 검증한다(`SecurityMockMvcRequestPostProcessors.authentication(...)`으로 `Authentication`을 직접 주입 — `authenticateApiKeyUseCase`는 `SecurityConfig`의 Bean 그래프를 만족시키기 위한 Mock일 뿐 실제로 호출되지 않는다). 여기에 더해 실제 `bootRun` + `curl`로 HMAC 해시를 미리 심어둔 테스트 Key를 상대로 헤더 없음(401) → Secret 틀림(401) → 정상 Key로 결제 생성(201, `last_used_at` 갱신 확인)까지 수동으로 검증했다.

**Spring Boot 4.1 / Jackson 3.x로 넘어오며 자주 걸리는 패키지 함정 두 가지**(둘 다 `apps:api-payment`에서 처음 부딪혔다):

- `ObjectMapper`는 `com.fasterxml.jackson.databind`가 아니라 **`tools.jackson.databind`**에 있다 — Jackson 3.x부터 그룹 ID/패키지가 `tools.jackson`으로 바뀌었다(`jackson-module-kotlin`도 `tools.jackson.module:jackson-module-kotlin`). 이 좌표는 `build-logic`의 `practicepay.spring-boot-app` convention plugin(`CLAUDE.md`의 "build-logic" 절 참고)에 이미 그 흔적이 있다.
- `@WebMvcTest`는 `org.springframework.boot.test.autoconfigure.web.servlet`이 아니라 **`org.springframework.boot.webmvc.test.autoconfigure`**에 있다 — Spring Boot 4.x가 `spring-boot-autoconfigure`를 기술별 전용 모듈로 쪼갠 것과 같은 개편이다(`SpringTransactionProvider`가 `spring-boot-jooq` 모듈로 옮겨진 것과 동일한 패턴 — `CLAUDE.md`의 "영속성 Adapter 컨벤션" 절 참고). 새로운 Spring Boot 4.x 애노테이션/클래스를 쓸 때는 예전 패키지 경로를 그대로 가정하지 않는다.

## `api-admin`의 내부 운영자 로그인 컨트롤러

`POST /admin/login`(`docs/architecture/identity-access-api-key.md`의 "3.4 로그인 경로" 권장 경로)이 `AuthenticateInternalUserUseCase`를 HTTP로 노출한다. `api-payment`와 같은 패키지 구조(`api.admin.web`/`api.admin.config`/`api.admin.support`)를 따른다.

- **`AuthenticateInternalUserUseCase`**(`application.identity`)는 로그인 아이디/비밀번호만 검증하고 인증된 신원(`AuthenticateInternalUserResult`)만 돌려준다 — 세션은 전혀 다루지 않는다. `InternalUserRepository.findByLoginId` → 계정 상태 확인(`LOCKED`이고 잠금이 아직 안 풀렸으면 `AccountLockedException`, `ACTIVE`가 아니면 `InvalidCredentialsException`) → `PasswordEncoder.matches`로 비밀번호 확인 → 실패면 `InternalUser.recordFailedLogin` 기록(연속 [`MAX_FAILED_LOGIN_ATTEMPTS`]번째면 `InternalUser.lock`도 호출) 후 저장, 성공이면 `recordSuccessfulLogin` 저장. 로그인 아이디가 없거나 계정이 `INVITED`/`SUSPENDED`/`TERMINATED`인 경우도 전부 같은 `InvalidCredentialsException`으로 묶는다 — 계정 존재 여부나 상태를 호출부에 드러내지 않기 위해서다. `MAX_FAILED_LOGIN_ATTEMPTS`(5)/`LOCK_DURATION`(15분)은 `docs/`에 값이 없어서 `CreatePaymentUseCase`의 `SPREAD_RATE`처럼 고정한 MVP 값이다 — 원래 이 Use Case의 상수였는데, 가맹점 로그인과 같은 값을 복제하고 있어 나중에 공유 `LoginLockoutPolicy`로 옮겼다.
- **세션은 `AdminLoginController`가 만든다.** 로그인이 성공하면 `UsernamePasswordAuthenticationToken` + `ROLE_<InternalUserRole>` 권한으로 Spring Security `SecurityContext`를 만들고 `SecurityContextRepository`(`HttpSessionSecurityContextRepository`)로 세션에 저장한다 — 이후 요청은 이 세션 쿠키로 인증된다. `docs/`가 이 앱을 "PG 내부 관리자 **화면**"이라고 부르는 것에 맞춰(가맹점 서버 간 API Key/Bearer 인증인 `MerchantApiKey`와 다르게) 세션 쿠키 방식을 선택했다 — JWT 등 다른 방식으로 정해진 문서 근거는 없다.
- **`SecurityConfig`**: `/admin/login`만 인증 없이 열고 나머지는 인증을 요구한다. **알려진 gap: CSRF 보호를 꺼뒀다.** 세션 쿠키 인증에서 원래는 반드시 켜야 하지만, 이 학습용 MVP 단계에서는 아직 CSRF 토큰 발급/검증 흐름을 만들지 않았다 — 실제 프론트엔드가 이 API를 붙이기 전에 반드시 켜야 한다.
- **`InternalUserRepositoryAdapter`**(`modules:infra-persistence`)는 `PaymentRepositoryAdapter`와 같은 모양·같은 낙관적 잠금 한계를 가진다(`CLAUDE.md`의 "영속성 Adapter 컨벤션" 절 참고) — `internal_user`도 `version` 컬럼이 있는데 도메인 `InternalUser`는 그걸 모른다.
- **테스트**: `AuthenticateInternalUserUseCaseTest`(단위, 성공/미존재/오답/5회 오답 잠금/잠금 중 시도/잠금 만료 후 재시도/`INVITED` 계정을 전부 커버), `InternalUserRepositoryAdapterTest`(Testcontainers MySQL 통합), `AdminLoginControllerTest`(`@WebMvcTest(AdminLoginController::class)` + `@Import(SecurityConfig::class)` — 컨트롤러가 `SecurityContextRepository` Bean도 필요해서 `PaymentControllerTest`와 달리 `SecurityConfig`를 명시적으로 Import한다). 여기에 더해 실제 `bootRun` + `curl`로 BCrypt 해시를 미리 심어둔 테스트 계정을 상대로 로그인 성공(세션 쿠키 발급 확인) → 오답 5회 반복 → 잠김(`AccountLockedException`, DB의 `user_status=LOCKED` 확인)까지 수동으로 검증했다.

## `api-admin`의 내부 운영자 발급 컨트롤러

`POST /admin/internal-users`(`docs/architecture/identity-access-api-key.md`의 "3.3 발급 정책": "내부 운영자 계정은 SUPER_ADMIN만 발급할 수 있다")가 새 `IssueInternalUserUseCase`를 HTTP로 노출한다. `docs/`에 이 경로 자체가 정해져 있진 않아 `/admin/login`과 같은 리소스 계층에 `POST /api/v1/payments`와 같은 REST 관례로 새로 정했다.

- **발급 = `InternalUser(INVITED)` + `AccountInvitation(PENDING)`을 한 트랜잭션으로.** `docs/database/database-design.md`의 가맹점 등록 트랜잭션 예시(`Merchant + MerchantUser(OWNER, INVITED) + AccountInvitation`)와 같은 모양이다 — `IssueInternalUserUseCase`가 `InternalUser.invite(...)`와 `AccountInvitation.forInternalUser(...)`를 만들어 `TransactionManager.runInTransaction { }` 안에서 함께 저장한다(`CreatePaymentUseCase`와 같은 다중 Aggregate 생성 패턴). 초대를 수락해 비밀번호를 설정하고 `INVITED → ACTIVE`로 전이하는 흐름(활성화)은 별도 Use Case `AcceptAccountInvitationUseCase`로 구현했다(아래 "초대 수락(활성화) Use Case" 절 참고) — 로그인 흐름이 발급보다 먼저 별도로 구현됐던 것과 같은 이유로, 발급과는 다른 시점에 별개로 만들어졌다.
- **초대 Token은 저장하지 않고 Hash만 저장한다** — `AccountInvitation`의 KDoc과 그대로 일치한다. 원문 Token은 `IdGenerator.newId()`로 만든다(별도의 "랜덤 문자열 생성" Port를 새로 만들지 않고 기존 Port를 재사용했다). Hash는 새 Port `InvitationTokenHasher`(`hash`/`matches`, `ApiKeySecretHasher`와 완전히 같은 모양)로 만들고, `api-admin`의 `HmacInvitationTokenHasher`가 HMAC-SHA-256 + Pepper로 구현한다 — **API Key Pepper(`app.api-key.pepper`)와는 별도의 설정값(`app.invitation-token.pepper`)을 쓴다**, 한쪽 비밀값이 새도 다른 쪽까지 같이 위험해지지 않도록 하려는 의도적 분리다. `INVITATION_VALIDITY`(7일)는 `docs/`에 값이 없어 `CreatePaymentUseCase`의 `PAYMENT_VALIDITY`와 같은 성격의 MVP 상수로 고정했다. 응답의 `invitationToken`은 API Key 원문과 같은 규칙(`docs/`의 "6.4 저장 정책")으로 **이 응답에서만** 원문으로 보인다.
- **`loginId`/`email` 중복은 사전에 막는다.** 둘 다 `internal_user`의 DB Unique 제약(`uk_internal_user_login_id`/`uk_internal_user_email`)이 걸려 있어, 체크 없이 두면 raw SQL 에러가 새 나간다 — `InternalUserRepository`에 (기존 `findByLoginId`에 더해) `findByEmail`을 추가해서 둘 다 사전 조회하고, 겹치면 `DuplicateInternalUserException`(409)을 던진다. `CreatePaymentUseCase`의 멱등성 체크와 같은 성격의 한계다(DB Unique 제약만큼 원자적이지 않다).
- **호출자 식별을 위해 `InternalUserPrincipal`을 새로 도입했다.** `AdminLoginController`는 원래 `Authentication.principal`에 로그인 아이디 문자열만 심었는데, 발급 감사 정보(`createdByInternalUserId`)로 쓸 `InternalUserId`가 필요해서 `apps:api-payment`의 `ApiKeyPrincipal` 패턴을 그대로 가져와 `InternalUserPrincipal(internalUserId, loginId, role)`을 로그인 성공 시 principal로 심도록 `AdminLoginController`를 바꿨다. `InternalUserIssuanceController`는 `@AuthenticationPrincipal InternalUserPrincipal`로 발급자를 바로 받는다 — `PaymentController`가 `merchantId`를 요청 본문 대신 `ApiKeyPrincipal`에서 가져오는 것과 같은 이유다.
- **`SecurityConfig`에 역할 기반 인가가 처음 등장했다.** `authorize("/admin/internal-users", hasRole("SUPER_ADMIN"))`를 `anyRequest`보다 먼저 추가했다(Spring Security는 먼저 매칭되는 규칙을 쓴다). `SUPER_ADMIN`이 아닌 인증된 세션이 호출하면 Spring Security 기본 `AccessDeniedHandler`가 403을 돌려준다 — `apps:api-payment`의 Scope 인가(`PaymentControllerTest`의 403 케이스)와 같은 수준으로, 커스텀 JSON 바디를 만들지 않는다. 세션이 아예 없으면(로그인 안 함) 이 앱은 커스텀 `AuthenticationEntryPoint`가 없어서 Spring Security 기본 동작대로 403이 돈다(실제 `bootRun` + `curl`로 확인) — `api-payment`가 `ApiKeyAuthenticationEntryPoint`로 401 JSON 바디를 통일한 것과 달리, `api-admin`은 아직 이 부분을 커스텀하지 않았다.
- **예외 핸들러 이름을 바꿨다.** `AdminAuthExceptionHandler` → `AdminApiExceptionHandler`(로그인 전용이 아니게 됐으므로 `PaymentApiExceptionHandler`와 이름 패턴을 맞췄다) — `DuplicateInternalUserException`(409)과 `IllegalArgumentException`(400, Value Object `require()` 실패나 `InternalUserRole.valueOf()` 실패를 공통 처리, `PaymentApiExceptionHandler`와 완전히 같은 패턴)을 새로 추가했다.
- **`IdGenerator`가 `api-admin`에 처음 필요해졌다** — 당시에는 `apps:api-payment`의 `UuidIdGenerator`를 복제해 각 앱이 자기 `support` 패키지에 자체 구현을 갖게 했지만, 지금은 `modules:infra-support`의 공유 구현을 쓴다(`CLAUDE.md`의 "공용 Port 구현(modules:infra-support)" 절 참고).
- **`AccountInvitationRepositoryAdapter`**(`modules:infra-persistence`)는 `account_invitation`에 `version` 컬럼이 없어서(`AccountInvitation`의 KDoc 참고) `InternalUserRepositoryAdapter`와 달리 낙관적 잠금 없이 단순 UPDATE로 상태 전이를 반영한다. 발급(INSERT) 시점부터 Port 계약(`save`가 상태 전이도 반영해야 함)을 절반만 구현해 두지 않으려고 `accept`/`expire`/`revoke` 이후의 UPDATE 경로도 함께 만들어 뒀는데, `AcceptAccountInvitationUseCase`가 그 `accept` UPDATE 경로를 처음 실제로 호출하는 지점이 됐다(아래 "초대 수락(활성화) Use Case" 절 참고).
- **테스트**: `IssueInternalUserUseCaseTest`(단위, 정상 발급/로그인 아이디 중복/이메일 중복), `AccountInvitationRepositoryAdapterTest`+`InternalUserRepositoryAdapterTest`의 `findByEmail` 케이스(Testcontainers MySQL 통합), `InternalUserIssuanceControllerTest`(`@WebMvcTest` + `@Import(SecurityConfig::class)`, `PaymentControllerTest`의 `SecurityMockMvcRequestPostProcessors.authentication(...)` 패턴으로 `InternalUserPrincipal`을 주입해 `SUPER_ADMIN`/`OPERATOR` 인가까지 검증). 여기에 더해 실제 `bootRun` + `curl`로 SUPER_ADMIN 로그인 → 발급(201, `invitationToken` 확인, DB에 `internal_user`+`account_invitation` 행 생성 확인) → 중복 loginId/email(둘 다 409) → 세션 없음(403) → 잘못된 role(400)까지 검증한 뒤 DB 행을 정리했다.
- **단위 테스트에서 걸린 함정: MockK의 `any()`가 값 클래스(Value Class)를 만들지 못할 수 있다.** `every { internalUserRepository.findByEmail(any()) } returns null`처럼 `Email` 타입 매개변수에 `any()`를 쓰면, MockK가 매처 서명을 만들려고 무작위 문자열로 `Email` 인스턴스를 생성하려 시도하는데 `Email`의 `init { require(value.contains("@")) }` 검증에 걸려 `IllegalArgumentException`이 난다(`LoginId`처럼 검증이 "공백 아님" 정도로 느슨한 값 클래스는 무작위 문자열이 통과해서 문제가 없다). 해결: `any()` 대신 실제 값(`findByEmail(EMAIL)`)으로 정확히 매칭한다 — 이런 종류의 값 클래스 매개변수에는 앞으로도 `any()`를 피한다.

## 가맹점 등록 Use Case(`RegisterMerchantUseCase`, `application.identity`)와 `api-admin`의 등록 컨트롤러

`POST /admin/merchants`(`docs/architecture/identity-access-api-key.md`의 "4.3 가맹점 등록과 OWNER 생성": "가맹점 등록 트랜잭션에서 `Merchant`와 최초 `MerchantUser(OWNER)`를 함께 생성한다")가 새 `RegisterMerchantUseCase`를 HTTP로 노출한다. `Merchant.create`/`MerchantUser.inviteInitialOwner`/`AccountInvitation.forMerchantUser` 도메인 팩토리는 전부 이전부터 있었다 — 이 Use Case가 그 셋을 실제로 처음 함께 호출하는 자리다.

- **`IssueInternalUserUseCase`의 "발급 + 초대" 패턴을 Aggregate 셋으로 넓혔다.** `Merchant(ACTIVE)` + `MerchantUser(OWNER, INVITED)` + `AccountInvitation(PENDING)`을 `TransactionManager.runInTransaction { }` 안에서 함께 저장한다 — `docs/database/database-design.md`의 "계정 생성 트랜잭션" 예시가 정확히 이 모양이다(아래 OutboxEvent 관련 예외 참고). 초대 수락(`INVITED → ACTIVE`)은 기존 `AcceptAccountInvitationUseCase`를 그대로 재사용한다 — 이미 `InvitationAccountType.MERCHANT_USER`를 처리하고 `api-merchant`가 `POST /merchant/account-invitations/accept`로 노출해 둔 상태라 새로 만들 게 없었다.
- **`docs/database/database-design.md`의 예시와 달리 `OutboxEvent`는 만들지 않는다 — 의도적인 이탈이다.** 그 문서의 "가맹점 등록" 트랜잭션 예시는 `OutboxEvent INSERT`를 포함하지만, `PublishOutboxEventUseCase.resolveMerchant()`는 오늘 `aggregateType="Payment"`만 지원해서 다른 타입을 만들면 `apps:batch`의 발행 Worker가 매 폴링마다 예외를 던지며 영원히 재시도하는 상태로 남는다(발행 대상에서 스스로 빠지지 않는다). 애초에 이 프로젝트에는 이메일 발송 인프라가 없어서 그 `OutboxEvent`가 실제로 무엇을 전달할지도 정해진 바 없다 — `IssueInternalUserUseCase`가 이미 같은 이유로 `InternalUser` 초대에 `OutboxEvent`를 만들지 않은 선례를 그대로 따랐다: `invitationToken` 원문을 API 응답으로 직접 돌려주고, 호출한 내부 운영자가 OWNER에게 수동으로(Out-of-band) 전달한다.
- **`merchantCode` 중복은 사전에 막지만, `ownerLoginId`/`ownerEmail`은 확인하지 않는다.** `merchant_code`는 `uk_merchant_merchant_code` 전역 Unique라서 `MerchantRepository.findByCode`로 사전 조회하고 겹치면 `DuplicateMerchantException`(409)을 던진다(`IssueInternalUserUseCase`의 `loginId`/`email` 중복 확인과 같은 한계 — DB Unique 제약만큼 원자적이지 않다). 반면 `merchant_user`의 Unique 제약은 `merchant_seq + login_id`/`merchant_seq + email`로 가맹점 안에서만 유일해서(`docs/database/database-design.md`의 "주요 Unique"), 이 Use Case가 항상 새로 만드는 `merchant_seq`에는 애초에 충돌할 기존 행이 없다 — 그래서 OWNER 쪽은 사전 조회 자체가 불필요하다.
- **`MerchantRepository` Port에 `save`를 처음 추가했다.** 원래 "조회만 필요해 `findBy...`만 정의한다 — 등록·상태 변경 Use Case가 추가될 때 `save` 등을 함께 확장한다"고 Port KDoc에 미리 적어뒀던 그 시점이다. `MerchantRepositoryAdapter.save`는 `PaymentRepositoryAdapter`와 같은 모양·같은 낙관적 잠금 한계를 가진다.
- **누가 호출할 수 있는지: `SUPER_ADMIN`뿐 아니라 `OPERATOR`도 허용한다.** `POST /admin/internal-users`(`SUPER_ADMIN` 전용)와 다른 부분이다 — "3.2 MVP 역할"이 `OPERATOR`의 업무를 "가맹점·결제·운영 업무"로 정의해서, 내부 계정 발급과 달리 가맹점 등록은 `OPERATOR`의 정상 업무 범위로 판단했다. `SecurityConfig`에 `authorize(HttpMethod.POST, "/admin/merchants", hasAnyRole("SUPER_ADMIN", "OPERATOR"))`를 추가했다 — 뒤에 `GET /admin/merchants`(목록 조회, 아래 절 참고)가 생기면서 `HttpMethod.POST`로 메서드를 좁혔다. 처음엔 메서드 없이 경로만으로 걸었었는데, 그러면 같은 경로의 `GET`까지 이 역할 제약에 걸려 `VIEWER`가 목록을 못 보게 된다 — 실제로 이 문제를 만들 뻔했다가 잡았다.
- **컨트롤러 이름을 `MerchantRegistrationController` → `MerchantController`로 바꿨다** — 등록 전용이 아니게 됐다(`AdminAuthExceptionHandler` → `AdminApiExceptionHandler`와 같은 이유의 리네임, 아래 "가맹점 목록 조회" 절 참고).
- **테스트**: `RegisterMerchantUseCaseTest`(단위, 정상 등록/가맹점 코드 중복), `MerchantRepositoryAdapterTest`에 `save` 케이스 추가(신규 삽입/기존 행 상태 갱신, Testcontainers MySQL 통합), `MerchantControllerTest`(`@WebMvcTest` + `@Import(SecurityConfig::class)`, `SUPER_ADMIN`/`OPERATOR` 둘 다 201, `VIEWER`는 403, 인증 없음은 401/403). 여기에 더해 실제 `bootRun`(`api-admin` + `api-merchant` 동시 기동) + `curl`로 SUPER_ADMIN 로그인 → 가맹점 등록(201, `invitationToken` 확인) → **그 토큰을 `api-merchant`의 `POST /merchant/account-invitations/accept`에 그대로 제출해 실제로 수락 성공(200)** → 새 OWNER로 `api-merchant` 로그인 성공 → 가맹점 코드 재등록 시도(409)까지 발급→수락→로그인 전체 흐름을 앱 두 개에 걸쳐 검증했다. 이 마지막 단계가 `api-admin`/`api-merchant`의 `app.invitation-token.pepper`가 실제로 일치해야 한다는 제약(위 "설정과 비밀값" 절)이 처음으로 실전에서 작동하는 지점이었다 — 검증 후 DB 행은 정리했다.

## 가맹점 목록 조회 Use Case(`ListMerchantsUseCase`, `application.merchant`)와 `api-admin`의 `MerchantController`

`GET /admin/merchants`가 새 `ListMerchantsUseCase`를 HTTP로 노출한다. `docs/`에 이 흐름 자체가 정해져 있진 않아 `POST /admin/merchants`(가맹점 등록)와 같은 리소스 계층에 REST 관례로 새로 정했다 — 두 메서드 다 `MerchantController`(옛 `MerchantRegistrationController`) 하나가 담당한다(`MerchantApiKeyController`가 `api-merchant`에서 발급/폐기를 한 컨트롤러로 묶은 것과 같은 모양).

- **`MerchantRepository`(Command Repository)에 `findAll`을 추가하지 않고, 별도 Projection Port `MerchantListProjection`을 새로 만들었다.** `docs/architecture/persistence-jooq.md`의 "Command Repository는 Aggregate를 저장하고 복원한다. 복잡한 조회는 전용 jOOQ Projection을 사용한다"는 원칙을 이 프로젝트에서 처음 실제로 적용한 사례다. 화면용 목록은 `Merchant` Aggregate 전체(낙관적 잠금 `version` 포함)를 복원할 필요가 없어서, `MerchantListProjectionAdapter`가 jOOQ Record에서 곧바로 `MerchantSummary`(목적에 맞게 좁힌 읽기 전용 모델)로 매핑한다 — `Merchant.reconstitute`를 거치지 않는다. `MerchantRepositoryAdapter`와 같은 `merchant` 테이블을 보지만 클래스는 분리했다.
- **`ListMerchantsUseCase`에는 `Command`가 없다.** 필터·페이지네이션이 없는 MVP 단순화(알려진 gap — `MerchantListProjection`의 KDoc에 남겼다) 때문에 이 Use Case가 실제로 받을 수 있는 입력이 없어서, 의미 없는 빈 `Command` 클래스를 만들지 않았다. 이 프로젝트에서 아그리게이트별 서브패키지(`application.merchant`)를 갖는 첫 순수 조회 Use Case이기도 하다 — `RegisterMerchantUseCase`가 `application.identity`에 있는 건 그 복잡도가 대부분 초대·식별 쪽이기 때문이지 "Merchant 관련은 다 `merchant` 패키지"라는 규칙은 아니다(`ConnectCheckoutWalletUseCase`가 세운 논리와 같다).
- **호출 권한 확인이 다른 Identity/Access Use Case들과 다르다 — `InternalUser`에는 동적으로 재확인할 권한 메서드가 없다.** `MerchantUser`의 `canInviteSubAccounts()`/`canManageApiKeys()`와 달리 `InternalUser` 도메인에는 대응하는 `can*` 메서드가 아예 없어서, 여기서는 `IssueInternalUserUseCase`와 같은 원칙(정적 역할 검사를 inbound Adapter에 맡긴다)을 그대로 따른다. 그런데 그 정적 검사 자체가 실질적으로 "제약 없음"이다 — `InternalUserRole`의 KDoc이 `VIEWER`를 "조회 전용"으로 정의해서 세 역할 모두 조회를 볼 수 있어야 하고, 그건 곧 인증된 내부 사용자 전원을 뜻하기 때문이다. 그래서 `SecurityConfig`는 `GET /admin/merchants`에 별도 규칙을 두지 않고 기본 `authorize(anyRequest, authenticated)`에 맡긴다(바로 위 "가맹점 등록" 절의 `HttpMethod.POST` 스코핑이 이걸 가능하게 한 전제 조건이다).
- **최신 등록순(`created_at DESC`)으로 정렬한다** — 관리 화면에서 방금 등록한 가맹점을 바로 확인할 수 있게 하려는 선택이고, `docs/`에 근거는 없다.
- **테스트**: `ListMerchantsUseCaseTest`(단위, Projection이 돌려주는 값을 그대로 반환/빈 목록), `MerchantListProjectionAdapterTest`(Testcontainers MySQL 통합 — 삽입한 가맹점이 포함되는지, `createdAt` 내림차순으로 상대 순서가 맞는지. 공유 Testcontainers DB에 다른 테스트가 심어둔 행이 섞여 있을 수 있어 목록 전체 크기·절대 순서는 단정하지 않는다), `MerchantControllerTest`에 목록 조회 케이스 추가(`SUPER_ADMIN`/`OPERATOR`/`VIEWER` 셋 다 200, 인증 없음은 401/403, 빈 목록). 여기에 더해 실제 `bootRun` + `curl`로 SUPER_ADMIN 로그인 → 목록 조회(200, 시드 가맹점 확인) → **VIEWER를 발급·수락·로그인시켜 그 VIEWER 세션으로 목록 조회(200) → 같은 세션으로 가맹점 등록 시도(403)** → 새 가맹점 등록 후 목록 재조회로 정렬 확인(방금 등록한 가맹점이 맨 앞)까지 확인했다. VIEWER의 GET 성공/POST 실패 대비가 이번 변경에서 가장 위험했던 지점(SecurityConfig 메서드 스코핑을 빠뜨리면 VIEWER가 조회조차 못 하게 된다)이라 실제 세션으로 직접 확인했다 — 검증 데이터는 정리했다.

## `api-merchant`의 가맹점 관리자 로그인 컨트롤러

`POST /merchant/login`(`docs/architecture/identity-access-api-key.md`의 "4.5 로그인 경로" 권장 경로)이 `AuthenticateMerchantUserUseCase`를 HTTP로 노출한다. `api-admin`의 로그인 컨트롤러와 거의 모든 게 같다(같은 패키지 구조, 같은 `SecurityConfig`/세션 쿠키 방식, 같은 CSRF-꺼짐 gap, 같은 잠금 정책 상수) — 차이만 적는다:

- **가맹점부터 특정해야 한다.** `login_id`는 가맹점 안에서만 유일하다(`merchant_seq + login_id` — `docs/database/database-design.md`의 "주요 Unique") — `InternalUser`처럼 `loginId`만으로 계정을 찾을 수 없다. 그래서 `MerchantLoginRequest`/`AuthenticateMerchantUserCommand`는 `merchantCode`(사람이 읽는 가맹점 코드)를 함께 받고, Use Case가 `MerchantRepository.findByCode`로 가맹점을 먼저 확정한 다음 `MerchantUserRepository.findByMerchantIdAndLoginId`로 계정을 찾는다. 가맹점 코드가 틀려도 같은 `InvalidCredentialsException`을 던진다(가맹점 존재 여부도 노출하지 않는다) — 이걸 위해 `MerchantRepository` Port에 `findByCode`를 추가했다(기존엔 `findById`만 있었다).
- **가맹점 자체의 상태는 로그인 가능 여부에 영향을 주지 않는다.** `Merchant`가 `SUSPENDED`여도 그 가맹점의 관리자는 이유를 확인하러 로그인할 수 있어야 한다는 판단이다 — 문서에 명시된 규칙은 아니고, `AuthenticateMerchantUserUseCase`의 KDoc에 그렇게 남겨뒀다.
- **`MerchantUserRepositoryAdapter`**(`modules:infra-persistence`)는 `InternalUserRepositoryAdapter`와 같은 모양이지만 FK가 하나 더 있다 — `merchant_seq`(소속 가맹점)에 더해 `invited_by_internal_user_seq`/`invited_by_merchant_user_seq`(둘 다 nullable, 초대자 감사 정보)까지 resolve한다.
- **`Authentication.principal`에 `MerchantUserPrincipal`을 심는다** — 원래는 `result.loginId.value`(문자열)만 심었지만, `InviteMerchantSubAccountUseCase`가 감사 정보(`invitedByMerchantUserId`)와 발급 대상 가맹점(`merchantId`)을 세션에서 바로 가져와야 해서 확장했다(아래 "하위 계정 발급 Use Case" 절 참고) — `api-admin`이 `InternalUserPrincipal`을 도입했던 것과 같은 이유·같은 시점의 변화다.

## 하위 계정 발급 Use Case(`InviteMerchantSubAccountUseCase`, `application.identity`)와 `api-merchant`의 발급 컨트롤러

`POST /merchant/merchant-users`(`docs/architecture/identity-access-api-key.md`의 "4.4 하위 계정 발급": "`OWNER`, `ADMIN`은 하위 계정을 발급할 수 있다")가 새 `InviteMerchantSubAccountUseCase`를 HTTP로 노출한다. `MerchantUser.inviteSubAccount`는 이전부터 있었다 — 이 Use Case가 그걸 실제로 처음 호출하는 자리다.

- **발급 권한을 정적 역할 검사가 아니라 `MerchantUser.canInviteSubAccounts()`로 동적으로 확인한다 — `IssueInternalUserUseCase`/`RegisterMerchantUseCase`와 의도적으로 다른 선택이다.** 그 두 Use Case의 Command KDoc은 "발급 권한 확인은 inbound Adapter(세션의 역할)가 끝냈다고 전제한다"고 명시하는데, 여기서는 `invitedByMerchantUserId`로 요청자의 `MerchantUser`를 다시 읽어 `canInviteSubAccounts()`를 호출한다. `canInviteSubAccounts()`가 이미 도메인에 존재하는데 이 Use Case가 생기기 전까지 어디서도 호출되지 않고 있었다는 게 결정적 근거였다 — 정적 역할 검사만으로 충분했다면 이 메서드가 있을 이유가 없다. `ACTIVE` 상태까지 함께 검증하는 것 자체가 세션의 역할 스냅샷만으로는 부족하다는 뜻으로 읽었다.
  - **실제로 이 차이가 의미 있는 상황을 `bootRun`으로 재현해서 확인했다.** ADMIN으로 로그인해 세션을 살려둔 채로 그 계정을 DB에서 직접 `SUSPENDED`로 바꾼 뒤 같은 세션으로 다시 하위 계정 발급을 시도했다 — `SecurityConfig`의 정적 `hasAnyRole("OWNER", "ADMIN")`은 세션에 캐싱된 `ROLE_ADMIN` 권한을 그대로 통과시켰지만, Use Case가 요청자를 다시 읽어 `canInviteSubAccounts()`를 호출한 덕분에 정확히 `MerchantUserCannotInviteSubAccountsException`(403, `"...role=ADMIN, status=SUSPENDED)."`)으로 막혔다. 정적 검사만 있었다면 이 요청은 그대로 통과했을 것이다.
- **어느 가맹점에 계정을 만들지도 같은 조회로 함께 얻는다(`inviter.merchantId`) — 요청 본문으로 받지 않는다.** `RegisterMerchantUseCase`는 항상 새 가맹점을 만들어서 `merchantId` 문제가 없었지만, 이 Use Case는 기존 가맹점에 끼워 넣는 것이라 그 가맹점이 어디인지를 신뢰할 수 있는 곳(방금 DB에서 읽은 요청자 자신의 소속)에서 가져와야 한다 — 요청 본문에 `merchantId`를 받으면 호출자가 임의의 값을 실어 보내 남의 가맹점에 계정을 만드는 멀티테넌시 취약점이 생긴다(`MerchantUserPrincipal`의 KDoc에도 같은 내용을 남겼다).
- **`loginId`/`email` 중복을 이번엔 사전에 확인해야 한다 — `RegisterMerchantUseCase`와 다른 점이다.** `RegisterMerchantUseCase`는 항상 새 `merchant_seq`를 만들어서 충돌할 기존 행이 없었지만, 이 Use Case는 기존 `merchant_seq`에 끼워 넣으므로 실제로 겹칠 수 있다. `MerchantUserRepository`에 (기존 `findByMerchantIdAndLoginId`에 더해) `findByMerchantIdAndEmail`을 추가해서 둘 다 사전 조회하고, 겹치면 `DuplicateMerchantUserException`(409)을 던진다.
- **`OWNER`는 이 경로로 만들 수 없다.** `MerchantUser.inviteSubAccount` 자체가 `require(role != MerchantUserRole.OWNER)`를 갖고 있어서, 컨트롤러가 별도로 막지 않아도 `IllegalArgumentException`(400)으로 자연스럽게 걸린다 — 실제 `curl`로 `role: "OWNER"`를 보내 정확히 그 도메인 예외 메시지가 그대로 400 응답에 실리는 것까지 확인했다.
- **예외 핸들러 이름을 바꿨다.** `MerchantAuthExceptionHandler` → `MerchantApiExceptionHandler`(로그인 전용이 아니게 됐으므로 `AdminAuthExceptionHandler` → `AdminApiExceptionHandler`와 같은 이름 패턴을 맞췄다) — 이 김에 이전까지 없었던 `IllegalArgumentException`(400) 핸들러도 추가했다(`AdminApiExceptionHandler`/`PaymentApiExceptionHandler`와 같은 패턴, 원래 gap이었다).
- **`SecurityConfig`에 역할 기반 인가가 두 번째로 등장했다.** `authorize("/merchant/merchant-users", hasAnyRole("OWNER", "ADMIN"))`를 추가했다 — `VIEWER`가 호출하면 Use Case에 닿기도 전에 Spring Security가 403으로 막는다(실제 확인).
- **`modules:infra-support`에서 `infra.support.id`를 처음으로 스캔에 추가했다.** 이 앱은 지금까지 새 ID를 만드는 Use Case가 없어서 `UuidIdGenerator` Bean을 스캔하지 않았다(`MerchantApiApplication`의 예전 KDoc에 그렇게 적혀 있었다) — 이 Use Case가 그 첫 사례다.
- **테스트**: `InviteMerchantSubAccountUseCaseTest`(단위, OWNER가 ADMIN 발급/ADMIN도 발급 가능/VIEWER는 예외/`SUSPENDED` OWNER는 역할이 맞아도 예외/loginId 중복/email 중복/OWNER 발급 시도는 `IllegalArgumentException`), `MerchantUserRepositoryAdapterTest`에 `findByMerchantIdAndEmail` 케이스 추가(Testcontainers MySQL 통합), `MerchantSubAccountControllerTest`(`@WebMvcTest` + `@Import(SecurityConfig::class)`, OWNER/ADMIN 둘 다 201, VIEWER는 403, 인증 없음은 401/403, 각 예외의 상태 코드 매핑까지 검증). 여기에 더해 실제 `bootRun`(`api-merchant`만 기동, 시드 OWNER로 시작) + `curl`로 OWNER 로그인 → ADMIN 발급(201) → 수락(200) → ADMIN 로그인(200) → **그 ADMIN이 다시 VIEWER 발급(201)** → 수락 → VIEWER 로그인 → VIEWER의 발급 시도(403, `SecurityConfig` 차단) → loginId/email 중복(각각 409) → OWNER 역할 시도(400) → **위에 적은 `SUSPENDED` 동적 검사**까지 전부 확인한 뒤 DB 행을 정리했다. 이 과정에서 한글 `userName`을 담은 `curl` 요청이 Git Bash에서 CP949로 나가 파싱에 실패하는(이전에 이미 겪은) 문제를 다시 만났다 — ASCII로 바꿔 재확인했다.

## API Key 발급/폐기 Use Case(`IssueMerchantApiKeyUseCase`/`RevokeMerchantApiKeyUseCase`, `application.apikey`)와 `api-merchant`의 발급/폐기 컨트롤러

`POST /merchant/api-keys`(발급)와 `DELETE /merchant/api-keys/{merchantApiKeyId}`(폐기)가 `docs/architecture/identity-access-api-key.md`의 "6.6 발급 권한"("`OWNER`, `ADMIN`은 발급/폐기할 수 있다")을 구현한다. `MerchantApiKey.create`/`revoke`는 이전부터 있었다 — 이 두 Use Case가 그걸 실제로 처음 호출하는 자리다. `AuthenticateApiKeyUseCase`(인증, `api-payment`가 씀)와 같은 `application.apikey` 패키지에 둔다.

- **발급 권한 확인은 `InviteMerchantSubAccountUseCase`와 완전히 같은 방식이다** — 정적 역할 검사가 아니라 요청자의 `MerchantUser`를 다시 읽어 `canManageApiKeys()`를 동적으로 호출한다(그 Use Case의 KDoc에 적은 이유와 같다: 이 도메인 메서드도 지금까지 어디서도 호출되지 않고 있었다). `SecurityConfig`의 `hasAnyRole("OWNER", "ADMIN")`은 1차 정적 관문일 뿐이다.
  - **`SUSPENDED` 동적 검사를 API Key 발급에서도 실제로 재현해 확인했다.** 시드 OWNER로 로그인해 세션을 살려둔 채 그 계정을 DB에서 직접 `SUSPENDED`로 바꾸고 같은 세션으로 발급을 시도했더니, 정적 역할 검사는 그대로 통과시켰지만 Use Case가 정확히 `MerchantUserCannotManageApiKeysException`(403, `"...role=OWNER, status=SUSPENDED)."`)으로 막았다 — `InviteMerchantSubAccountUseCase`에서 확인한 것과 같은 결과다.
- **폐기는 대상이 요청자와 같은 가맹점 소속인지 직접 검사한다.** `MerchantApiKeyRepository.findById`는 ID로만 조회해서 다른 가맹점의 Key도 그대로 돌려주므로, `RevokeMerchantApiKeyUseCase`가 `apiKey.merchantId == revoker.merchantId`를 확인한다. 존재하지 않음과 다른 가맹점 소속을 같은 `MerchantApiKeyNotFoundException`(404)으로 가린다 — 다른 가맹점의 Key ID를 무차별 대입으로 탐색하지 못하게 하려는 것이다(`AcceptAccountInvitationUseCase`의 `InvalidInvitationException`과 같은 철학).
- **`MerchantApiKey.revoke()`를 부르기 전에 `isUsable()`을 먼저 확인한다.** 이미 `REVOKED`/`EXPIRED`인 Key를 그대로 다시 부르면 도메인의 `checkTransition`이 `IllegalStateException`을 던지는데, 이 예외는 어느 컨트롤러도 매핑을 갖고 있지 않아 그대로 두면 raw 500으로 샌다 — `AcceptAccountInvitationUseCase`가 `AccountInvitation.accept()`를 부르기 전에 `status == PENDING`을 먼저 확인하는 것과 같은 이유로, `MerchantApiKeyNotActiveException`(409)을 먼저 던진다.
- **`scopes`를 MVP가 허용하는 값(`PAYMENT_CREATE`/`PAYMENT_READ`)으로 제한한다.** `ApiKeyScope`에는 `REFUND_CREATE`/`REFUND_READ`/`SETTLEMENT_READ`도 있지만(스키마의 CHECK 제약이 이미 나열해 둔 값들, `docs/`의 "MVP에서는 쓰지 않는다") 도메인 레벨의 `require`가 없어서, `IssueMerchantApiKeyUseCase`가 직접 검증해 벗어나면 `IllegalArgumentException`(400)을 던진다 — `MerchantUser.inviteSubAccount`가 `require(role != OWNER)`로 스스로를 지키는 것과 달리 이 제약은 Use Case가 대신 짊어진다.
- **API Key 형식(`sk_test_<prefixToken>_<secret>`)이 `AuthenticateApiKeyUseCase.extractPrefix()`의 파싱 규칙과 정확히 맞아야 한다.** `prefixToken`은 `idGenerator.newId().take(8)`(`ApiKeyPrefix`의 KDoc 예시 `sk_test_ab12cd34`와 같은 길이), `secret`은 `idGenerator.newId()` 그대로다. 단위 테스트가 발급 결과를 `extractPrefix`와 같은 방식(`split("_", limit=4)`)으로 되잘라 같은 `keyPrefix`가 나오는지 확인한다 — 발급이 만드는 형식과 인증이 파싱하는 형식이 어긋나면 방금 발급한 Key로 곧바로 인증에 실패하는 상황이 생긴다.
- **새로 생긴 Pepper 공유 제약: `app.api-key.pepper`를 이제 `api-payment`와 `api-merchant`가 공유해야 한다.** `api-payment`의 그 설정 주석이 원래 "API Key를 발급·검증하는 앱이 여기뿐이라 다른 앱과 값을 맞출 필요가 없다"고 적혀 있었는데, 이 Use Case가 생기면서 그 전제가 깨졌다 — `api-merchant`가 발급(`hash(rawApiKey)`)하고 `api-payment`가 인증(`matches(rawApiKey, secretHash)`)하므로, 두 앱의 Pepper가 다르면 방금 발급한 Key로 결제 API를 호출해도 401이 난다(초대 Token Pepper와 같은 구조의 제약, `CLAUDE.md`의 "설정과 비밀값" 절 참고). 두 앱의 `application.yaml` 주석을 서로를 가리키도록 갱신했다.
- **`api-merchant`의 컴포넌트 스캔에 `infra.support.apikey`를 처음 추가했다.** `HmacApiKeySecretHasher`가 이 하위 패키지에 있는데, 지금까지 이 앱은 API Key를 다루지 않아 스캔하지 않았다.
- **테스트**: `IssueMerchantApiKeyUseCaseTest`(단위, OWNER 발급/VIEWER는 예외/`SUSPENDED` OWNER는 예외/빈 scopes는 `IllegalArgumentException`/MVP 밖 scope는 `IllegalArgumentException`/`extractPrefix`와의 형식 일치), `RevokeMerchantApiKeyUseCaseTest`(단위, OWNER가 자기 가맹점 Key 폐기/VIEWER는 예외/존재하지 않는 Key/다른 가맹점 Key는 같은 404/이미 REVOKED인 Key는 409), `MerchantApiKeyRepositoryAdapterTest`에 `findById` 케이스 추가(Testcontainers MySQL 통합), `MerchantApiKeyControllerTest`(`@WebMvcTest` + `@Import(SecurityConfig::class)`, 발급 POST와 폐기 DELETE 양쪽 다 같은 와일드카드 인가 규칙에 걸리는지 포함해 13개 케이스 — 아래 "API Key 목록 조회" 절에서 `GET` 케이스 5개를 이 같은 파일에 더했다). 여기에 더해 실제 `bootRun`(`api-merchant` + `api-payment` 동시 기동, 시드 OWNER로 시작) + `curl`로 OWNER 로그인 → API Key 발급(201) → **그 원문 Key로 `api-payment`의 `POST /api/v1/payments` 호출해 결제 생성 성공(201)** → 폐기(200) → **폐기된 Key로 재시도해 401** → 이미 폐기된 Key 재폐기(409) → 존재하지 않는 Key 폐기(404) → MVP 밖 scope 발급 시도(400) → 위에 적은 `SUSPENDED` 동적 검사(403)까지 두 앱에 걸쳐 실전 확인했다. 검증 데이터는 정리했다.

## API Key 목록 조회 Use Case(`ListMerchantApiKeysUseCase`, `application.apikey`)와 `api-merchant`의 목록 조회 API

`GET /merchant/api-keys`가 새 `ListMerchantApiKeysUseCase`를 HTTP로 노출한다 — 발급/폐기에 이어 `docs/architecture/identity-access-api-key.md`의 "6.6 발급 권한"이 나열한 세 능력(발급/폐기/목록 조회) 중 마지막을 채운다. `MerchantApiKeyController`(발급/폐기와 같은 컨트롤러)에 `@GetMapping`으로 추가했다.

- **`VIEWER`를 명시적으로 막는다 — 가맹점 목록 조회(`ListMerchantsUseCase`)와 정반대 판단이다.** `docs/`의 "6.6"은 `OWNER`/`ADMIN`을 "가능"이라고 명확히 적은 반면 `VIEWER`는 "제한적 또는 불가"로 모호하게 남겨뒀다 — `InternalUserRole.VIEWER`를 "조회 전용"으로 명확히 정의해 전면 허용했던 가맹점 목록 조회와 달리, 여기서는 문서 자체가 결론을 유보하고 있고 API Key 목록에는 `keyPrefix`/`scopes`/`lastUsedAt` 같은 운영 메타데이터가 담겨 있어 더 보수적으로 판단했다 — `IssueMerchantApiKeyUseCase`/`RevokeMerchantApiKeyUseCase`와 같은 `OWNER`/`ADMIN` 전용 게이트(동적 `canManageApiKeys()` 재확인 포함)를 그대로 쓴다.
- **`SecurityConfig` 변경이 필요 없었다 — 가맹점 목록 조회와 정반대 상황이다.** `GET /admin/merchants`를 추가했을 때는 기존 `POST /admin/merchants` 규칙이 경로 전체를 걸고 있어서 `HttpMethod.POST`로 좁혀야 `VIEWER`가 목록을 볼 수 있었는데, 여기서는 `VIEWER`를 애초에 막기로 했으므로 기존 `/merchant/api-keys` 와일드카드 규칙(`hasAnyRole("OWNER", "ADMIN")`, 메서드 제한 없음)이 새 `GET`도 이미 덮는다 — `SecurityConfig`를 건드리지 않았다.
- **`MerchantApiKeyRepository`(Command Repository)가 아니라 전용 Projection `MerchantApiKeyListProjection`을 새로 만들었다** — `ListMerchantsUseCase`/`MerchantListProjection`이 세운 Command/Projection 분리 선례(`docs/architecture/persistence-jooq.md`)를 두 번째로 적용한 사례다. `secretHash`/`hashAlgorithm`은 Projection의 읽기 모델(`MerchantApiKeySummary`)에 애초에 없다 — Adapter 경계가 아니라 Port 자체가 Secret을 배제한다.
- **Scope 조회는 N+1이다 — 의도적으로 받아들인 MVP 단순화다.** `MerchantApiKeyRepositoryAdapter`가 이미 갖고 있던 private `findScopes(merchantApiKeySeq)` 헬퍼와 같은 모양을 `MerchantApiKeyListProjectionAdapter`에도 그대로 뒀다 — 한 가맹점의 Key 개수가 MVP 규모에서는 작아서, `SellToFakeExchangeUseCase`의 `PaymentRepository.findPendingExchangeSettlement` 풀스캔과 같은 성격의 트레이드오프로 판단했다.
- **정렬은 `created_at DESC`** — `MerchantListProjection`과 같은 이유(방금 발급한 Key를 목록 맨 위에서 바로 확인).
- **테스트**: `ListMerchantApiKeysUseCaseTest`(단위, ACTIVE OWNER가 자기 가맹점 목록 조회/VIEWER는 예외/`SUSPENDED` OWNER는 예외/빈 목록), `MerchantApiKeyListProjectionAdapterTest`(Testcontainers MySQL 통합 — `createdAt` 내림차순 정렬과 Scope까지 포함해서 확인/폐기 상태 반영/다른 가맹점 Key 미포함/빈 목록), `MerchantApiKeyControllerTest`에 `GET` 케이스 5개 추가(OWNER/ADMIN 200, **VIEWER는 403** — 가맹점 목록 조회의 VIEWER 200과의 비대칭이 핵심 검증 지점, 인증 없음 401/403, Use Case 예외의 403 매핑). 여기에 더해 실제 `bootRun`(`api-merchant`만 기동, 시드 OWNER로 시작) + `curl`로 OWNER 로그인 → 목록 조회(200, 시드 Key `sk_test_devkey01` 확인, Secret 필드 없음) → API Key 신규 발급 → 목록 재조회로 새 Key가 맨 앞에 `ACTIVE`로 포함되는 것 확인 → 그 Key 폐기 → 목록 재조회로 `REVOKED`+`revokedAt` 반영 확인 → **VIEWER를 발급·수락·로그인시켜 그 세션으로 목록 조회 시도(403, `SecurityConfig` 차단)** → 인증 없이 조회(403)까지 전부 확인한 뒤 발급했던 Key와 VIEWER 계정을 정리했다.

## 초대 수락(활성화) Use Case(`AcceptAccountInvitationUseCase`, `application.identity`)

`IssueInternalUserUseCase`가 만든 `InternalUser(INVITED)` + `AccountInvitation(PENDING)`을 대상으로, 초대받은 사람이 원문 Token과 새 비밀번호를 제출해 `INVITED → ACTIVE`로 전이시키는 흐름이다(`docs/domain/state-transitions.md`의 "활성화": "유효한 초대, 초대 만료 전, 비밀번호 설정 완료"). `api-admin`/`api-merchant` 둘 다에서 쓰인다 — `docs/architecture/identity-access-api-key.md`가 `InternalUser`/`MerchantUser` 둘 다 같은 `INVITED → ACTIVE` 상태 흐름을 공유한다고 정의했고, 실제로 `AccountInvitation`이 이미 `accountType`으로 둘을 구분하며 두 애그리게이트의 `activate(passwordHash, activatedAt)` 시그니처가 완전히 같아서, Use Case 하나로 합쳐 만들었다 — 거의 동일한 로직을 두 Use Case로 중복시키지 않는다.

- **`Command.expectedAccountType`으로 앱 경계를 강제한다.** `api-admin`은 항상 `InvitationAccountType.INTERNAL_USER`로, `api-merchant`는 항상 `InvitationAccountType.MERCHANT_USER`로 고정해서 호출한다 — 실제 `AccountInvitation.accountType`이 다르면 다른 앱 경계의 초대 Token을 잘못 제출한 것으로 보고 거부한다(가맹점 사용자 초대 Token을 `api-admin` 엔드포인트에 제출해도 통과하지 않는다).
- **새 예외 `InvalidInvitationException`은 `InvalidCredentialsException`과 완전히 같은 철학이다.** Token 없음/`accountType` 불일치/`PENDING`이 아님(이미 수락·만료·폐기됨)/만료 시각 지남 — 네 경우를 전부 같은 메시지로 가린다. 어느 조건에서 실패했는지 드러내면 다른 사람의 초대 Token 존재 여부를 무차별 대입으로 탐색할 여지가 생긴다.
- **만료된 초대를 발견해도 `AccountInvitation.expire()`를 호출해 `EXPIRED`로 갱신하지는 않는다.** `docs/database/database-design.md`의 `idx_account_invitation_pending(invitation_status, expires_at)` 인덱스가 암시하는 별도의 만료 Sweep Worker의 책임으로 남겨뒀다(아직 없음, 알려진 gap) — 이 Use Case는 만료 여부를 읽기 전용으로만 판단하고 상태를 바꾸지 않는다.
- **Token을 URL 경로가 아니라 요청 본문으로 받는다**(`POST /admin/account-invitations/accept`, `POST /merchant/account-invitations/accept` — `docs/`에 이 경로 자체가 정해져 있지 않아 새로 정했다) — 접근 로그에 민감한 Token 원문이 남지 않게 하려는 의도적 선택이다(`docs/`의 "6.4 저장 정책"이 API Key 원문 노출을 최소화하는 것과 같은 정신).
- **두 경로 다 `SecurityConfig`에서 `permitAll`이다** — 호출자는 아직 인증되지 않은 상태(Token만 갖고 있다)라서, `/admin/login`/`/merchant/login`과 같은 자리에 둔다.
- **빠져 있던 조회 Port 3개를 추가했다**: `AccountInvitationRepository. findByTokenHash`(`account_invitation.token_hash`가 이미 `UNIQUE` 인덱스라 `MerchantApiKey`의 Prefix→Hash 2단계 조회와 달리 곧바로 정확히 일치하는 값으로 조회한다 — 스키마가 이미 그렇게 설계돼 있었을 뿐 새로 판단한 게 아니다), `InternalUserRepository.findById`, `MerchantUserRepository.findById`(둘 다 `AccountInvitation.internalUserId`/`merchantUserId`로 대상 계정을 로드하는 데 쓴다 — `MerchantUserRepositoryAdapter`에 이미 있었지만 지금까지 안 쓰이던 private `resolveMerchantId(merchantSeq)` 헬퍼를 이 메서드가 처음 실제로 쓴다).
- **`api-merchant`에는 `InvitationTokenHasher` 구현체가 아직 없었다** — 당시에는 `api-admin`의 `HmacInvitationTokenHasher`를 복제해 `api-merchant/support/`에 추가했지만, 지금은 두 앱 다 `modules:infra-support`의 공유 구현을 쓴다 (`CLAUDE.md`의 "공용 Port 구현(modules:infra-support)" 절 참고). `api-merchant/application.yaml`의 `app.invitation-token.pepper` 설정은 그대로 필요하다 — 그 값을 읽는 Bean이 공유 모듈로 옮겨졌을 뿐 설정 자체는 앱마다 있어야 한다.
- **`AccountInvitation + (InternalUser 또는 MerchantUser)`를 함께 저장하는 트랜잭션 경계는 `docs/architecture/persistence-jooq.md`가 명시한 세 경계 어디에도 없다** — `IssueInternalUserUseCase`가 발급 시점에 이미 같은 방식으로 새 경계를 정의한 선례를 그대로 따랐다.
- **테스트**: `AcceptAccountInvitationUseCaseTest`(단위, InternalUser 정상 수락/MerchantUser 정상 수락/존재하지 않는 Token/accountType 불일치/이미 ACCEPTED/만료됨), `AccountInvitationRepositoryAdapterTest`의 `findByTokenHash` 케이스 + `InternalUserRepositoryAdapterTest`/ `MerchantUserRepositoryAdapterTest`의 `findById` 케이스(Testcontainers MySQL 통합), `AcceptAccountInvitationControllerTest`(api-admin/api-merchant 각각, `@WebMvcTest` + `@Import(SecurityConfig::class)` — 비인증 요청도 성공해야 함을 검증). 여기에 더해 실제 `bootRun` + `curl`로 `api-admin`에서 발급 API → 그 응답의 `invitationToken`을 그대로 수락 API에 제출 → `INVITED → ACTIVE` 전이(DB `user_status=ACTIVE` 확인) → 그 계정으로 실제 `/admin/login` 로그인 성공까지 발급→수락→로그인 흐름 전체를 처음으로 끝까지 검증했다.

## `apps:batch`의 Confirm 폴링 Worker

`ConfirmBlockchainTransactionUseCase`(`modules:application`)의 KDoc이 "향후 Worker(`apps:batch`)가 대상 목록을 뽑아 하나씩 호출하는 것을 전제로 설계했다"고 남겨뒀던 그 Worker다 — `apps:batch`의 첫 실제 Job이며, 이 앱을 "웹 스타터도 jOOQ/DataSource도 없는 부팅 골격"에서 진짜 배치 앱으로 만든 계기다.

- **Spring Batch의 `Job`/`Step`/`Tasklet`을 그대로 쓴다** — `@Scheduled` 크론만으로 충분했을 수도 있지만, `spring-boot-starter-batch`가 이미 프로젝트 초기부터 `apps:batch`의 의존성으로 들어가 있었다(그러려고 넣어둔 의존성이었다는 뜻으로 받아들였다). `ConfirmBlockchainTransactionJobConfiguration`이 Job/Step 하나씩만 정의하는 가장 단순한 모양이고, `ConfirmPendingBlockchainTransactionsTasklet`이 실제 폴링 로직(`BlockchainTransactionRepository.findPendingConfirmation()`으로 대상을 뽑아 하나씩 `ConfirmBlockchainTransactionUseCase.execute()` 호출)이다. Chunk 지향(ItemReader/Processor/Writer)으로 나누지 않았다 — Use Case 호출 자체가 이미 저장까지 끝내서 "읽기 → 처리 → 쓰기"를 분리할 이유가 없다.
- **`JobOperator`를 쓴다, `JobLauncher`가 아니다** — 이 프로젝트가 쓰는 `spring-batch-core:6.0.4`(Spring Boot 4.1.0 BOM)부터 `JobLauncher`가 `@Deprecated(forRemoval)`이고 `JobOperator`(`JobLauncher`를 상속)가 그 대체다. `BlockchainTransactionConfirmScheduler`가 `@Scheduled(fixedDelay = 10_000)`로 10초마다 `jobOperator.start(job, jobParameters)`를 부른다 — 매번 `JobParameters`에 현재 시각을 담아서, 이미 `COMPLETED`된 `JobInstance`를 Spring Batch가 재실행 거부하는 걸 피한다(같은 파라미터면 새 인스턴스로 안 쳐준다). `POLL_INTERVAL_MILLIS`(10초)는 `docs/`에 값이 없어 고정한 MVP 상수다 — Base의 블록 생성 주기(~2초)를 감안했다.
- **`spring.batch.job.enabled: false`로 부팅 시 자동 1회 실행을 껐다** — Spring Boot의 `JobLauncherApplicationRunner`가 기본으로 하는 "Job Bean을 부팅 시 한 번 실행" 동작은, 계속 폴링해야 하는 이 Job에는 안 맞는다. 실행 시점은 전부 `BlockchainTransactionConfirmScheduler`가 정한다.
- **Step에 `ResourcelessTransactionManager`를 쓴다** — 실제 DB 쓰기는 Tasklet 안에서 `ConfirmBlockchainTransactionUseCase`가 자기 트랜잭션(`TransactionManager.runInTransaction`)으로 이미 처리하므로, Step 레벨에서 Spring이 관리하는 진짜 트랜잭션으로 또 감싸면 안 된다(이중으로 걸린다). `JobRepository`(BATCH_* 테이블에 실행 기록을 남기는 쪽)는 이 트랜잭션 매니저와 무관하게 별도로 동작한다.
- **하나가 실패해도 나머지를 계속 처리한다** — `ConfirmPendingBlockchainTransactionsTasklet`이 각 항목을 개별 `try/catch`로 감싸고, 실패하면 로그만 남기고 다음 항목으로 넘어간다. 다음 폴링에서 같은 항목을 다시 시도한다(Repository가 상태를 안 바꿨으니 여전히 대상 목록에 남아 있다).
- **새 Repository 조회 `BlockchainTransactionRepository.findPendingConfirmation()`을 추가했다** — `SUBMITTED`/`DETECTED`/`CONFIRMING` 전부를 `updated_at` 오름차순으로 돌려준다. `docs/database/database-design.md`의 "Confirm Worker: `transaction_status + updated_at`" 인덱스와 정확히 대응한다.
- **Spring Batch JobRepository 스키마를 위한 새 Flyway 마이그레이션(`V5__add_spring_batch_schema.sql`)을 추가했다** — 이 프로젝트가 설계한 도메인 테이블이 아니라 `spring-batch-core:6.0.4`의 공식 `schema-mysql.sql`(JAR 안에서 그대로 추출)이다. `db-core`의 jOOQ codegen `excludes`에 `BATCH_.*`를 더해서 이 테이블들은 jOOQ 코드가 생성되지 않는다 — Spring Batch가 자체 JDBC로만 관리하고 우리 코드는 손대지 않는다. `spring.batch.jdbc.initialize-schema: never`로 Spring Boot가 스키마를 자동 생성하는 것도 명시적으로 막았다 — "Migration → MySQL Schema → jOOQ Code Generation" 원칙을 프레임워크 테이블에도 그대로 적용했다.
- **실제 RPC/DB로 끝까지 검증했다.** 로컬 DB에 실제 Base Sepolia 트랜잭션(과거 `Web3jBlockchainClient` 검증 때 썼던 것과 같은 Hash)을 가리키는 `Payment`+`BlockchainTransaction` 행을 수동으로 심고 `bootRun`으로 실제 앱을 띄워서, 스케줄러가 10초마다 Job을 실행하고(로그로 확인), 첫 폴링에서 그 거래를 실제로 조회해(`block_number=44280832`로 정확히 detect) `PaymentTransactionValidator`가 우리 USDC Contract와 다르다고 정확히 판단해(`TOKEN_CONTRACT_NOT_ALLOWED`) `BlockchainTransaction`/`Payment` 둘 다 `FAILED`로 저장하고, 다음 폴링부터는 대상 목록에서 빠지는 것까지 실제로 확인했다. 검증 후 스모크 테스트 행은 정리했다.
- **테스트**: `ConfirmPendingBlockchainTransactionsTaskletTest`(단위, 대상 전부 호출/하나 실패해도 나머지 계속/빈 목록은 no-op), `BlockchainTransactionRepositoryAdapterTest`의 `findPendingConfirmation` 케이스(Testcontainers MySQL 통합, `SUBMITTED`/`DETECTED`/`CONFIRMING`은 포함하고 `CONFIRMED`는 제외하는 것까지 확인), `BatchApplicationTests`(Testcontainers, 전체 Spring 컨텍스트 — `JobRepository`/`Job`/`Step`/`Web3jConfiguration`/jOOQ가 다 같이 뜨는지). Job/Step의 실제 실행 자체(`spring-batch-test`의 `JobLauncherTestUtils` 등)는 별도 통합 테스트로 만들지 않았다 — 위 수동 `bootRun` 검증으로 대신했다(알려진 gap: 자동화된 Job 실행 테스트는 없다).

## `apps:batch`의 OutboxEvent 발행 Worker

`OutboxEvent`(`domain.outbox`)의 KDoc이 "별도 발행 Worker가 이 레코드를 읽어 실제 메시지 발행(예: Webhook 트리거)을 수행하고 상태를 갱신한다"고 남겨뒀던 그 Worker다. `apps:batch`의 두 번째 Job이며, Confirm 폴링 Worker와 정확히 같은 골격(`Job`/`Step`/`Tasklet`, `JobOperator`, `ResourcelessTransactionManager`, `spring.batch.job.enabled: false`, 10초 `@Scheduled` 폴링, 하나 실패해도 나머지 계속)을 그대로 재사용한다 — 그 골격 자체의 근거는 위 "apps:batch의 Confirm 폴링 Worker" 절 참고, 여기는 이 Worker에서만 다른 판단만 적는다.

- **새 `modules:application` 패키지 `application.outbox`를 만들었다.** `PublishOutboxEventUseCase`가 이 Worker의 핵심 로직이다 — `OutboxEvent`를 대상으로 `PENDING`/`RETRY_WAITING` 체크 → `startPublishing()` → `aggregateType == "Payment"`로 `PaymentRepository.findById` → `Payment.merchantId`로 `MerchantRepository.findById`까지 이어서 수신 Merchant를 찾는다(`CreatePaymentUseCase`/`ConfirmBlockchainTransactionUseCase` 둘 다 지금은 `aggregateType = "Payment"`로만 이벤트를 만들어서 그 경우만 다룬다 — 다른 `aggregateType`이 생기면 그때 분기를 넓힌다).
- **`Merchant.webhookUrl`이 없으면(가맹점이 Webhook을 설정하지 않은 정상적인 경우) `WebhookDelivery`를 아예 만들지 않고 바로 `OutboxEvent.publish()`로 끝낸다.** 이 분기를 실제 `bootRun`으로 확인했다(아래 "실제 RPC/DB로 끝까지 검증했다" 참고) — 보낼 곳이 없는 이벤트를 "발행 실패"로 취급하면 안 된다는 판단이다.
- **`Merchant.webhookUrl`이 있으면 `(eventId, merchantId)`로 기존 `WebhookDelivery`를 먼저 찾는다(`WebhookDeliveryRepository.findByEventIdAndMerchantId`)** — 재시도로 다시 호출됐을 때 새 `WebhookDelivery`를 중복으로 만들지 않기 위해서다(`uk_webhook_event_merchant` DB 제약과 대응하는 애플리케이션 레벨 확인, `SubmitPaymentTransactionUseCase`가 `BlockchainTransaction`에 대해 하는 것과 같은 패턴). 없으면 새로 만든다.
- **`WebhookSender.send()`의 결과에 따라 세 갈래로 나뉜다**: 2xx 응답 → `WebhookDelivery.succeed()` + `OutboxEvent.publish()`. 그 외(비-2xx 응답 또는 전송 자체 실패)면서 `attemptCount < MAX_WEBHOOK_ATTEMPTS`(5, `docs/`에 값이 없어 고정한 MVP 상수 — `WebhookDelivery`/`OutboxEvent`의 KDoc도 "최대 횟수"를 명시하지 않고 호출부 판단으로 남겨뒀다)면 → 둘 다 `scheduleRetry()`(`nextRetryAt = now + RETRY_DELAY`, 1분 고정 — 지수 백오프 없는 MVP 단순화). 그 이상이면 → 둘 다 `fail()`로 최종 실패 처리.
- **`OutboxEvent + WebhookDelivery`를 함께 저장하는 트랜잭션 경계는 `docs/architecture/persistence-jooq.md`가 명시한 세 경계(결제 생성/결제 완료/환전 완료) 어디에도 없다** — `PublishOutboxEventUseCase`가 새로 정의한 경계다(`SubmitPaymentTransactionUseCase`의 "결제 제출" 경계, `IssueInternalUserUseCase`의 "발급" 경계와 같은 성격).
- **`WebhookSender`를 JDK 내장 `java.net.http.HttpClient`로 구현했다(`HttpWebhookSender` — `modules:infra-support`의 `infra.support.webhook`, 원래는 `apps:batch` 안에 있었다)** — 이 프로젝트에서 처음으로 아웃바운드 HTTP 호출이 필요해졌지만, 유일한 사용처인 `apps:batch`가 웹 앱이 아니라서(`spring-boot-starter-web*` 없음) Spring의 `RestClient`/`WebClient`를 새로 끌어오는 대신 별도 의존성이 필요 없는 JDK 내장 클라이언트를 썼다. 인스턴스 하나를 필드로 재사용하고(`HttpClient`는 스레드 안전·재사용 전제 타입), `connectTimeout=5초`/요청 `timeout=10초`를 둔다. 응답 본문은 필요 없어 `BodyHandlers.discarding()`을 쓴다.
- **`OutboxEventRepositoryAdapter`를 insert-only에서 select-then-insert-or-update로 바꿨다.** 원래(`OutboxEvent`를 처음 만들 때) `save()`가 `.insert()` 하나뿐이었는데, 이 Worker가 처음으로 기존 `OutboxEvent`의 상태 전이(`PROCESSING`/`RETRY_WAITING`/`PUBLISHED`/`FAILED`)를 다시 저장해야 해서 UPDATE 경로를 추가했다. `outbox_event`는 `version` 컬럼이 없어서(`OutboxEvent`의 KDoc 참고) 낙관적 잠금 없이 단순 UPDATE다 — 여러 발행 Worker 인스턴스가 동시에 같은 행을 집어가는 경합은 막지 않는다(이 MVP는 배치 앱을 단일 인스턴스로만 돌린다고 전제한다, 알려진 gap).
- **새 Adapter `WebhookDeliveryRepositoryAdapter`(`modules:infra-persistence`, 새 패키지 `infra.persistence.jooq.webhook`)는 `PaymentRepositoryAdapter`와 같은 모양·같은 낙관적 잠금 한계를 가진다** — `webhook_delivery`는 (`outbox_event`와 달리) 진짜 `version` 컬럼이 있어서 UPDATE에 `VERSION.eq(existing.version)` 조건을 건다.
- **실제 RPC/DB로 끝까지 검증했다.** 로컬 DB에 Merchant 셋(Webhook URL이 `https://httpbin.org/status/200`인 것, `NULL`인 것, `https://httpbin.org/status/500`인 것)과 각각의 `Payment`+`OutboxEvent(PENDING, aggregateType=Payment)` 행을 수동으로 심고 `bootRun`으로 실제 앱을 띄워서, 스케줄러 첫 폴링에서 세 이벤트를 모두 집어(`Outbox 발행 대상 2건` 로그, 이어서 3번째 이벤트는 별도로 심어 다음 폴링에서 `1건`으로 확인) 실제 HTTP 요청을 보내고: (1) 200 응답 → `WebhookDelivery.SUCCEEDED`(`last_http_status=200`) + `OutboxEvent.PUBLISHED`, (2) `webhookUrl=NULL` → `WebhookDelivery` 행 자체가 생기지 않고 `OutboxEvent.PUBLISHED`, (3) 500 응답 → `WebhookDelivery.RETRY_WAITING`(`last_http_status=500`, `next_retry_at`=1분 뒤) + `OutboxEvent.RETRY_WAITING`까지 DB에서 직접 확인했다. 검증 후 스모크 테스트 행은 정리하고 `bootRun` 프로세스를 종료했다.
- **테스트**: `PublishOutboxEventUseCaseTest`(단위, Webhook 미설정 시 즉시 발행/2xx 응답 성공/비-2xx 응답 재시도 예약/재개된 `WebhookDelivery`가 시도 한도에 도달해 최종 실패/존재하지 않는 ID/이미 처리 중이거나 종료 상태), `PublishPendingOutboxEventsTaskletTest`(단위, 대상 전부 호출/하나 실패해도 나머지 계속/빈 목록은 no-op — `ConfirmPendingBlockchainTransactionsTaskletTest`와 같은 케이스 구성), `OutboxEventRepositoryAdapterTest`에 `findById`/`findPendingPublication`(`PENDING`과 기한 도래 `RETRY_WAITING`은 포함하고 미도래 `RETRY_WAITING`/`PUBLISHED`는 제외)/update 경로 케이스 추가, 새 `WebhookDeliveryRepositoryAdapterTest`(Testcontainers MySQL 통합 — insert/상태 전이 update/조회 없음).

## "Fake Exchange 매도" Use Case(`SellToFakeExchangeUseCase`, `application.exchange`)

`docs/architecture/mvp-scope.md`의 전체 흐름 중 마지막 구간 `Fake Exchange 매도 → SettlementReceivable READY`와, `docs/architecture/persistence-jooq.md`가 정의한 세 번째이자 마지막 트랜잭션 경계 "환전 완료"(`ExchangeOrder COMPLETED + SettlementReceivable READY + OutboxEvent`)를 구현한다. 이 Use Case가 성공하면 MVP 완료 경계(`Payment=SUCCEEDED`, `ExchangeOrder=COMPLETED`, `SettlementReceivable=READY`)가 처음으로 끝까지 채워진다. `ExchangeOrder`/ `SettlementReceivable` 도메인 애그리게이트 자체는 이전부터 구현돼 있었다 — 이 Use Case가 실제로 그 둘을 만들고 완료시키는 첫 호출부다.

- **이미 `SUCCEEDED`인 Payment 하나를 대상으로 한 매도 시도 한 번이다** — `ConfirmBlockchainTransactionUseCase`/`PublishOutboxEventUseCase`와 같은 모양. `docs/decisions/ADR-004-fake-exchange.md`는 트리거 방식을 명시하지 않지만, 이 코드베이스에 이미 두 번 반복된 확립된 패턴(Use Case는 대상 하나에 대한 시도 한 번, `apps:batch`의 Worker가 반복 호출)을 세 번째로 그대로 따랐다 — 새 아이디어를 만들지 않았다.
- **Fake Exchange는 `ExchangeOrder.create()` 직후 곧바로 `complete()`를 호출해 `SUBMITTED`/`PROCESSING`을 건너뛴다**(`ExchangeOrder.complete`의 KDoc, ADR-004에 이미 그렇게 설계돼 있었다). `clientOrderId`는 `"sell_" + paymentId`로 Payment ID에서 결정론적으로 만든다 — 같은 Payment로 재시도해도 항상 같은 값이라 `uk_exchange_client_order_id` Unique 제약과 충돌하지 않는다.
- **Gross/Fee/Adjustment 금액 계산을 이 Use Case에 인라인했다, 별도 파일을 만들지 않았다.** `docs/domain/domain-model.md`는 이 계산을 `SettlementAmountCalculator`라는 별도 Domain Service로 분류하지만, 바로 옆에 나열된 `PaymentAmountCalculator` (KRW→USDC 변환)도 실제로는 별도 파일 없이 `CreatePaymentUseCase`에 인라인돼 있다 — 그 기존 선례를 그대로 따랐다. `grossAmount`는 정산 기준 금액이라 정의상 매도 시점이 아니라 원래 주문 시점 KRW 금액(`Payment.orderAmount`)을 그대로 쓴다 — 결제 시점과 매도 시점 사이 시장 환율이 움직인 차이는 `SettlementReceivable.exchangeProfitLossAmount`(매도로 실제 확보한 KRW − grossAmount)에 담긴다. `SETTLEMENT_FEE_RATE`(1.5%)도 `CreatePaymentUseCase`의 `SPREAD_RATE`와 같은 성격의 MVP 상수다(`docs/`에 값이 없어 고정).
- **"환전 완료" Webhook용 `OutboxEvent`는 `aggregateType="Payment"`를 재사용한다, 새 aggregateType을 만들지 않았다.** `PublishOutboxEventUseCase.resolveMerchant()`가 오늘 `"Payment"`만 지원해서(다른 aggregateType이 생기면 그때 분기를 넓힌다고 이미 KDoc에 적혀 있었다), 여기서 `eventType="payment.settled"`로만 구분하고 `aggregateType`/`aggregateId`는 `ConfirmBlockchainTransactionUseCase`의 `payment.succeeded` 이벤트와 똑같이 Payment를 가리키게 했다 — `PublishOutboxEventUseCase`를 고치지 않고 그대로 재사용했다.
- **`PaymentRepository.findPendingExchangeSettlement()`를 새로 추가했다** — `payment_status='SUCCEEDED'`이면서 아직 `exchange_order` 행이 없는 Payment를 찾는다(`PAYMENT`에 `NOT EXISTS(SELECT 1 FROM EXCHANGE_ORDER WHERE EXCHANGE_ORDER.PAYMENT_SEQ = PAYMENT.PAYMENT_SEQ)`). `payment` 레코드에 정산 상태를 절대 추가하지 않는다는 루트 `CLAUDE.md`의 규칙 때문에 Payment 테이블만으로는 "이미 매도 처리됐는지"를 판단할 수 없어 불가피하게 크로스 애그리게이트 조회가 됐다 — Confirm Worker/Outbox 발행과 달리 `docs/database/database-design.md`에 이 폴링만을 위한 전용 인덱스가 명시돼 있지는 않다(알려진 gap, 다만 이 MVP 데이터량에서는 풀스캔으로도 문제없다).
- **새 outbound Port 둘을 추가했다**: `ExchangeOrderRepository`(`save`/ `findByPaymentId`), `SettlementReceivableRepository`(`save`/`findByPaymentId`) — 둘 다 `payment_seq` Unique 제약(`uk_exchange_payment`/ `uk_settlement_receivable_payment`)과 대응하는 멱등성 조회다.
- **새 Adapter `ExchangeOrderRepositoryAdapter`/`SettlementReceivableRepositoryAdapter`** (`modules:infra-persistence`, 새 패키지 `infra.persistence.jooq.exchange`/ `.settlement`)는 `WebhookDeliveryRepositoryAdapter`와 같은 모양·같은 낙관적 잠금 한계를 가진다 — 둘 다 `version` 컬럼이 있다. `quote_currency`/ `settlement_currency` 컬럼은 `PaymentRepositoryAdapter`의 `order_currency` 하드코딩과 같은 이유로 `"KRW"` 리터럴로 채운다.
- **`apps:batch`에도 `FakeExchangeRateProvider`가 필요해졌다** — 당시에는 `apps:api-payment`의 구현을 복제했고("필요해지면 그때 공유 모듈로 옮긴다"), 실제로 그 시점이 와서 지금은 `modules:infra-support`의 공유 구현을 쓴다 (`CLAUDE.md`의 "공용 Port 구현(modules:infra-support)" 절 참고).
- **테스트**: `SellToFakeExchangeUseCaseTest`(단위, 정상 처리/멱등 재실행/ 존재하지 않는 Payment/SUCCEEDED가 아닌 상태), `ExchangeOrderRepositoryAdapterTest` + `SettlementReceivableRepositoryAdapterTest`(Testcontainers MySQL 통합, insert/ 상태 전이 update/`findByPaymentId`), `PaymentRepositoryAdapterTest`에 `findPendingExchangeSettlement` 케이스 추가(SUCCEEDED+ExchangeOrder 없음 포함/SUCCEEDED+ExchangeOrder 있음 제외/SUCCEEDED 아님 제외).

## `apps:batch`의 Fake Exchange 매도 폴링 Worker

`apps:batch`의 세 번째 Job이며, 앞선 두 Worker와 완전히 같은 골격(`Job`/`Step`/ `Tasklet`, `JobOperator`, `ResourcelessTransactionManager`, `spring.batch.job.enabled: false`, 10초 `@Scheduled` 폴링, 하나 실패해도 나머지 계속)을 그대로 재사용한다 — 그 골격 자체의 근거는 위 "apps:batch의 Confirm 폴링 Worker" 절 참고. `SellPendingPaymentsToFakeExchangeTasklet`이 `PaymentRepository.findPendingExchangeSettlement()`로 대상을 뽑아 `SellToFakeExchangeUseCase`를 하나씩 호출한다.

- **테스트**: `SellPendingPaymentsToFakeExchangeTaskletTest`(단위, 대상 전부 호출/하나 실패해도 나머지 계속/빈 목록은 no-op — `ConfirmPendingBlockchainTransactionsTaskletTest`/ `PublishPendingOutboxEventsTaskletTest`와 같은 케이스 구성).

## 가맹점 콘솔 CORS/CSRF와 세션 복원(`apps:api-merchant`)

브라우저 프론트엔드(`frontend/merchant`, 로그인 → API Key 관리 슬라이스)가 붙는 첫 앱이라, 그동안 `SecurityConfig` 주석에 "실제 프론트엔드가 붙기 전에 반드시 켜야 한다"고 미뤄 뒀던 CORS/CSRF를 실제로 켰다. 브라우저 대면 계약 전체는 `docs/architecture/merchant-console-api.md`에, 재사용 규칙 요약은 `CLAUDE.md`의 "가맹점 콘솔 CORS/CSRF" 절에 있다. 여기는 구현 판단·함정만 남긴다.

- **CSRF는 Spring Security 6의 SPA 표준 레시피 그대로다**: `CookieCsrfTokenRepository.withHttpOnlyFalse()`(토큰을 `XSRF-TOKEN` 쿠키로 내림) + `CsrfTokenRequestAttributeHandler`(`setCsrfRequestAttributeName(null)`) + `CsrfCookieFilter`(`BasicAuthenticationFilter` 뒤). 프론트는 상태 변경 요청에 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 되돌려준다.
  - **함정 ① 지연 토큰 로딩** — SS6은 토큰을 지연 로딩해서, 실제로 토큰 값을 "읽는" 코드가 없으면 `CookieCsrfTokenRepository`가 응답에 `XSRF-TOKEN` 쿠키를 싣지 않는다. 그러면 프론트가 첫 POST에 실을 토큰을 얻을 방법이 없다. `CsrfCookieFilter`가 요청마다 토큰 값을 한 번 읽어(`.token`) 지연 로딩을 깨워서, 안전한 GET(`GET /merchant/me`) 응답에도 쿠키가 실리게 한다. `setCsrfRequestAttributeName(null)`은 토큰을 `CsrfToken` 클래스 이름 속성에 담고 지연 로딩을 끄는 짝이다.
  - **함정 ② BREACH 핸들러** — `XorCsrfTokenRequestAttributeHandler`(BREACH 보호, 요청마다 마스킹)가 아니라 평범한 `CsrfTokenRequestAttributeHandler`를 쓴다. "쿠키 값 = 헤더 값"이라야 JS가 단순해지기 때문으로, Spring 공식 SPA 레시피가 택한 트레이드오프 그대로다.
  - **`bootRun` 실물 검증에서 확인한 것**(테스트가 못 잡는 층 — 필터 체인·쿠키): `GET /merchant/me`(미인증) → 401 + `Set-Cookie: XSRF-TOKEN`, 로그인 → 세션 쿠키 발급 후 `/me` 200, 토큰 없는 `POST /merchant/api-keys` → 403 / 토큰 실으면 201, 전체 로그인→발급→폐기 왕복.
- **미인증을 401로 고정했다**(`HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)`) — 기본 엔트리포인트는 403을 내는데, 그러면 프론트가 "로그아웃(401)"을 "CSRF/권한 거부(403)"와 구분할 수 없다. `GET /merchant/me`의 401을 곧 "로그인 필요"로 신뢰하게 하는 API 계약이다. `MerchantMeControllerTest`가 미인증 401을 회귀로 지킨다.
- **CORS는 `allowCredentials = true`**(세션 쿠키를 교차 출처로 실어 보냄)라 허용 Origin에 와일드카드를 못 쓴다 — `app.merchant-console.allowed-origins`(기본 `http://localhost:5174`)로 정확히 나열하고, 허용 헤더에 `X-XSRF-TOKEN`을 더했다. `api-payment`의 체크아웃 CORS(`allowCredentials=false`)와 대비된다. `/merchant/**`에 등록한다(모든 엔드포인트가 브라우저 호출 대상).
- **`/merchant/account-invitations/accept`만 CSRF 예외**(`ignoringRequestMatchers`)로 뒀다 — 비인증 공개 경로이고 자격증명이 세션 쿠키가 아니라 본문의 초대 Token 자체라 CSRF가 막을 대상이 아니다. 이메일 링크로 도달해 토큰을 미리 받아올 GET을 앞에 둘 수도 없다. 그래서 `AcceptAccountInvitationControllerTest`는 `.with(csrf())` 없이 그대로 통과한다.
- **CSRF를 켜니 기존 `@WebMvcTest`의 POST/DELETE 테스트가 403으로 깨졌다** — Login/SubAccount/ApiKey 테스트의 상태 변경 요청에 `.with(csrf())`(SecurityMockMvcRequestPostProcessors)를 더했다. "미인증 → 401/403" 케이스에도 붙여서 CSRF 거부가 아니라 인증 거부를 테스트하게 했다. `MerchantApiKeyControllerTest`에 "인증됐지만 CSRF 토큰 없으면 403" 회귀 케이스를 새로 넣었다.
- **`/merchant/me`(세션 복원)와 `/merchant/logout`을 새로 추가했다.** `me`는 `@AuthenticationPrincipal MerchantUserPrincipal`을 그대로 돌려주고(`merchantUserId`/`merchantId`/`loginId`/`role` — `userName`/`merchantCode`는 principal에 없어 이 슬라이스에서 뺐다, 컨트롤러 KDoc에 표시), CSRF 쿠키 발급도 겸한다. `logout`은 `HttpSession.invalidate()` + `SecurityContextHolder.clearContext()`로 204를 돌려준다 — Spring 기본 `/logout` 대신 명시적 REST 컨트롤러로 둬서 계약이 문서·스펙에 그대로 드러나게 했다(세 API 앱이 로그인/인증을 전부 명시적 컨트롤러로 노출하는 것과 결을 맞춤).
- **OpenAPI 스펙**: `MerchantApiDocumentationTest`가 콘솔 API를 문서화한다 — `api-payment`의 `CheckoutApiDocumentationTest`와 같은 REST Docs 패턴·같은 함정(`resource(builder.build())`로 감싸기, null 예시 필드는 non-null로 채우고 `.optional()` 표시). `build.gradle.kts`도 api-payment와 동일하게 `openapi3` 태스크에 `dependsOn(test)` + `notCompatibleWithConfigurationCache`를 걸었다. 처음에는 로그인·me·logout·api-keys 6개였고, 팀 계정 슬라이스에서 하위 계정 발급·명부·초대 수락을 더해 **9개 오퍼레이션**이 됐다(프론트가 이 스펙으로 타입을 생성하므로, 새 엔드포인트를 프론트에서 쓰려면 여기 먼저 추가해야 한다).

## 가맹점 사용자 목록 조회 Use Case(`ListMerchantUsersUseCase`, `application.identity`)와 `api-merchant`의 명부 API

콘솔의 팀 계정 화면(`frontend/merchant`의 `/team`)이 "누가 소속돼 있고 누가 아직 `INVITED`로 남아 있는지"를 보여주기 위해 추가했다. 하위 계정 발급(`InviteMerchantSubAccountUseCase`)은 이미 있었지만 명부가 없어서, 발급만 하고 결과를 확인할 수 없는 반쪽짜리 상태였다.

- **`ListMerchantApiKeysUseCase`의 선례를 그대로 따랐다** — 새 판단을 만들지 않았다. Command Repository(`MerchantUserRepository`)에 `findAll`류를 추가하지 않고 전용 Projection(`MerchantUserListProjection` + `MerchantUserSummary`)을 뒀고(`docs/architecture/persistence-jooq.md`), 권한은 요청자를 `findById`로 다시 읽어 `ACTIVE` 상태까지 동적으로 확인하며, 조회 대상 가맹점도 그 조회에서 얻는다(`Command`에 `merchantId`가 아예 없다 — 요청이 준 값을 믿으면 남의 가맹점 명부를 읽는 멀티테넌시 취약점이 된다. 이 사실 자체를 `ListMerchantUsersUseCaseTest`의 `verify`로 남겼다).
- **기존 도메인 메서드 `MerchantUser.canInviteSubAccounts()`를 조회에도 재사용했다 — 목록 전용 메서드를 새로 만들지 않았다.** 술어가 완전히 같고(`ACTIVE` && (`OWNER`||`ADMIN`)), `ListMerchantApiKeysUseCase`가 목록 조회에 `canManageApiKeys()`를 그대로 쓴 선례가 이미 있다. 권한 없음 예외도 `MerchantUserCannotInviteSubAccountsException`을 재사용해서(403 매핑이 이미 있다) 새 예외 타입을 늘리지 않았다.
- **`VIEWER`를 막았다** — API Key 목록과 같은 보수적 판단이다(`docs/`의 "6.6"이 "제한적 또는 불가"로 결론을 유보한 상태). 명부에는 다른 사용자의 이메일과 마지막 로그인 시각이 담긴다는 점이 근거다.
- **`SecurityConfig`를 고치지 않아도 됐다.** 기존 규칙 `authorize("/merchant/merchant-users", hasAnyRole("OWNER","ADMIN"))`이 `HttpMethod`로 메서드를 좁히지 않아 새로 추가한 `GET`을 이미 덮는다 — `api-keys` 와일드카드와 같은 상황이고, `api-admin`의 `MerchantController`가 `GET`을 더할 때 메서드를 좁혀야 했던 것과는 반대다. 컨트롤러 KDoc에 이 사실을 적어 뒀다(나중에 규칙을 건드릴 때 근거가 사라지지 않게).
- **`MerchantUserSummary`는 `passwordHash`를 담지 않는다** — `MerchantApiKeySummary`가 `secretHash`를 제외한 것과 같은 정신으로, Projection의 SELECT 목록에서부터 뺐다. 잠금 관련 필드(`failedLoginCount`/`lockedUntil`)도 화면이 쓰지 않아 담지 않았다.
- **새 컨트롤러를 만들지 않고 기존 `MerchantSubAccountController`에 `@GetMapping`을 더했다**(`MerchantApiKeyController`가 발급·폐기·목록을 함께 갖는 것과 같다).
- **테스트**: `ListMerchantUsersUseCaseTest`(단위 5개 — OWNER/ADMIN 통과, VIEWER·SUSPENDED 차단, 조회 가맹점이 항상 요청자에게서 온다), `MerchantUserListProjectionAdapterTest`(Testcontainers MySQL 통합 4개 — 정렬, `INVITED`/`ACTIVE` 혼재와 `lastLoginAt` null, 가맹점 격리, 없는 가맹점), `MerchantSubAccountControllerTest`에 GET 케이스 4개 추가.
- **실물 검증(`bootRun` + curl)에서 초대 전 왕복을 처음으로 끝까지 증명했다**: OWNER 로그인 → 하위 계정(ADMIN) 초대로 `invitationToken` 확보 → 명부에 `INVITED`로 노출 확인 → `POST /merchant/account-invitations/accept`로 비밀번호 설정(비인증·CSRF 토큰 없이 통과하는 것까지 확인) → **새 계정으로 로그인 성공** → 명부에서 `ACTIVE`로 전이 확인 → VIEWER 계정으로 명부 조회·초대 시도 시 둘 다 403. 검증에 만든 계정과 초대 행은 정리했다.
  - **이 과정에서 백엔드 버그가 아닌 함정을 하나 만났다**: Git Bash에서 `curl -d`에 한글을 그대로 실으면 UTF-8이 깨져 `HttpMessageNotReadableException: Invalid UTF-8 start byte`로 **400**이 난다. 서버 문제로 오인하기 쉬우니(응답 본문에 원인이 없다) 셸에서 검증할 때는 ASCII 값을 쓰거나 본문을 파일로 넘긴다 — `CLAUDE.md`의 "openssl base64가 `\r`를 남긴다"와 같은 계열의 Windows 셸 함정이다.



## 가맹점 계정 관리 Use Case(`ChangeMerchantUserStatusUseCase`/`ChangeMerchantUserRoleUseCase`)와 "최소 1 활성 OWNER" 불변식

콘솔에서 하위 계정을 정지·재개·종료하고 역할을 바꾸는 슬라이스다. **`docs/domain/domain-model.md`가 규정하는데 코드 어디에도 구현이 없던 불변식("최소 하나의 활성 OWNER를 유지한다")을 처음으로 구현한 자리**이기도 하다.

- **도메인에 `MerchantUser.changeRole(newRole, changedAt)`을 추가했다** — `role`이 `val`이라 역할 변경 자체가 불가능했다. `OWNER`로의 승격은 `require`로 막는다(`inviteSubAccount`가 같은 제약을 갖는 것과 같은 이유 — 최초 OWNER는 가맹점 등록 트랜잭션에서만 생성된다). 종료된 계정의 역할 변경도 막는다.
- **"마지막 활성 OWNER" 불변식은 애그리게이트가 아니라 Use Case에 뒀다.** 같은 가맹점의 *다른* 사용자를 세어봐야 아는 판단이라 애그리게이트가 혼자 할 수 없다(애그리게이트는 다른 애그리게이트를 모른다). `InviteMerchantSubAccountUseCase`가 `loginId` 중복을 Repository 조회로 막는 것과 같은 자리·같은 성격이다. 새 Port 메서드 `MerchantUserRepository.countActiveOwners(merchantId)`는 목록 화면용 복잡 조회가 아니라 **도메인 규칙 보조 조회**라 Projection이 아니라 Command Repository에 뒀다(`findByMerchantIdAndLoginId`/`findByMerchantIdAndEmail`과 같은 성격).
- **정지·재개·종료를 Use Case 셋으로 쪼개지 않고 `ChangeMerchantUserStatusUseCase` 하나로 뒀다 — "동작마다 Use Case 하나" 관행에서 의도적으로 벗어난 지점이다.** 셋은 권한 확인·테넌시 확인·불변식이 완전히 같고 마지막에 부르는 도메인 메서드 하나만 다르다(`IssueMerchantApiKeyUseCase`/`RevokeMerchantApiKeyUseCase`가 별도인 것은 그 둘이 입력도 규칙도 실제로 다른 연산이기 때문이다). 특히 불변식이 **셋 중 둘**(정지·종료)에만 걸려서, 복제하면 빠뜨리기 쉬운 종류의 규칙이다. 두 Use Case가 공유하는 검사는 `MerchantUserManagementGuard`(순수 함수 묶음, Use Case가 아니다 — `ApplicationPurityTest`의 "Use Case는 다른 Use Case를 호출하지 않는다"를 지킨다)에 모았다.
- **`docs/`에 없어 추론한 판단 둘**(각 예외/KDoc에 표시했다): ① **자기 자신은 대상이 될 수 없다** — 스스로를 정지·종료·강등하면 복구 수단이 사라진다. ② **`ADMIN`은 `OWNER`를 변경할 수 없다** — `docs/`의 "ADMIN은 기존 OWNER의 권한을 변경할 수 없다"(4.4)를 정지·종료까지 확장했다(권한만 못 바꾸고 정지는 할 수 있으면 규칙이 무의미해진다). 다른 가맹점 사용자는 403이 아니라 **404**로 취급한다 — 존재 여부를 응답 코드로 알려주지 않기 위해서다.
- **`IllegalStateException`을 전역으로 409에 매핑하지 않았다.** 그렇게 하면 `checkNotNull`(세션이 가리키는 사용자가 DB에 없음)처럼 **500이 맞는 오류까지 409로 가려진다.** Use Case가 도메인 전이 호출 **한 줄만** 감싸 `InvalidMerchantUserTransitionException`으로 바꾸고, 핸들러는 그 타입만 409로 매핑한다 — `apps:api-payment`가 같은 이유로 그 매핑을 체크아웃 경로에만 좁힌 것과 같은 판단이고, 거기는 경로로, 여기는 예외 타입으로 좁혔다.
- **`SecurityConfig`의 규칙을 `/merchant/merchant-users/**`로 넓혀야 했다.** 기존 정확 경로 규칙은 새 하위 경로(`/{id}/suspend` 등)를 덮지 못해서, 그대로 뒀다면 액션 경로가 `anyRequest, authenticated`로 떨어져 **VIEWER도 정적 1차 관문을 통과**했다(Use Case가 막긴 하지만 방어가 한 겹 사라진다). `MerchantSubAccountControllerTest`에 VIEWER가 액션 경로에서 403을 받는 회귀 테스트를 남겼다.

### 실물 검증에서 잡은 진짜 버그 — `save()`가 `role_code`를 갱신하지 않았다

`bootRun` + curl로 "역할을 VIEWER로 바꾼 뒤 그 계정으로 로그인해 명부를 조회하면 403이어야 한다"를 확인하다가 **200이 나와서** 발견했다. 원인은 `MerchantUserRepositoryAdapter.save()`의 UPDATE 분기에 `ROLE_CODE`가 없었던 것이다 — 그 어댑터를 쓸 당시 `role`이 `val`이라 바뀔 일이 없었기 때문이다. `changeRole()`이 생기면서 **조용한 데이터 유실**이 됐다: API는 200에 새 역할을 돌려주는데(메모리 상태) DB는 옛 역할 그대로였고, 그래서 권한도 축소되지 않았다.

- **자동화 테스트로는 잡을 수 없는 층이었다**: Use Case 단위 테스트는 Repository를 Mock해서 저장 내용을 검증하지 않고, 어댑터 통합 테스트는 애초에 역할을 바꿔볼 수 없었다(불변이었으니까). `backend/CLAUDE.md`의 "테스트가 잡지 못하는 층" 표에 있는 사례들과 같은 성격이다 — **필드를 불변에서 가변으로 바꿀 때는 그 필드를 쓰는 영속성 UPDATE 목록을 반드시 함께 확인한다.**
- 고친 뒤 회귀 테스트(`save persists a changed role`)를 `MerchantUserRepositoryAdapterTest`에 남겼고, 재기동해 DB에 `VIEWER`가 저장되고 그 계정의 명부 조회가 실제로 403이 되는 것까지 확인했다.

### 불변식이 오늘의 API로는 도달 불가능하다는 사실

실물 검증 중 확인했다: 요청자가 활성 OWNER이고 대상이 다른 활성 OWNER면 활성 OWNER가 이미 둘이라 통과하고, ADMIN은 "ADMIN은 OWNER를 변경할 수 없다"에서, 자기 자신은 그 앞에서 먼저 막힌다 — 그래서 `LastActiveOwnerException`은 **현재 HTTP 경로로는 트리거되지 않는다**(단위 테스트로만 검증된다). 죽은 코드로 오해하지 않도록 여기 남긴다: 규칙 자체가 `docs/`에 있고, 향후 경로(내부 운영자 API의 가맹점 계정 관리, 다중 OWNER 승격)에서 곧바로 필요해지므로 방어선으로 유지한다.

## 초대 관리 Use Case(`ResendMerchantUserInvitationUseCase`/`RevokeMerchantUserInvitationUseCase`)

콘솔에서 초대를 발급한 뒤 손댈 수단이 전혀 없던 것을 메운다 — 링크를 잃어버리면 그 계정은 영영 활성화할 수 없었고, 만료돼도 명부에는 `INVITED`로만 보여 원인을 알 수 없었으며, 잘못 보낸 초대를 무효화할 방법도 없었다. 도메인(`AccountInvitation.revoke()`)은 이미 있었고 없던 것은 조회 수단·Use Case·API·화면이다.

- **재발송은 "링크를 다시 보여주기"가 아니라 새 Token 발급이다.** Token은 Hash만 저장돼 원문을 복구할 수 없기 때문이다(`docs/`의 "6.4"와 같은 정신). 그래서 기존 `PENDING`을 `revoke()`하고 새 초대를 만든다 — **이전 링크는 그 즉시 죽는다.** 두 쓰기가 함께 반영돼야 하므로 `TransactionManager`로 묶었다(`InviteMerchantSubAccountUseCase`가 계정+초대를 함께 저장하는 것과 같은 이유). Token 생성 방식과 유효기간(7일)은 그 Use Case의 것을 그대로 따랐다 — 재발송이라고 다른 값을 쓸 이유가 없다.
- **초대 취소와 계정 종료를 분리했다.** 취소는 Token만 무효화하고 계정은 `INVITED`로 남긴다. 묶지 않은 이유는 종료가 **되돌릴 수 없는데** "초대 취소"라는 가벼운 이름 뒤에 숨으면 위험하기 때문이다(재발송하려다 오조작하면 복구 불가). 대신 명부의 `pendingInvitationExpiresAt`이 `null`이 되어 "유효한 초대 없음"이 드러나므로 좀비로 방치되지 않고, 재발송으로 곧바로 되살릴 수 있다(실물 검증에서 이 복구 경로까지 확인했다).
- **초대를 별도 목록으로 만들지 않고 명부(`MerchantUserSummary`)에 `pendingInvitationExpiresAt` 한 필드만 더했다.** 초대는 이미 명부에 `INVITED` 사용자로 나타나므로 별도 목록을 두면 같은 사람이 두 화면에 중복되고, 운영자가 실제로 궁금한 것("왜 아직 활성화가 안 됐지?")은 명부에서 바로 보이는 게 맞다.
- **`MerchantUserManagementGuard`(3번째 슬라이스)를 그대로 재사용했다** — 요청자 권한·테넌시·자기 자신·ADMIN→OWNER 차단이 똑같이 필요하다. **다만 "최소 1 활성 OWNER" 불변식은 부르지 않는다** — 초대 조작은 활성 OWNER 수를 바꾸지 않는다. `SecurityConfig`도 손대지 않았다(3번째 슬라이스에서 넓힌 `/merchant/merchant-users/**` 와일드카드가 이 하위 경로도 덮는다 — 컨트롤러 테스트로 확인).
- **`PENDING`이 사용자당 하나라는 것은 DB 제약이 아니라 우리 로직의 규약이다**(`account_invitation`에 그런 UNIQUE가 없다). 재발송이 항상 기존 것을 `REVOKED`로 만든 뒤 새로 만들기 때문에 성립한다 — 그래서 Port KDoc에 이 사실을 적고, 어댑터는 둘 이상이어도 터지지 않게 최신 하나를 돌려주며, 명부 Projection도 JOIN이 아니라 `MAX(expires_at)` 스칼라 서브쿼리를 써서 **행이 늘지 않게** 했다.
- **만료 처리에는 배치가 없다.** `AcceptAccountInvitationUseCase`가 수락 시점에 `expiresAt`을 검사할 뿐 상태는 `PENDING`으로 남는다(`AccountInvitation.expire()`는 호출부가 없다). 그래서 화면이 `pendingInvitationExpiresAt`을 현재와 비교해 "만료됨"을 판단한다 — 알려진 gap이며, 정리 배치는 다음 범위로 미뤘다(`docs/architecture/merchant-console-api.md`의 7절).
- **실물 검증(`bootRun` + curl)에서 확인한 것**: 초대 발급(토큰 A) → 재발송(토큰 B) → **토큰 A 수락 400 / 토큰 B 수락 200**(토큰 교체가 이 슬라이스의 핵심이라 반드시 실물로 봤다), 취소 후 그 토큰 수락 400, 취소 후에도 계정이 `INVITED`이고 `pendingInvitationExpiresAt`이 `null`인 것(취소≠종료 판단의 실증), 취소된 계정을 재발송으로 복구 후 수락 200, 이미 `ACTIVE`인 계정 재발송 409, 취소할 초대 없는데 취소 409, 활성 VIEWER의 두 액션 403.
  - **셸 함정 하나 더**: Git Bash에서 `UID`는 읽기 전용 예약 변수라 `UID=$(...)`가 조용히 실패하고 셸의 UID(숫자)가 그대로 URL에 들어간다 — 백엔드가 "MerchantUser(197609)를 찾을 수 없습니다"로 응답해서 서버 버그로 오인하기 쉽다. 검증 스크립트에서는 `MUID` 같은 다른 이름을 쓴다(한글 본문 UTF-8 함정과 같은 계열).

## 내부 운영자 콘솔 준비물(`apps:api-admin`의 CORS/CSRF·세션 복원·OpenAPI)

`frontend/admin`이 붙으면서 `api-merchant`에서 검증된 레시피를 그대로 옮겼다 — **새 판단을 만들지 않았다.** 레시피 자체의 근거(Spring Security 6의 지연 토큰 로딩, BREACH 핸들러 선택, 401 엔트리포인트가 필요한 이유)는 위 "가맹점 콘솔 CORS/CSRF" 절에 있고, 여기는 이 앱에서만 다른 점만 남긴다.

- **`CsrfCookieFilter`를 복제했다, 공유하지 않았다.** 앱은 서로를 모르는 독립 배포 단위라(`backend/CLAUDE.md`의 Apps 절) 클래스를 공유할 자리가 없다 — 공유하려면 `modules:*`로 내려야 하는데, 그건 Spring Security 필터라 헥사고날 계층 어디에도 맞지 않는다(도메인도 애플리케이션도 아니고, inbound Adapter의 일부다). 30줄짜리 필터를 두 앱에 두는 편이 계층을 흐리는 것보다 낫다고 판단했고, 양쪽 KDoc에 "같은 이유·같은 코드"임을 명시해 한쪽만 고치는 일을 막았다.
- **기존 인가 규칙을 건드리지 않았다.** 특히 `POST /admin/merchants`의 `HttpMethod` 스코핑(=`GET`은 `VIEWER`도 허용)은 `VIEWER`가 "조회 전용"이라는 정의를 지키는 장치라, CSRF/CORS를 얹으면서 실수로 넓히거나 좁히지 않도록 주의했다. `bootRun`으로 `VIEWER`가 `GET` 200 / `POST` 403 / `POST /admin/internal-users` 403을 받는 것을 다시 확인했다(`api-merchant`에서 와일드카드를 넓혀야 했던 것과 달리 여기서는 **바꿀 이유가 없었다** — 새 경로 `/admin/me`·`/admin/logout`은 `anyRequest, authenticated`가 덮는다).
- **OpenAPI 스펙을 이 앱에도 신설했다**(5개 오퍼레이션: login·me·logout·merchants 목록/등록). 이로써 세 웹 API 앱이 전부 같은 방식으로 프론트에 타입을 공급한다.

### 실물 검증 — 두 앱을 잇는 흐름을 처음으로 증명했다

이 슬라이스의 핵심 검증은 **admin에서 만든 초대가 merchant에서 통하는지**였다:

1. `GET /admin/me` 미인증 → 401 + `XSRF-TOKEN` 쿠키
2. CORS: 5175 허용(credentials 포함), 5174(가맹점 콘솔)는 403 — Origin을 정확히 좁혔다는 증거
3. CSRF 없는 `POST /admin/merchants` → 403 / 토큰 실으면 201
4. **가맹점 등록(:8082) → 받은 토큰으로 `POST /merchant/account-invitations/accept`(:8083) → 새 OWNER로 merchant 로그인 → 자기 가맹점 명부 조회까지 성공**
5. VIEWER 회귀: `GET /admin/merchants` 200, `POST` 403, `POST /admin/internal-users` 403

4번이 특히 값지다 — 이 왕복이 성공한다는 것은 **두 앱의 `app.invitation-token.pepper`가 실제로 같다**는 뜻이기도 하다(어긋나면 초대를 영영 찾지 못하고, 그때 나오는 예외는 원인을 숨기도록 설계된 `InvalidInvitationException`이라 추적이 매우 어렵다 — `backend/CLAUDE.md`의 "설정과 비밀값"에 적어 둔 위험이 실제로 검증된 셈이다). 검증에 만든 가맹점·계정은 정리했다.

### 프론트에서 조심할 것: 초대 링크가 다른 콘솔을 가리킨다

`POST /admin/merchants`가 돌려주는 `invitationToken`은 **가맹점 OWNER의 것**이라 활성화 화면도 가맹점 콘솔에 있다. `frontend/admin`은 `VITE_MERCHANT_CONSOLE_URL`로 링크를 만들고(`console/format.ts`의 `merchantInvitationUrlFor`), 자기 origin을 쓰면 **상대가 열 수 없는 링크**가 되는데 화면상으로는 멀쩡해 보인다 — 그래서 "링크가 5174를 가리키고 현재 origin을 포함하지 않는다"를 프론트 테스트로 고정했다. 계약 문서(`docs/architecture/admin-console-api.md`의 5절)에도 절로 남겼다.

## 내부 운영자 목록 조회(`ListInternalUsersUseCase`)와 `api-admin`의 명부 API

내부 직원을 발급할 수는 있는데 **누가 있는지 볼 수 없고, 발급해도 UI로 활성화할 수 없던** 두 구멍을 메운다(가맹점 쪽 2번째 슬라이스와 같은 성격의 gap이 admin 쪽에도 있었다).

- **요청자를 받지 않는다 — 가맹점 쪽 `ListMerchantUsersUseCase`와 의도적으로 다르다.** 같은 앱의 `ListMerchantsUseCase`가 무인자 `execute()`이고 `IssueInternalUserCommand`의 KDoc이 "발급 권한 확인은 inbound Adapter(세션의 역할)가 끝냈다고 전제한다"고 명시한 **지역 관행**을 따랐다. 더 본질적으로는, 가맹점 쪽에서 요청자를 다시 읽은 핵심 이유가 "조회 대상 가맹점을 신뢰할 수 있는 곳에서 얻기 위해서"였는데(멀티테넌시), **내부 운영자는 특정 가맹점에 속하지 않아 좁힐 범위 자체가 없다.** 그래서 인가는 전적으로 `SecurityConfig`가 진다.
- **`SecurityConfig`를 고치지 않았다 — 이번에는 고칠 필요가 없어서다.** 기존 규칙 `authorize("/admin/internal-users", hasRole("SUPER_ADMIN"))`이 `HttpMethod`로 좁혀져 있지 않아 새 `GET`을 **이미** 덮는다. 같은 앱의 `/admin/merchants`가 `POST`로 좁혀 `GET`을 `VIEWER`에게 여는 것과 **정반대**인데, 둘 다 의도한 결과다: 가맹점 목록은 "조회 전용" VIEWER의 업무이고, 내부 직원 명부는 직원 이메일·마지막 로그인·누가 SUPER_ADMIN인지가 담기는 SUPER_ADMIN의 영역이다("3.3"). `api-merchant`에서 계정 관리 액션을 더할 때 와일드카드를 **넓혀야 했던** 것과 대비된다 — 규칙을 건드릴지 말지는 매번 경로 모양을 보고 판단한다.
- **`InternalUserIssuanceController` → `InternalUserController`로 이름을 바꿨다** — 목록이 생기면서 "Issuance"가 실제 책임보다 좁아졌다(`MerchantController`가 등록·목록을 함께 갖는 것과 같은 모양).
- **`InternalUserSummary`는 `passwordHash`를 담지 않는다**(Projection의 SELECT 목록에서부터 제외 — `MerchantUserSummary`와 같은 정신). 가맹점 Projection들과 달리 `merchant_seq` 범위 지정이 없다.
- **OpenAPI 스펙이 5개 → 8개가 됐다**: 목록·발급에 더해 `POST /admin/account-invitations/accept`도 이번에 문서화했다(1차에서 빠뜨렸는데 프론트가 실제로 호출한다).
- **실물 검증(`bootRun` + curl)**: 발급 → 명부에 `INVITED` → 수락 → 새 계정으로 로그인 → 명부에서 `ACTIVE` + `lastLoginAt` 채워짐까지 확인했고, **핵심 인가**로 같은 `OPERATOR` 세션이 `GET /admin/internal-users` **403**, `GET /admin/merchants` **200**, `POST /admin/internal-users` **403**을 받는 것을 확인했다(두 경로의 메서드 스코핑이 의도대로 갈리는지). 검증 계정은 정리했다.

### `SUPER_ADMIN` 발급 금지를 도메인으로 내렸다

처음에는 **프론트에서만** 선택지를 제한했다(`ISSUABLE_INTERNAL_ROLES` = OPERATOR/VIEWER) — `IssueInternalUserUseCase`가 `role`을 그대로 넘겨서 API를 직접 호출하면 `SUPER_ADMIN`을 만들 수 있었다. `docs/`의 "3.3"이 "최초 SUPER_ADMIN은 배포 초기화 명령, 안전한 운영 절차 또는 별도 Bootstrap 과정으로 생성한다"고 규정하는데 그 제약이 코드 어디에도 없던 것이다.

바로 이어서 **`InternalUser.invite`에 `require(role != SUPER_ADMIN)`을 넣어 닫았다** — `MerchantUser.inviteSubAccount`가 같은 이유로 `OWNER`를 막는 것과 정확히 같은 제약이고, 화면이 아니라 도메인이 규칙의 주인이어야 한다는 이 저장소의 원칙에 맞다. `IllegalArgumentException`은 `AdminApiExceptionHandler`의 기존 매핑이 400으로 옮긴다(새 예외 타입이 필요 없었다).

- **막는 층을 셋 다 테스트로 고정했다**: 도메인(`InternalUserTest`), Use Case(`IssueInternalUserUseCaseTest`), 컨트롤러 400 매핑(`InternalUserControllerTest`). 프론트의 선택지 제한은 UX로 남기고, 실제 방어선은 도메인이다.
- **MockK 함정 하나**: `every { repository.findByEmail(any()) }`처럼 value class 파라미터에 `any()`를 쓰면 MockK가 더미 인스턴스를 만들다 `Email`의 `require(contains("@"))`에 걸려 `InvocationTargetException`으로 죽는다. 이 파일의 다른 테스트들이 구체값(`EMAIL`)을 쓰고 있던 이유이기도 하다 — **`init { require(...) }`를 가진 value class에는 `any()`를 쓰지 않는다.**

## 내부 운영자 계정 관리 Use Case(`ChangeInternalUserStatusUseCase`/`ChangeInternalUserRoleUseCase`)와 "최소 1 활성 SUPER_ADMIN" 불변식

내부 직원을 정지·재개·종료하고 역할을 바꾸는 슬라이스다. 가맹점 쪽 "가맹점 계정 관리" 슬라이스를 내부 운영자로 옮긴 것이라 **구조·판단은 대부분 그대로 미러링했고, api-admin이 인가를 다루는 방식이 달라 갈리는 지점만** 여기 남긴다(공통 근거는 그 절 참고).

- **도메인에 `InternalUser.changeRole(newRole, changedAt)`을 추가했다** — `MerchantUser.changeRole`과 짝이다. `role`이 `val`이라 변경 자체가 불가능했고, `var role ... private set`으로 바꿔 `changeRole`로만 갱신되게 했다. `SUPER_ADMIN`으로의 승격은 `require`로 막는다(`invite`가 같은 제약을 갖는 것과 짝 — 최초 SUPER_ADMIN은 Bootstrap 경로뿐이다). 종료된 계정의 역할 변경도 막는다.
- **"최소 1 활성 SUPER_ADMIN" 불변식은 가맹점의 "최소 1 활성 OWNER"와 같은 자리(Use Case)·같은 성격이다.** 새 Port 메서드 `InternalUserRepository.countActiveSuperAdmins()`는 **범위 인자가 없다** — 내부 운영자는 가맹점에 속하지 않아서다(`countActiveOwners(merchantId)`와 다른 유일한 점). 정지·종료·강등 셋이 활성 SUPER_ADMIN 집합에서 빼는 연산일 때만 검사하고, 재개는 부르지 않는다. 사라지면 아무도 내부 계정을 발급할 수 없는 상태로 굳는다(발급이 SUPER_ADMIN 전용이고 그 위에 개입할 주체가 없다 — 복구는 Bootstrap뿐).
- **`InternalUserManagementGuard`는 요청자 권한을 다시 확인하지 않는다 — 가맹점 쪽 Guard와 의도적으로 다른 핵심 지점이다.** api-admin은 인가를 `SecurityConfig`의 정적 규칙에 맡기는 앱이고(`ListInternalUsersUseCase`/`IssueInternalUserCommand`의 지역 관행), `/admin/internal-users/**`가 SUPER_ADMIN 전용이라 **이 코드가 실행된다는 것 자체가 이미 SUPER_ADMIN 세션**이다. 그래서 요청자 식별자는 인가가 아니라 **자기 자신 차단**에만 쓴다. 가맹점 쪽에 있던 "ADMIN은 OWNER를 못 건드린다"에 대응하는 규칙도 여기 없다 — 요청자 역할이 하나(SUPER_ADMIN)뿐이라 대응물이 없다. 테넌시 확인도 없다.
- **`SecurityConfig`의 규칙을 `/admin/internal-users` 정확 경로 → `/admin/internal-users/**` 와일드카드로 넓혀야 했다.** 목록 조회(3번째 admin 슬라이스)에서는 새 `GET`이 base 경로와 **같은** 경로라 정확 규칙이 이미 덮어서 손대지 않았는데, 이번 관리 액션은 `/{id}/suspend` 같은 **다른** 하위 경로라 정확 규칙으로는 안 덮인다. 그대로 뒀다면 액션 경로가 `anyRequest, authenticated`로 떨어져 **OPERATOR/VIEWER도 정적 관문을 통과**했다(가맹점 쪽 `/merchant/merchant-users/**`와 정확히 같은 상황). Guard가 요청자 권한을 안 보고 "여기 왔으면 SUPER_ADMIN"을 전제하므로 이 1차 방어가 특히 중요하다 — `InternalUserControllerTest`에 OPERATOR가 액션 경로에서 403을 받는 회귀를 남겼다.
- **`IllegalStateException`을 전역 409로 매핑하지 않았다** — 도메인 전이 호출 한 줄만 감싸 `InvalidInternalUserTransitionException`으로 바꾸고 그 타입만 409로 매핑한다(가맹점 쪽과 같은 판단). 예외 4종을 `AdminApiExceptionHandler`에 추가했다: `InternalUserNotFoundException`(404), `InternalUserNotManageableException`(403, 자기 자신), `LastActiveSuperAdminException`(409), `InvalidInternalUserTransitionException`(409). `SUPER_ADMIN` 승격 시도는 도메인의 `IllegalArgumentException`이라 기존 400 매핑이 처리한다(새 타입 불필요).
- **`save()`의 UPDATE에 `ROLE_CODE`를 함께 넣었다 — 가맹점 쪽에서 실물로 겪은 버그를 재현 전에 막은 것이다.** `MerchantUserRepositoryAdapter`가 `role`을 `val`→`var`로 바꿀 때 UPDATE에 `role_code`가 빠져 "API는 200인데 DB는 옛 역할"인 조용한 유실을 겪었다(그 절 참고). `InternalUserRepositoryAdapter`도 같은 이유로 `role_code`가 빠져 있었고, `changeRole`을 추가하면서 같은 자리가 되므로 함께 채웠다. 회귀 테스트(`save persists a changed role`)를 어댑터 테스트에 남겼다.

### 불변식이 오늘의 API로는 도달 불가능하다(가맹점 쪽과 같은 상황)

요청자는 항상 활성 SUPER_ADMIN이고 자기 자신은 먼저 막히므로, 대상이 활성 SUPER_ADMIN이면 활성 SUPER_ADMIN이 이미 둘이다 — 그래서 `LastActiveSuperAdminException`은 **현재 HTTP 경로로는 트리거되지 않고** 단위 테스트로만 검증된다. 죽은 코드로 오해하지 않도록 남긴다: 규칙이 `docs/`("3.3")에 있고, 관리 권한 범위나 자기 자신 차단이 바뀌는 순간 곧바로 필요해진다(가맹점 쪽 `LastActiveOwnerException`과 같은 성격이며 계약 문서에도 적었다).

### KDoc 안의 `/**`가 컴파일을 깨뜨렸다 — Kotlin 블록 주석은 중첩된다

`InternalUserManagementGuard`의 KDoc에 `/admin/internal-users/**`를 그대로 적었더니 **파일 전체가 컴파일되지 않았다**("Unclosed comment"). Kotlin은 블록 주석이 **중첩**되므로, `/** ... */` 안의 `/**`(경로 와일드카드 표기)가 중첩 주석을 열어 바깥 KDoc의 `*/`가 그걸 닫고, 결국 주석이 EOF까지 이어진다. KDoc/블록 주석 본문에 `/**`나 `/*`를 문자 그대로 쓰지 않는다 — 경로 와일드카드는 "하위 경로 와일드카드"처럼 풀어 쓰거나, 정말 필요하면 `//` 라인 주석(중첩 안 됨)이나 코드 문자열 리터럴에만 둔다. `SecurityConfig`의 같은 표기는 `//` 라인 주석과 `authorize(...)` 문자열 리터럴이라 안전했다.

## 내부 운영자 콘솔에서 가맹점 계정 관리(`AdminChangeMerchantUser*UseCase`) — "최소 1 활성 OWNER"가 실제로 트리거되는 첫 경로

내부 운영자(`api-admin`)가 임의 가맹점의 사용자를 정지·재개·종료·역할 변경하는 슬라이스다. 가맹점이 스스로 잠기거나(마지막 OWNER 정지) 계정 사고가 났을 때 PG가 개입하는 경로다. `docs/`가 이 권한을 규정하지 않아 설계 판단을 먼저 `identity-access-api-key.md`의 4.6에 명문화했다(관리 행위=SUPER_ADMIN/OPERATOR, 조회=전원 — 가맹점 등록 `POST /admin/merchants`와 같은 스코핑).

- **merchant-side Use Case를 그대로 못 쓴다 — 행위자가 `MerchantUser`가 아니라 `InternalUser`라서다.** `ChangeMerchantUserStatusUseCase`는 `MerchantUserManagementGuard.loadAuthorizedRequester`로 요청자의 OWNER/ADMIN·ACTIVE를 확인하고 테넌시를 `requester.merchantId`로 잡고 자기 자신·ADMIN→OWNER를 막는데, admin 컨텍스트에는 그 요청자가 없다. 그래서 `Admin` 접두어 Use Case 3개를 새로 뒀되 **도메인 전이 메서드·`countActiveOwners`·불변식·예외·Projection은 그대로 재사용**했다. 인가는 `SecurityConfig` 정적 규칙이 지므로 Use Case는 요청자를 아예 받지 않고, 테넌시는 경로가 지정한 `merchantId`로 잡는다.
- **공유 Guard에 `loadTargetInMerchant(repo, merchantId, targetId)`만 더했다** — 테넌시 확인(다른 가맹점이면 404, 존재 여부 숨김)만 하는 메서드다. "최소 1 활성 OWNER"는 기존 `requireAnotherActiveOwnerRemains`를 그대로 부른다. merchant-user 관리 접근 로직을 한 파일에 유지했다.
- **이 경로가 `LastActiveOwnerException`을 HTTP로 처음 도달 가능하게 만든다.** 가맹점 계정 관리 슬라이스에서 "요청자 자기 자신 차단·ADMIN 제한 때문에 현재 HTTP 경로로는 트리거되지 않는다"고 적었던 그 방어선이다 — 내부 운영자 경로엔 그 제약이 없어 실제로 작동한다. Use Case 테스트에 그 케이스를 명시적으로 고정했다(`the last ACTIVE OWNER cannot be suspended — reachable via this HTTP path for the first time`).
- **조회는 `MerchantUserListProjection.findByMerchantId(merchantId)`를 그대로 재사용**했다(merchantId를 직접 받는 형태라 admin 조회에 그대로 맞았다). **가맹점 존재 확인은 생략**했다 — 없는 merchantId면 빈 목록일 뿐이고, 변경 경로는 대상 조회에서 이미 테넌시 404를 낸다. 그래서 `MerchantRepository`·별도 NotFound가 필요 없었다(계획 단계에서 넣으려다 뺐다).
- **`SecurityConfig`: `authorize(HttpMethod.POST, "/admin/merchants/**", hasAnyRole("SUPER_ADMIN","OPERATOR"))`로 넓혔다.** 기존 정확 경로 규칙(`POST /admin/merchants`)을 이 와일드카드가 대체하며, `GET`은 `anyRequest, authenticated`로 떨어져 VIEWER 포함 전원 조회가 된다(조회/변경 인가가 메서드로 갈린다). 하위 경로 mutation의 인가를 이 정적 규칙에만 맡기므로(Use Case가 요청자를 안 봄) 이게 유일한 관문이다.
- **프론트: 가맹점 목록 행 → 상세(`/merchants/:merchantId`)로 링크**하고, 상세에서 그 가맹점의 사용자 명부와 액션을 보여준다. 관리 액션은 `canManageMerchantAccounts`(SUPER_ADMIN/OPERATOR)일 때만 그린다 — VIEWER는 명부만. 가맹점 헤더 정보는 별도 단건 조회 엔드포인트 없이 이미 캐시된 가맹점 목록에서 찾는다. 액션 컴포넌트는 내부 직원판(`InternalUserActions`)과 같은 모양이되 OWNER 행은 역할 변경을 감추고(강등이 마지막 OWNER 보호에 걸리기 쉬움) 초대 재발송·취소는 없다.
- **또 KDoc 안의 `/admin/merchants/**`가 컴파일을 깨뜨렸다**(바로 위 절의 재발). 컨트롤러 KDoc에서 그 표기를 "하위 경로 POST 와일드카드 규칙"으로 풀어 고쳤다 — 이 함정은 슬라이스마다 반복되니 경로 와일드카드를 KDoc에 쓰려는 순간 멈춘다.
- **실물 검증(권장)은 아직 안 했다** — `bootRun`으로 OPERATOR 세션이 마지막 활성 OWNER 정지 시 409를 받는지가 이 슬라이스의 핵심 확인이다(HTTP로 처음 도달하는 불변식). `@WebMvcTest`가 정적 관문(VIEWER 403·GET 200)은 검증하지만 오류 디스패치·필터 체인은 실물로만 확인된다.

## 만료 Sweep 배치(`apps:batch`의 `expireAccountInvitationsJob`/`expireCheckoutsJob`)

도메인에 `expire()` 전이는 있는데 호출부가 없어 만료를 화면이 `expiresAt` 비교로만 판단하던 gap을 메운다(`AccountInvitation`은 명부, `CheckoutSession`/`Payment`는 체크아웃 조회의 410). `apps:batch`의 기존 3개 폴링 Worker(Scheduler+JobConfiguration+Tasklet 3파일 세트)를 그대로 복제해 Sweep Job 2개를 붙였다.

- **후보 애그리게이트를 그대로 전이시키지 않고 식별자로 다시 읽는다 — 재검증이 유일한 동시성 안전장치다.** Tasklet이 `findExpirablePending(now)`/`findExpirable(now)`로 후보를 뽑고, Use Case가 그 id로 다시 읽어 여전히 만료 가능한 상태일 때만 전이한다. 후보를 뽑은 뒤 초대가 수락되거나 결제가 진행됐을 수 있어서다(폴링이라 창이 있다). 상태가 이미 넘어갔으면 조용히 지나간다(Sweep은 best-effort 정리). 그래서 조회 결과가 살짝 낡아도 안전하다.
- **초대 Sweep은 단일 애그리게이트라 트랜잭션 경계가 없다.** `ExpireAccountInvitationUseCase`는 재검증 후 `expire()`+save뿐이다. `AccountInvitation.expire()`는 다른 두 대상과 달리 타임스탬프를 받지 않는다(`account_invitation`에 `updated_at`이 없다).
- **체크아웃 Sweep은 Payment와 CheckoutSession을 한 트랜잭션에서, 그러나 독립적으로 가드한다.** `ExpireCheckoutUseCase`는 `TransactionManager.runInTransaction` 안에서 Payment가 `CREATED`/`READY`면 만료, 세션이 `PAYMENT_SUBMITTED` 이전이면 만료 — 한쪽이 이미 그 창을 벗어나도 다른 쪽을 막지 않는다. 세션은 결제 생성 직후엔 아직 없을 수 있어 `null`도 정상이다(Payment가 먼저 `CREATED`로 생긴다). Sweep은 만료된 **Payment**를 후보로 몰아가고 세션은 `findByPaymentId`로 딸려 온다.
- **새 포트 조회는 Projection이 아니라 Command Repository에 뒀다** — `findPendingExchangeSettlement()`와 같은 도메인 규칙 보조 조회다(`PaymentRepository.findExpirable`, `AccountInvitationRepository.findExpirablePending`+`findById`). 상태·시각 조건의 jOOQ select이고, 어댑터 통합 테스트가 "PENDING+만료만/CREATED·READY+만료만 잡힌다"를 고정한다.
- **폴링 60초** — 만료 정리는 결제 흐름을 진행시키는 Confirm/Webhook/매도(10초)와 달리 급하지 않다. 다른 Worker와 다른 유일한 상수라 Scheduler KDoc에 근거를 남겼다.
- **실물 검증(권장)은 아직 안 했다** — 로컬 MySQL에 `expires_at`이 과거인 `PENDING` 초대·`CREATED` 결제를 넣고 `apps:batch:bootRun` → 한 폴링 뒤 각각 `EXPIRED`가 되고 이미 `ACCEPTED`/`PROCESSING`인 것은 안 건드리는지 확인하는 것이 핵심이다. 배치 폴링·트랜잭션 경계는 유닛/통합 테스트가 못 잡는 층이다.

## 내부 운영자 로그인 감사 로그(`internal_login_audit`, `GET /admin/login-audit`)

로그인 이력이 집계 필드(`last_login_at` 등) 스냅샷으로만 남던 것을, 시도 하나하나를 남기는 append-only 감사 로그로 확장했다(`docs/architecture/identity-access-api-key.md` 8·9절). 내부 운영자 로그인에 한정한 첫 감사 인프라이고, 가맹점 로그인·API Key 사용 감사는 이 인프라를 확장하는 후속으로 미뤘다(사용자와 범위 확정).

- **감사 이벤트를 도메인 불변 스냅샷으로 모델링했다**(`InternalLoginAudit`, 공개 생성자 `data class`) — `PaymentQuote`와 같은 취급이다(상태 전이 없는 append-only). 로깅 관심사를 도메인에 두는 게 과할 수도 있지만, 이 저장소가 `OutboxEvent`까지 도메인으로 모델링할 만큼 DDD 일관성을 지켜 와서 그 결에 맞췄다. Command 측 포트 `InternalLoginAuditRepository.append`와 읽기 측 `InternalLoginAuditProjection`으로 CQRS를 나눴다.
- **기록은 `AuthenticateInternalUserUseCase`가 모든 종료 지점에서** 한다 — 성공/없는 loginId/잠김/비활성/비밀번호 불일치 직전마다 `append`. 별도 Use Case가 아니라 outbound 포트 직접 호출이라 `ApplicationPurityTest`("Use Case는 다른 Use Case를 호출하지 않는다")를 지킨다.
- **실패도, 없는 계정도 남긴다.** 없는 `loginId` 시도는 `internal_user_seq`가 NULL(FK nullable)이고 `attempted_login_id`만 남긴다 — 존재하지 않는 계정 probing을 감사에서 볼 수 있다. 결과는 `SUCCESS`/`INVALID_CREDENTIALS`/`LOCKED` 셋으로, Use Case가 **클라이언트에는** 뭉개는 실패 사유를 감사에는 잠김만 구분해 남긴다.
- **감사 write는 로그인 성공 저장과 트랜잭션으로 묶지 않는다** — 단일 DB 전제라 append가 실패하면 로그인도 실패하는 것을 감수한다(감사 누락을 조용히 삼키지 않는다). Use Case KDoc에 근거를 남겼다.
- **클라이언트 IP는 `AdminLoginController`가 `HttpServletRequest.remoteAddr`로** 채워 Command로 넘긴다(Command에 `clientIp: String? = null` 기본값 — 기존 호출·테스트 안 깨짐). 프록시 뒤 실제 IP(`X-Forwarded-For`)는 다루지 않는다(9절 "로그인 IP 정책"과 함께 후속).
- **조회는 전역 최근 목록**(`GET /admin/login-audit`, `occurred_at DESC` 최대 200건) — 계정별 상세 페이지가 없어 그걸 새로 만들기보다 "최근 로그인/실패"를 한 화면에 보여주는 쪽이 보안 모니터링에 맞다. **SUPER_ADMIN 전용**이라 `SecurityConfig`에 `authorize("/admin/login-audit", hasRole("SUPER_ADMIN"))`(GET 전용, 정확 경로)를 더했다. Projection은 `internal_user`를 LEFT JOIN해 `user_name`을 붙이되, 없는 계정 시도는 JOIN이 안 맞아 `null`로 나온다.
- **새 테이블이라 Flyway V6 적용 후 `:db-core:jooqCodegen`을 먼저 돌려야 어댑터가 컴파일된다** — 이 순서를 어기면 생성 클래스가 없어 컴파일 실패한다(새 테이블 슬라이스의 고정 함정).
- **실물 검증(권장)은 아직 안 했다** — `api-admin:bootRun`으로 틀린 비밀번호·성공·없는 loginId 로그인을 한 뒤 `GET /admin/login-audit`에 세 건이 결과·IP와 함께 최신순으로 보이고 VIEWER/OPERATOR는 403인지 확인하는 것이 핵심이다(Security 필터·오류 디스패치는 `@WebMvcTest`가 못 잡는 층).

## 가맹점 로그인 감사 로그(`merchant_login_audit`) — 기록은 api-merchant, 조회는 api-admin

직전 `internal_login_audit`을 가맹점 관리자 로그인으로 확장했다. 대부분 그 슬라이스의 직접 미러라 여기는 갈리는 지점만 남긴다.

- **기록 앱과 조회 앱이 다르다 — 감사 인프라를 두 앱에 나눈 첫 사례다.** 로그인 Use Case(`AuthenticateMerchantUserUseCase`)가 있는 **api-merchant**가 `MerchantLoginAuditRepository.append`로 기록하고, 내부 운영자가 **전 가맹점**을 감독하는 **api-admin**이 `MerchantLoginAuditProjection`으로 조회한다. 두 앱 모두 `infra.persistence.jooq`를 컴포넌트 스캔하므로 어댑터(@Repository)는 양쪽에 다 잡히지만, Composition Root(`UseCaseConfiguration`)에서는 각 앱이 자기가 쓰는 포트만 배선한다(api-merchant는 append, api-admin은 projection+list use case). 조회 위치를 가맹점 콘솔이 아니라 admin으로 정한 것은 사용자와 확정했다(전 가맹점 지원·보안 감독).
- **merchantCode 테넌시가 얽힌다.** `AuthenticateMerchantUserUseCase`는 merchantCode로 가맹점을 먼저 찾으므로, 없는 merchantCode 시도는 `merchantId=null`(+`merchantUserId=null`), 가맹점은 찾았지만 loginId가 없으면 `merchantId`만 있고 `merchantUserId=null`이다. 시도한 merchantCode 원문과 nullable merchantId/merchantUserId를 함께 남긴다. Projection은 `merchant`·`merchant_user`를 둘 다 LEFT JOIN해 가맹점 이름·사용자 이름을 붙이되 없는 쪽은 null이다.
- **`attemptedMerchantCode`를 `MerchantCode` VO가 아니라 `String`으로 뒀다 — ArchUnit이 잡았다.** 처음엔 도메인 `MerchantLoginAudit`에 `attemptedMerchantCode: MerchantCode`로 뒀는데 `DomainPurityTest`("애그리게이트는 다른 애그리게이트를 `*Id`로만 참조한다")가 실패했다 — `MerchantCode`는 `Merchant` 애그리게이트의 VO다(`MerchantId`는 `*Id`라 허용, `LoginId`는 `domain.identity`의 공유 VO라 허용). 감사는 원문 시도값을 남기는 것이라 String이 의미상으로도 맞다. **다른 애그리게이트의 비-Id VO를 도메인 감사 기록에 넣지 않는다** — 원문 문자열로 담는다.
- **로그인 결과 enum은 내부/가맹점이 공유하는 `LoginOutcome` 하나다** — 처음엔 `InternalLoginOutcome`/`MerchantLoginOutcome`으로 병렬로 뒀다가, 값이 완전히 동일해(`SUCCESS`/`INVALID_CREDENTIALS`/`LOCKED`) 코드 리뷰에서 통합했다. 값이 갈리는 `InternalUserRole`/`MerchantUserRole`은 병렬로 두지만, 값이 같은 것은 `AccountStatus`/`LoginId`처럼 공유하는 이 저장소의 선례를 따른 것이다. 타입만 바뀌었고 DB 저장값(`.name`)·동작·프론트는 그대로다. 인가는 내부 로그인 감사(SUPER_ADMIN 전용)와 달리 **SUPER_ADMIN/OPERATOR**다 — OPERATOR가 "가맹점·결제·운영 업무"·가맹점 계정 관리를 맡기 때문. 프론트 게이트도 그래서 `canManageMerchantAccounts`(SUPER_ADMIN||OPERATOR)로 다르다.
- **새 테이블이라 Flyway V7 적용 + `:db-core:jooqCodegen`이 어댑터 컴파일보다 먼저**(V6과 같은 순서).
- **실물 검증(권장)은 아직 안 했다** — 두 앱이 같은 DB를 봐야 한다: api-merchant `bootRun`으로 틀린 비밀번호·성공·없는 merchantCode 로그인 → api-admin `bootRun`의 `GET /admin/merchant-login-audit`에 세 건이 가맹점·결과·IP와 함께 보이고 VIEWER 403·OPERATOR 200인지 확인.

## 동시 쓰기 보호 — 고위험 3개 애그리게이트에 행 잠금(`FOR UPDATE`)

`backend/CLAUDE.md`가 "알려진 한계"로 적어 두고 **"기존 애그리게이트를 다시 저장하는 첫 상태 전이 Use Case가 생기면 반드시 다시 검토한다"**고 단서를 달았던 낙관적 잠금 문제를 처리했다. 그 전제("지금은 `CreatePaymentUseCase`만 `save()`를 부른다")는 이미 깨져 있었다 — 만료 Sweep·Confirm Worker·체크아웃 API·API Key 인증이 전부 기존 애그리게이트를 재저장한다. 만료 Sweep을 추가할 때 이 단서를 확인하지 않아 위험을 키웠다.

- **왜 `save()` 안에서 잠그면 안 되나(핵심).** `save()`의 재조회에 `FOR UPDATE`를 붙여도 못 막는다 — 문제의 읽기는 Use Case가 앞서 부른 `findById()`다. 잠금은 **변경할 목적으로 읽는 시점**에 잡고 저장까지 유지돼야 하므로, 로드와 저장이 같은 트랜잭션에 있어야 한다. 그래서 `ConnectCheckoutWallet`/`CancelCheckoutSession`/`AuthenticateApiKey`는 저장을 묶으려고가 아니라 **잠금을 유지하려고** `TransactionManager`를 새로 받는다. `SubmitPaymentTransaction`은 로드가 트랜잭션 밖에 있어서 전체를 트랜잭션 안으로 옮겼다.
- **테스트가 문제를 먼저 증명했다.** `PaymentConcurrentWriteTest`를 잠금 없이 돌리면 실패하고(트랜잭션 안에서는 version 가드가 `IllegalStateException`으로 터진다), `findByIdForUpdate`로 바꾸면 통과한다. 적용 후 일부러 되돌려 실패를 재확인했다. **트랜잭션 밖 경로(당시의 체크아웃 2개·API Key 인증)에서는 같은 상황이 조용한 lost update**가 된다는 것이 이 작업의 실제 동기다.
- **가장 위험했던 것: API Key 폐기가 되돌려진다.** 인증은 매 결제 요청마다 `recordUsage()` 후 저장하는데 그 UPDATE가 상태 컬럼까지 자기 사본 값으로 쓴다 — 관리자가 폐기하는 사이 in-flight 인증이 저장되면 `ACTIVE`로 복구된다. `MerchantApiKeyConcurrentWriteTest`가 "어느 순서로 겹치든 최종 상태는 `REVOKED`"를 고정한다.
- **잠금 순서는 `Payment` → `CheckoutSession`으로 통일했다** — 반대로 잠그는 경로가 생기면 교착이 난다. `SubmitPaymentTransactionUseCase`가 세션을 **잠그지 않고 한 번 더 읽는** 이유가 이것이다(`paymentId`를 얻어 Payment부터 잠그려고). `ExpireCheckoutUseCase`는 세션을 `paymentId`로 찾은 뒤 그 id로 잠금 조회를 다시 한다 — `findByPaymentId`의 잠금 변형을 Port에 더하지 않기 위해서다.
- **`ConfirmBlockchainTransactionUseCase`는 의도적으로 제외했다** — 중간에 온체인 RPC 호출이 있어 잠그면 네트워크 지연 동안 행을 붙잡는다. 그 Use Case가 다루는 Payment는 이미 `PROCESSING`/`CONFIRMING`이라 만료 Sweep(`CREATED`/`READY`만 선택)·제출과 겹치지 않아 경합 창이 사실상 없다. **잠금 구간에 외부 호출을 넣지 않는다**는 규칙을 지킨 것이다.
- **범위는 고위험 3개로 한정했다**(사용자와 확정) — 나머지 7개 어댑터도 같은 재조회 패턴이지만 두 흐름이 같은 행을 동시에 바꾸는 경로가 없다. 그런 경로가 생기면 같은 방식으로 넓힌다.
- **실물 검증(권장)은 아직 안 했다** — `api-payment`에 같은 Key로 요청을 보내면서 `api-merchant`에서 그 Key를 폐기해, 폐기가 유지되는지 DB로 확인하는 것이 핵심이다.

## 수취 지갑을 요청에서 걷어내고 서버 설정으로 옮김(`ReceivingWalletRegistry`)

**문서 모순이 실제 구멍을 가리고 있던 사례다.** `docs/domain/glossary.md`는 수취 지갑을
"PG 관리 지갑"으로 정의하는데 `docs/guides/testnet-wallet-setup.md`는 "원래 가맹점이
지정하는 것"이라고 정반대로 적고 있었고, 코드는 후자를 따라 `POST /api/v1/payments`의
요청 본문으로 받으면서 허용 목록 검증도 없었다.

- **왜 위험한가**: 정산 흐름이 "PG가 USDC를 받아 매도하고 가맹점에 KRW 채권을 세운다"를
  전제한다(`SellToFakeExchangeUseCase`). 수취 지갑이 가맹점 것이면 가맹점은 USDC를 직접
  받으면서 KRW 채권까지 받아 **같은 대금이 두 번 나간다.** `ADR-007`이 실패 사유를 자금
  위치로 분류하는 것도("우리 지갑에 들어왔다") 이 전제 위에 서 있었다.
- **결정**: PG 수탁으로 통일하고(`docs/architecture/mvp-scope.md`의 "수취 지갑 귀속"),
  `receivingWallet`을 요청 필드에서 **없앴다**. 검증으로 막지 않고 필드를 없앤 이유는
  단순하다 — 검증은 빠뜨릴 수 있지만 없는 필드는 쓸 수 없다.
- **`network`는 남겼다** — 어느 체인으로 받을지는 가맹점의 정당한 선택이고 수탁 문제와
  무관하다. 그래서 레지스트리를 네트워크로 키잉했고, 다중 네트워크로 그대로 넓어진다.
- **`PaymentNetworkConfig`(코드 상수)에 넣지 않고 주입받는 클래스로 만들었다** — 실제
  자금을 보유하는 주소라 환경마다 다르고 저장소에 적을 수 없다. 조립은 `api-payment`의
  `UseCaseConfiguration`이 `@Value`로 한다.
- **기본값을 두지 않는다.** 다른 설정(`pepper`, DB 비밀번호)과 달리 "로컬 개발용
  기본값"이 성립하지 않는다 — 실제 테스트넷 USDC가 그 주소로 전송되고 되찾을 수 없다.
  비어 있으면 그 네트워크를 레지스트리에 등록하지 않아 결제 생성이 실패한다.
- **실패는 4xx가 아니라 503이다**(`ReceivingWalletNotConfiguredException`). 가맹점이 요청을
  고쳐서 해결할 수 있는 것이 없으므로 400으로 돌려주면 엉뚱한 곳을 고치게 만든다. 앱
  기동 자체는 설정 없이도 정상이다 — "환경변수 없이 `bootRun`이 동작한다"를 지키면서
  실패를 결제 생성 시점에만 드러낸다.
- **회귀 테스트 3개**: 지갑이 레지스트리에서 온다(`CreatePaymentUseCaseTest`), 설정이
  없으면 아무것도 저장하지 않고 예외(같은 파일), 가맹점이 옛 필드를 보내도 무시되고
  201이다(`PaymentControllerTest`). 프론트에는 **요청 본문에 `receivingWallet`이 없다**를
  고정하는 `DevPaymentCreator.test.tsx`가 있다.
- **프론트의 `VITE_DEV_RECEIVING_WALLET`이 사라졌다** — DEV 결제 생성 버튼이 더 이상 그
  값을 보내지 않는다. 로컬에서 실물 전송을 해보려면 `api-payment`를 띄우기 전에
  `APP_PAYMENT_RECEIVING_WALLETS_BASE_SEPOLIA`를 넣는다(`docs/guides/testnet-wallet-setup.md` 7절).
- **실물 검증 완료(`bootRun` + `curl`)**:
  - 설정 없이 앱이 정상 기동하고, 결제 생성만 **503**으로 실패한다.
  - 가맹점이 옛 `receivingWallet` 필드를 보내도 실제 컨테이너에서 무시된다(MockMvc 결과와 일치).
  - 설정 후 **201**이 나오고, 가맹점이 `0xeeee…`를 보냈는데 DB의
    `payment.receiving_wallet_address`는 설정값 `0x1111…`이었다 — **구멍이 닫힌 직접 증거다.**
  - 검증 데이터(결제 1건과 딸린 세션·견적·Outbox)는 삭제했다.
- **여기서 새 함정을 하나 배웠다**: 환경변수를 설정했는데 앱에 전달되지 않았다. `bootRun`의
  앱은 **Gradle 데몬의 자식**이라 데몬이 처음 뜰 때의 환경을 물려받는다 — 이미 떠 있는
  데몬은 나중의 `export`를 모른다. `gradlew --stop` 후 다시 띄워야 한다. 증상이 "설정했는데
  적용이 안 된다"라 원인이 드러나지 않아서, `backend/CLAUDE.md`의 "테스트가 잡지 못하는
  층" 표와 `docs/guides/testnet-wallet-setup.md` 7절에 함께 남겼다.

## 확정 이전 reorg 처리(`BlockchainTransaction.markReorged`)

ADR-007이 후속으로 미뤄 둔 `REORGED`를 보러 갔다가, **문서 공백이 아니라 도달 가능한
버그였다는 것을 발견했다.**

- **증상**: `ConfirmBlockchainTransactionUseCase`는 온체인 조회가 `null`이면 상태를 그대로
  두고 돌아간다. `SUBMITTED`(미채굴)에는 맞지만, 이미 블록 번호를 기록해 둔
  `DETECTED`/`CONFIRMING` 거래가 사라진 것은 뜻이 전혀 다르다 — reorg나 거래 교체다.
  그런데 그 결제는 **`CONFIRMING`에 영원히 갇혔다**: 만료 Sweep은 `CREATED`/`READY`만
  고르고, Confirm Worker는 계속 `null`만 받는다.
- **같은 `null`을 상태로 구분한다**가 이 슬라이스의 핵심이다(`handleMissingOnChain`).
- **유예를 둔다(`REORG_GRACE` = 10분).** 한 번의 `null`로 결제를 죽이지 않기 위해서다 —
  뒤처진 RPC 노드도 `null`을 돌려주고, reorg된 거래가 다음 블록에 다시 들어가는 것이
  오히려 흔하다. 반면 `Payment.FAILED`는 종료 상태라 오판을 되돌릴 수 없다. **짧게 잡아
  얻는 것(빠른 실패 판정)보다 길게 잡아 피하는 것(정상 결제의 회복 불가능한 실패)이 크다.**
- **마지막으로 온체인에서 본 시각은 `updatedAt`이다** — 별도 컬럼을 추가하지 않았다.
  거래를 볼 때마다 `detect`/`recordConfirmation`이 갱신하고, 못 찾은 폴링은 아무것도
  저장하지 않아 그 값이 그대로 남는다.
- **`markReorged`는 `DETECTED`/`CONFIRMING`에서만 허용한다.** `SUBMITTED`는 블록에서 본
  적이 없어 "사라졌다"가 성립하지 않고, `CONFIRMED` 이후는 `Payment = SUCCEEDED`와
  `ExchangeOrder`·`SettlementReceivable`까지 뒤집는 보상 흐름이 필요해 범위 밖이다
  (ADR-007에 그대로 남겨 뒀다).
- **마이그레이션이 필요 없었다**: 스키마의 `ck_blockchain_transaction_status`가 `REORGED`를
  이미 허용하고, `payment.failure_code`는 `VARCHAR(50)`에 CHECK 제약이 없어 새
  `PaymentFailureReason.TRANSACTION_REORGED`가 그대로 들어간다.
- **`TRANSACTION_REORGED`는 ADR-007의 자금 위치 분류에서 유일하게 "고객 지갑" 쪽이다** —
  전송 자체가 없어졌으므로 이 실패 사유만은 "돈이 오지 않았다"가 실제로 맞다.
- 조회 쿼리는 손대지 않아도 됐다 — `PENDING_CONFIRMATION_STATUSES`가 이미
  `SUBMITTED`/`DETECTED`/`CONFIRMING`만 고르므로 `REORGED`가 되면 Worker가 자동으로 뺀다.
- **테스트**: 도메인 3개(허용 전이, `SUBMITTED`에서 거부, `CONFIRMED`에서 거부), Use Case
  3개(유예 안, 유예 후 `REORGED`+`FAILED`, 오래된 `SUBMITTED`는 그대로).
- **실물 검증은 하지 않았다** — 테스트넷에서 reorg를 일부러 일으킬 방법이 없다. 유예
  경계는 고정 `Clock`으로만 검증했다.

## 결제 내역 엑셀(.xlsx) 내보내기(`XlsxPaymentExportWriter`)

조회 슬라이스에 이어 붙인 내보내기다. 계약은 `docs/architecture/admin-console-api.md`의 4.2.

- **POI를 어디에 둘지가 첫 결정이었다.** 앱(inbound Adapter)에 두면 `HexagonalLayerTest`의
  "앱은 outbound Port를 구현하지 않는다"에 걸리고 두 콘솔에 복제된다. `modules:application`에
  두면 `ApplicationPurityTest`(인프라 라이브러리 금지)에 걸린다. 그래서 **Port를
  `application.port.outbound`에, 구현을 `modules:infra-support`에** 뒀다 — 두 앱이 컴포넌트
  스캔으로 같은 구현을 공유한다.
  - JSON 직렬화는 Port를 거치지 않는데 이건 거치는 이유: JSON은 프레임워크가 응답 표현으로
    알아서 처리하지만, 스프레드시트는 **우리가 라이브러리를 직접 불러 만드는 산출물**이라
    그 의존성이 어느 계층에 있는지가 드러나야 한다.
- **`SXSSFWorkbook`(스트리밍)을 쓴다.** 일반 `XSSFWorkbook`은 모든 셀을 힙에 들고 있다.
  **`dispose()`를 빠뜨리면 임시 파일이 서버에 쌓인다** — `finally`에서 부른다.
- **상한 10,000행, 그리고 상한+1건을 조회한다.** 정확히 상한만큼 조회하면 "딱 맞게 채워진
  것"과 "넘쳐서 잘린 것"을 구분할 수 없다. `COUNT`를 한 번 더 돌리는 것보다 싸다.
- **잘림을 반드시 알린다**(`X-Export-Truncated` 헤더 → 화면 경고). 본문이 바이너리라 JSON
  필드로 전할 수 없다. **교차 출처에서 읽으려면 CORS `exposedHeaders`에 있어야 한다** —
  빠뜨리면 프론트가 잘림을 모르고, 사용자는 일부만 담긴 파일을 그냥 받아간다. 이 기능에서
  가장 위험한 실패라 백엔드·프론트 양쪽에 회귀 테스트를 뒀다.
- **프론트가 `<a href>`가 아니라 `fetch`로 받는다**(`http.ts`의 `createDownload`) — 세션
  쿠키(`credentials: 'include'`)와 위 헤더 둘 다 필요해서다. 링크로 받으면 브라우저가
  파일만 저장하고 헤더는 화면 코드에 닿지 않는다.
- **엑셀에서는 금액을 숫자 셀로 쓴다.** JSON 응답이 문자열인 것과 다른 판단인데, 그쪽은
  JavaScript `Number`의 안전 정수 범위가 문제였고 여기서는 그 제약이 없다 — 받는 사람이
  합계·정렬을 해야 한다. 환산은 `BigDecimal.movePointLeft`로 하고 `Double` 연산을 거치지
  않는다.
- **시각은 KST로 적는다**(API는 UTC). 사람이 바로 읽는 산출물이라 시차 계산을 시키지 않고,
  열 제목에 시간대를 밝힌다.
- **파일 이름은 ASCII만 쓴다** — 한글은 RFC 5987 인코딩이 필요하고 브라우저마다 갈린다.
- **이 엔드포인트는 OpenAPI 스펙에 넣지 않았다** — 응답이 바이너리라 스키마로 적을 것이 없고
  실제와 어긋난 스키마를 만들 위험만 남는다(오류 응답을 스펙에서 빼는 것과 같은 판단).
- **`@WebMvcTest` 슬라이스에 `Clock` Bean이 필요해졌다** — 컨트롤러가 파일 이름에 현재
  시각을 넣는다. `UseCaseConfiguration`이 로드되지 않는 슬라이스라 `FixedClockConfiguration`을
  테스트에 직접 뒀고, 덕분에 파일 이름을 문자열로 고정 검증할 수 있다.
- **실물 검증 완료(`bootRun` + 로그인 + 다운로드)**: 로그인은 CSRF 때문에 GET으로 XSRF
  쿠키를 먼저 받아야 했다. 받은 파일이 실제 OOXML(zip, `PK` 매직, 9개 엔트리)이고,
  **한글 헤더가 깨지지 않았으며**(`inlineStr`로 들어간다 — SXSSF는 sharedStrings를 쓰지
  않는다), 금액 셀이 `t="n"`(숫자)로, 나머지는 문자열로 들어간 것을 sheet XML에서 직접
  확인했다. 검증에 쓴 파일은 삭제했다.

## MVP 완주 실물 검증 — 브라우저에서만 드러난 버그 3개

`docs/architecture/mvp-scope.md`가 정의한 완료 경계(`Payment SUCCEEDED` + `ExchangeOrder
COMPLETED` + `SettlementReceivable READY`)를 **처음으로 실제 온체인 결제로 통과시켰다.**
그전까지 `settlement_receivable`은 0건이었다 — 전체 흐름이 한 번도 완주된 적이 없었고,
그 이유가 아래 세 버그다. **셋 다 `curl`·MockMvc·jsdom 테스트는 전부 통과하는 상태에서
존재했다.**

| # | 버그 | 왜 테스트가 못 잡았나 |
|---|---|---|
| 1 | DEV 결제 생성이 브라우저에서 CORS로 차단 | `curl`·MockMvc는 `Origin`을 보내지 않아 CORS 자체를 거치지 않는다 |
| 2 | 수취 지갑이 EIP-55 체크섬 형태가 아니면 지갑 단계에서 막힘 | 백엔드는 체크섬을 검증하지 않고(의도적), viem은 요구한다 — 두 계층의 규칙이 다른데 어느 쪽 테스트도 경계를 넘지 않았다 |
| 3 | 지갑 등록이 세션이 아니라 **연결 동작**에 묶여 있었음 | wagmi가 연결 상태를 브라우저에 저장한다는 사실은 jsdom 단위 테스트에 없다 |

- **1번**: `SecurityConfig`가 CORS를 `/checkout/**`에만 거는 것은 의도된 보안 경계다
  (`checkout-api.md` 2.1) — 앱 전체에 걸면 API Key로 보호되는 `POST /api/v1/payments`가
  브라우저 호출 표면이 된다. **백엔드를 넓히지 않고** Vite 개발 서버 프록시로 풀었다
  (`/api/v1` → :8081). 프록시는 개발 서버에만 있고 프로덕션 번들에 없다. 고객 대면
  체크아웃 호출은 **일부러 프록시를 태우지 않았다** — 운영에서도 교차 출처라 개발 중에도
  진짜 CORS를 겪어야 설정이 깨진 것을 미리 발견한다.
- **2번**: 프론트 `asAddress`가 viem `getAddress`로 정규화한다(대소문자만 바뀌므로 주소는
  같다). **남은 gap**: 운영자가 수취 지갑을 오타로 넣은 경우는 여전히 잡지 못한다 —
  진짜 방어는 백엔드가 설정을 읽는 시점에 체크섬을 검증해 기동을 실패시키는 것이다.
- **3번이 가장 나쁘다 — 돈은 나가고 결제는 실패한다.** 새 체크아웃 세션을 열어도 wagmi가
  연결을 복원해 화면이 연결 버튼을 건너뛰므로, 그 세션은 지갑이 등록된 적이 없는 채로
  전송 서명까지 간다. 등록을 `pay()` 안에서 **세션 상태**(`session.connectedWallet`) 기준으로
  하도록 바꿨고, `walletRegistration.test.ts`가 회귀로 고정한다(수정을 빼면 실제로
  실패하는 것까지 확인했다).

**되살리기가 가능했던 이유**: 3번을 만났을 때 고객은 이미 USDC를 보낸 뒤였는데, 온체인
기록이 남아 있어 세션에 지갑을 등록하고 그 Hash를 제출하는 것만으로 결제를 완주시킬 수
있었다. ADR-007이 정한 "수령 사실을 `blockchain_transaction`에 보존한다"는 원칙이 실제로
쓸모를 증명한 자리다.

**완주 결과**(20,000원 주문):

| 단계 | 값 |
|---|---|
| 고객 전송 | 14.357502 USDC (적용 환율 1,393 = 1,400 × 0.995) |
| Fake Exchange | `COMPLETED`, 체결 1,400 → 20,101 KRW 확보 |
| 정산채권 | `READY`, gross 20,000 − fee 300 = **net 19,700** |

확보액(20,101)이 주문액(20,000)보다 큰 것이 정상이다 — 고객에게는 스프레드가 적용된
환율로 청구하고 매도는 시장 환율로 체결되므로 그 차액이 PG 마진이다.

## 수취 지갑 오타 방어 — 기동 시 EIP-55 검증(`WalletAddressChecksum`)

실물 검증에서 남겨 둔 gap을 닫는다. 프론트가 주소를 `getAddress`로 정규화하게 해서 소문자
설정으로 결제가 막히는 문제는 풀었지만, **정규화는 오타를 감춘다** — 한 글자가 틀린 주소도
정규화하면 "체크섬만 맞는, 아무도 통제하지 못하는 주소"가 되어 그대로 통과한다.

- **방어를 기동 시점으로 옮겼다.** `api-payment`가 뜰 때 `app.payment.receiving-wallets.*`의
  체크섬을 검증하고 어긋나면 컨텍스트가 아예 뜨지 않는다. 프론트에 두면 이미 결제 화면까지
  온 고객에게 실패가 드러나는데 그건 너무 늦다 — 설정이 잘못됐다는 사실은 **배포 시점에**
  알아야 한다.
- **소문자로만 적힌 주소도 거부한다.** 체크섬 정보가 없어 오타를 검증할 방법이 없기
  때문이다. 지갑은 언제나 체크섬 형태로 보여주므로 복사해 넣으면 통과한다.
- **오류 메시지에 정규 형태를 찍지 않는다.** 찍으면 운영자가 그 값을 그대로 복사해 넣는데,
  오타였다면 그건 남의 주소라 오타를 확정시킨다. "지갑에서 다시 복사하라"가 유일하게 옳은
  안내다 — 이걸 회귀 테스트로도 고정했다(`shouldNotContain`).
- **keccak256을 직접 구현하지 않았다.** 이 계산이 틀리면 정상 주소를 거부하거나 오타를
  통과시키는데, 그 오류를 우리 테스트가 잡아주지 못한다(검증 대상이 곧 검증 도구가 된다).
  `web3j-crypto`의 `Keys.toChecksumAddress`를 쓴다.
- **자리는 Port(application) + 구현(`modules:infra-support`)이다** — `PaymentExportWriter`(POI)와
  같은 판단이다. `modules:infra-blockchain`에도 web3j가 있지만 그쪽은 `Web3jConfiguration`이
  RPC URL 설정을 강제해서 api-payment가 끌어다 쓸 수 없다.
- **실물 검증**: 마지막 글자만 `e1`→`e2`로 바꾼 주소로 `bootRun`하면 컨텍스트 초기화가
  취소되며 기동이 거부되고(로그의 한글 메시지도 정상), 원래 주소로는 그대로 뜬다. 양쪽 다
  확인했다.

**남은 것**: `domain`의 `WalletAddress`는 여전히 체크섬을 보지 않는다(의도적). 고객 지갑
주소처럼 기계가 넘겨주는 값에는 EIP-55가 방어 수단이 아니고, 온체인 조회 결과를 소문자로
받는 경로도 있어서다. 검증은 **사람이 설정하는 값**에만 건다.

## 구매자 개인정보 — 암복호를 **어댑터가 아니라 Use Case 경계**에 뒀다(ADR-008)

ADR-008이 정한 것(무엇을 받고, 어떻게 보관하고, 누가 읽나)은 그 문서에 있다. 여기 적는 것은
**구현하면서 갈렸던 자리**다.

### 왜 Repository 어댑터가 암호화하지 않나

처음 모양은 `PaymentCustomerRepositoryAdapter(dsl, encryptor, indexer)`였다. 그러면 도메인
`PaymentCustomer`(평문)를 그대로 주고받아 대칭이 깔끔하다. **배선이 그걸 막았다** — 네 앱이
전부 `paytech.practice.pay.infra.persistence.jooq`를 통째로 컴포넌트 스캔하므로, 어댑터가
`PiiEncryptor`를 주입받는 순간 **개인정보를 아예 다루지 않는 `api-merchant`와 `batch`도 AES
키 설정 없이는 기동하지 못한다.** 키가 닿는 앱이 둘에서 넷으로 늘어나는데, 그건 ADR-008이
"읽는 경로를 좁힌다"고 말한 것과 정반대다.

그래서 Port가 도메인이 아니라 **이미 암호화된 `EncryptedPaymentCustomer`를 오간다.**

- `modules:infra-persistence`는 이제 **복호화할 방법이 없다.** 옮기기만 한다.
- 변환은 `application.customer.PaymentCustomerCrypto`가 하고, 그 Bean은 각 앱의
  `UseCaseConfiguration`에서 조립되므로 **실제로 필요한 앱에만 존재한다.**
- 대가는 타입이 하나 늘고 Repository 대칭이 깨지는 것이다. `@ConditionalOnBean`으로 어댑터를
  조건부 등록하는 길도 있었지만, 컴포넌트 스캔에서의 조건부 Bean은 순서에 좌우돼 **"어떤 앱은
  조용히 저장이 안 되는"** 실패로 이어질 수 있어 택하지 않았다.

### 수정 경로가 복호화를 타지 않는다

고객이 오타를 고치면(같은 세션에서 다시 제출) 세 항목을 전부 새 값으로 덮어쓰므로 **옛 평문이
필요 없다.** 그래서 기존 행을 복호화하지 않고 `reconstitute` + `change`로 넘긴다 —
`PaymentCustomerCrypto.decrypt`를 부르는 곳은 앞으로도 **원본 열람 Use Case 하나**여야 하고,
그 호출은 감사 기록과 같은 트랜잭션 안에 있어야 한다.

### 입력 단계가 `open()`을 하게 됐다

`CheckoutSession.open()`을 부르던 곳은 `ConnectCheckoutWalletUseCase` 하나였다("고객이 처음
행동한 순간 = 지갑 연결"). 구매자 정보 입력이 그보다 앞서므로 **두 Use Case가 모두** `CREATED
→ OPEN`을 처리한다. 한쪽으로 몰지 않은 이유는 API가 순서를 강제하지 않기 때문이다 — 순서를
지키는 것은 프론트이고, 백엔드는 어느 쪽이 먼저 와도 받는다.

### 입력을 받을 수 있는 경계는 `PAYMENT_SUBMITTED`다(취소와 같은 자리)

`CheckoutSession`에는 구매자 정보 입력에 해당하는 **상태 전이가 없다** — 즉 도메인의
`checkTransition`이 뒤에서 받쳐 주지 않아서, Use Case의 확인이 유일한 방어선이다. 전송이
브로드캐스트된 뒤에 연락처가 바뀌면 그 결제에 문제가 생겼을 때 **연락할 상대가 소리 없이
달라진다**(ADR-007의 "돈은 나갔는데 결제는 실패"가 정확히 그 상황이다).

### 응답에 마스킹만 싣는다

방금 입력한 본인에게 돌려주는 값이라 평문을 실어도 새로 새는 정보는 없다. 그래도 마스킹만
보내는 이유는 **예외를 하나 만들면 다음 응답이 그 예외를 근거로 삼기** 때문이다. "이 API의
응답에는 구매자 원본이 없다"가 예외 없는 규칙이면 나중에 심사할 것이 없다.

### 테스트

- `SubmitCheckoutCustomerUseCaseTest`(단위 9개) — 되돌릴 수 있는 **가짜** 암호화를 끼워서
  "평문이 저장 형태로 나갈 때 반드시 변환을 거치는가"를 본다(암호 자체는
  `AesGcmPiiEncryptorTest`가 검증한다). **Blind Index가 정규화된 값으로 만들어지는지**를
  따로 고정했다 — 여기가 틀리면 `A@b.com`을 입력한 사람이 검색에 걸리지 않는데, 그 사실은
  검색 기능을 만들기 전까지 드러나지 않는다.
- `PaymentCustomerAdapterTest`(Testcontainers MySQL, 5개) — 컬럼 길이·문자셋처럼 **실제
  MySQL에 넣어야만 드러나는 것**을 본다: Base64 암호문이 `VARCHAR(512)`에서 잘리지 않는지,
  마스킹된 한글 이름이 그대로 돌아오는지, 두 번째 저장이 새 행을 만들지 않고 같은 행의
  version을 올리는지.
- `CheckoutControllerTest`에 3개(200/마스킹만 응답, 잘못된 이메일 400, 제출 후 409),
  `CheckoutApiDocumentationTest`에 OpenAPI 스니펫 1개를 더했다.
### 실물 검증 — 예상 못 한 실패는 없었고, 대신 검증 방법에서 한 번 물렸다

`bootRun`(api-payment) + 실제 MySQL로 아홉 가지를 확인했다. 결제·세션은 테스트 픽스처와 같은
방식으로 SQL로 심었다 — 결제 생성은 MVP 완주 검증에서 이미 증명된 경로이고, 여기서 닫으려는
gap은 **환경변수로 들어온 실제 키로 암호화한 값이 진짜 MySQL을 왕복하는가**였다.

| 확인 | 결과 |
|---|---|
| 기동 시 개발용 기본 키·Pepper WARN | 둘 다 찍힌다 |
| 입력 200, 세션 `CREATED → OPEN` | 정상 |
| 응답에 평문 없음 | 마스킹 셋만 나간다 |
| DB 암호문에 평문 흔적 | 이름·이메일·전화 전부 0건(`LIKE '%평문%'`) |
| 마스킹 컬럼의 한글 | `홍*동` 그대로(utf8mb4 정상) |
| Blind Index | hex 64자, `CHAR(64)`에 맞는다 |
| 같은 값 재제출 | **암호문은 달라지고 인덱스는 같다** — 랜덤 IV와 검색 가능성이 동시에 성립한다 |
| 오타 수정 | 같은 행 UPDATE, `version` 0→1→2, 행 수 1 유지 |
| 400/404/409 | 잘못된 이메일 400, 없는 세션 404, `PAYMENT_SUBMITTED` 이후 409 |
| 로그 | `PaymentCustomer 수정` 2회(재제출 2회와 일치), 로그 전체에 평문 0건 |

**물린 것은 서버가 아니라 검증 도구였다.** 첫 요청이 `400 요청 본문을 읽을 수 없습니다`로
떨어져 잠깐 서버를 의심했는데, 원인은 Git Bash가 명령줄의 한글을 UTF-8로 넘기지 않은 것이었다.
`printf`로 파일에 쓰고 `--data-binary @파일`로 보내니 그대로 통과했다 — **한글이 든 요청을
`curl -d '...'`로 직접 치지 않는다**(`backend/CLAUDE.md`의 "셸에서 값을 만들 때" 항목과 같은
결의 함정이다). 검증에 쓴 데이터와 임시 파일은 전부 지웠다.

**여전히 실물로 확인하지 못한 것**: 체크아웃 화면에서 사람이 입력하는 경로(프론트가 아직 없다)와
`api-admin`의 원본 열람(구현이 없다).

### 운영 로그는 세 자리에만 넣었다

이 기능에서 **로그가 없으면 운영이 알아차릴 수 없는 것**만 골랐다(규칙은 `backend/CLAUDE.md`의
"로깅" 절).

| 자리 | 수준 | 왜 |
|---|---|---|
| `AesGcmPiiEncryptor` 복호화 실패 | WARN | 거의 언제나 **두 앱의 `app.pii.encryption-key` 불일치**인데, 예외만 올라가면 "인증 태그 불일치"라는 암호 라이브러리 메시지만 남아 원인이 드러나지 않는다 |
| 두 클래스의 기동 시점 기본값 확인 | WARN | 개발용 기본 키·Pepper를 그대로 들고 떠도 **아무 증상이 없다** — 알아차릴 계기가 로그밖에 없다 |
| `PaymentCustomerRepositoryAdapter`의 수정(UPDATE) | INFO | **수정됐다는 사실이 DB 어디에도 남지 않는다**(옛 값을 보관하면 파기가 반쪽이 되므로 이력 테이블을 두지 않았다) — 이 한 줄이 유일한 흔적이다 |

- **암호문도 찍지 않는다.** 로그는 파기 대상 밖이라 더 오래 남고, 나중에 키가 새면 그 로그가
  곧 평문이 된다. 세 로그 모두 식별자(`paymentId`)까지만 남긴다.
- **컨트롤러에는 로그를 넣지 않았다** — 이 저장소의 API 앱에는 요청 로그가 하나도 없고, 한
  엔드포인트만 예외로 두면 그게 새 관행이 된다.

### 남긴 것

- ~~원본 열람과 Blind Index 검색이 없다~~ — 아래 절에서 붙였다.
- ~~체크아웃 화면에 입력 단계가 없다~~ — `frontend/payment`의 `CustomerForm`이 채웠다(지갑 단계보다 앞에 두고, 순서는 `PayScreen`이 강제한다).
- **`GET /checkout/sessions/{id}`가 입력 여부를 알려주지 않는다** — 새로고침한 고객은 입력
  화면을 다시 만난다(다시 제출하면 덮어쓰므로 결과는 같다). 판단 근거는
  `docs/architecture/checkout-api.md`의 8절에 적었다.

## 구매자 원본 열람과 검색(`api-admin`) — 읽기에 감사를 붙인 자리

ADR-008이 정한 것(SUPER_ADMIN만, 열람마다 기록, 가맹점 콘솔에는 없음)은 그 문서에 있다.
여기는 구현하면서 갈렸던 자리다.

### 권한을 한 등급으로 묶지 않았다

`/admin/payment-customers/**`를 통째로 `SUPER_ADMIN`으로 잠그는 편이 규칙은 단순하다
(`/admin/blockchain-transactions/**`가 그렇게 돼 있다). 그렇게 하지 않은 이유는 **검색까지
좁히면 이 기능을 만든 이유가 사라지기** 때문이다 — ADR-008이 개인정보를 받기로 한 근거 중
하나가 "가맹점 CS의 '제 주문 어떻게 됐나요'"인데, 그 대응을 하는 것은 OPERATOR다.

갈라진 기준은 **응답에 원문이 실리는가**다.

| 경로 | 권한 | 응답 |
|---|---|---|
| `GET /admin/payment-customers` | SUPER_ADMIN/OPERATOR | 마스킹만 |
| `POST /admin/payment-customers/{paymentId}/reveal` | **SUPER_ADMIN만** | 원문 + 감사 기록 |

`SecurityConfig`에서 **좁은 규칙을 먼저 쓴다**(`POST .../*/reveal` → 그다음 `/**`). 순서가
뒤집히면 OPERATOR가 열람까지 하게 되는데, 그 회귀는 테스트가 없으면 조용히 지나간다 —
`AdminPaymentCustomerControllerTest`의 "OPERATOR can search but cannot reveal"이 그 자리다.

VIEWER는 검색도 막았다. **개인정보를 키로 삼는 조회는 목록을 훑는 것과 성격이 다르다** —
결제 목록(`GET /admin/payments`)이 VIEWER에게 열려 있는 것과 대비된다.

### 열람이 `GET`이 아니라 `POST`인 이유

읽기처럼 보이지만 **감사 기록을 남기는 쓰기**다. `GET`으로 두면 브라우저 프리페치·링크
미리보기·캐시가 사람의 의도 없이 열람을 일으킬 수 있는데, 이 자료는 "봤다"는 사실 자체가
사건이라 그런 경로가 있으면 안 된다. CSRF도 그래서 따라온다.

### 없는 결제를 앞에서 막아야 했다 — 안 그러면 404가 500이 된다

`PaymentCustomerRepositoryAdapter.findByPaymentId`는 `dsl.paymentSeq(paymentId)`로 seq를
푸는데, 그 공용 헬퍼는 **없는 값이면 `error(...)`로 즉시 실패한다**(FK 대상의 존재를 호출부가
보장한다는 전제다 — `SeqResolution`의 KDoc). 없는 결제 ID로 열람을 부르면 `404`여야 할
요청이 `500`으로 나간다.

그래서 Use Case가 `PaymentRepository.findById`로 먼저 확인한다. **공용 헬퍼의 의미를 바꾸지
않은 것**이 판단의 핵심이다 — nullable 변형을 하나 더 만들면 다른 12개 어댑터가 "어느 쪽을
써야 하나"를 매번 고르게 된다.

결제가 없는 경우와 구매자 정보가 없는 경우는 **같은 예외**다(`PaymentCustomerNotFoundException`).
나눠서 알려주면 "그 결제는 존재한다"가 응답으로 새어 나간다.

### 검색 Port는 인덱스 문자열을 받는다

`findByEmailIndex(emailIndex: String)`이지 `findByEmail(email)`이 아니다. 인덱스 계산은
`PaymentCustomerCrypto`(application)가 하고, 어댑터는 받은 문자열로 컬럼을 비교만 한다 —
**`modules:infra-persistence`에 Pepper를 주지 않으려는 것**이고, 암복호를 Use Case 경계에 둔
것과 같은 이유다.

어제 YAGNI로 지웠던 `emailIndex`/`phoneIndex` 헬퍼를 여기서 되살렸다. 지운 판단도 되살린
판단도 같은 기준이었다 — **호출부가 생겼을 때 만든다.**

### 테스트

- `SearchPaymentCustomersUseCaseTest`(5) — **복호화를 부르면 터지는 가짜 암호화**를 끼웠다.
  검색이 복호화 경로를 타지 않는다는 약속을 구조로 확인하는 것이지 눈으로 보는 게 아니다.
  정규화된 값으로 인덱스를 만드는지, 두 조건을 AND로 걸지 못하게 막는지도 고정했다.
- `RevealPaymentCustomerUseCaseTest`(6) — 누가·왜·어디서를 기록하는지, **기록에 원문이 담기지
  않는지**, 사유가 비면 복호화 자체를 시도하지 않는지.
- `PaymentCustomerAdapterTest`에 검색 5개 추가 — 실제 MySQL에서 조인·정렬·컬럼 혼동(이메일
  검색이 전화 인덱스에 걸리지 않는지)을 본다. **Blind Index 값을 테스트마다 유일하게 만든다**:
  같은 DB를 공유하므로 고정값을 쓰면 앞 테스트의 행이 뒤 테스트 결과에 섞인다.
- `AdminPaymentCustomerControllerTest`(13) — 역할 셋을 전부 박았다. 이 컨트롤러에서 가장
  위험한 회귀는 기능이 안 되는 것이 아니라 **권한이 넓어지는 것**이다.

### 남긴 것

- **결제 상세(4.1.1)에 마스킹된 구매자 정보가 없다.** 지금은 검색이 유일한 입구라, 결제를
  먼저 찾은 운영자는 그 결제에 구매자 정보가 있는지조차 모른다. 넣으려면 가맹점 콘솔의 같은
  엔드포인트도 함께 봐야 한다(ADR-008은 가맹점도 마스킹은 본다고 정해 두었다).
- **열람 감사를 읽는 경로가 없다.** `customer_pii_access_audit`에 쌓이기만 한다 — 로그인
  감사와 같은 모양이면 된다.
- **관리자 화면(`frontend/admin`)이 없다.** 백엔드만 붙었다.
- 검색어가 URL 쿼리로 들어간다(접근 로그에 남는다). 내부 전용이라 지금은 감수하고, 문제가
  되면 `POST` 검색으로 옮긴다.
