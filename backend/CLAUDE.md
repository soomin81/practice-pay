# CLAUDE.md (backend)

`backend/`에서 작업할 때의 지침이다. 공통 결제 도메인(애그리게이트, 상태 머신, MVP 범위)은 루트 `../CLAUDE.md`를 참고한다 — 이 문서는 백엔드 구현 컨벤션만 다룬다.

## 최상위 디렉토리는 "역할"로 나눈다

`backend/` 바로 아래의 구분 기준은 **Gradle 서브프로젝트냐 아니냐가 아니라 역할이 무엇이냐**다. `db-core`와 `architecture-tests`가 `modules/` 밖에 있는 건 정리가 덜 된 게 아니라 이 기준을 따른 결과다 — 일관성 문제로 보고 `modules/` 아래로 옮기지 않는다.

| 위치 | 역할 | 판별 기준 |
|---|---|---|
| `apps/` | 배포 단위 | 자체 `@SpringBootApplication` 메인 클래스와 `application.yaml`을 갖고 독립 실행된다 |
| `modules/` | 제품 라이브러리 | 헥사고날 계층(`domain` → `application` → `infra-*`)에 속하고, 다른 모듈이 의존해서 쓴다 |
| `db-core/` | 스키마 원천 + 코드 생성 | 유일하게 **빌드에 외부 상태(실행 중인 MySQL)가 필요**하고, 소스가 사람이 쓴 Kotlin이 아니라 SQL 마이그레이션 + jOOQ 생성 코드다. 헥사고날 계층 어디에도 속하지 않는 그 아래층이다 |
| `architecture-tests/` | 검증 하네스 | **아무도 의존하지 않고 자기가 전부를 의존한다**(의존 방향이 `modules/`와 정반대). `src/main`이 없다 |
| `build-logic/` | 빌드 인프라 | Composite Build로 포함된 별도 빌드(convention plugin 제공) |

새 서브프로젝트를 만들 때는 "Gradle 모듈이니까 `modules/`"가 아니라 위 판별 기준에 비춰 자리를 정한다.

## 현재 구현 상태

`modules:domain`, `modules:application`, `modules:infra-persistence`, `modules:infra-blockchain`, `modules:infra-support`, `modules:common`, `db-core`, `architecture-tests`, 그리고 `apps:*` 4개 전부 실제 Gradle 서브프로젝트다(`settings.gradle.kts` 참고). `modules:common`만 아직 `src`가 비어 있다(빌드는 NO-SOURCE로 통과한다) — 실제로 필요해질 때까지 다른 모듈에 대한 의존성도 추가하지 않았다(아래 항목 참고). 빈 서브프로젝트에 코드/배선이 있다고 가정하지 말고, 참조하기 전에 먼저 확인한다. 이 구조가 이미 여러 번 재편됐으니 의존하기 전에 다시 확인한다:

```
apps/
  api-payment/       실제 Gradle 서브프로젝트, 독립 배포 가능한 Spring Boot 앱(자체 메인 클래스, 자체 포트) —
                     webmvc + jooq + security이고 modules:application + modules:infra-persistence에 의존한다.
                     CreatePaymentUseCase(POST /api/v1/payments)를 MerchantApiKey Bearer 인증으로 보호한다
                     (Apps 절 참고).
  api-admin/         실제 Gradle 서브프로젝트, 독립 배포 가능한 Spring Boot 앱 — webmvc + jooq + security,
                     modules:application + modules:infra-persistence에 의존한다. AuthenticateInternalUserUseCase
                     (POST /admin/login), IssueInternalUserUseCase(POST /admin/internal-users, SUPER_ADMIN 전용),
                     RegisterMerchantUseCase(POST /admin/merchants, SUPER_ADMIN/OPERATOR),
                     ListMerchantsUseCase(GET /admin/merchants, 인증된 내부 사용자 전원 —
                     VIEWER 포함), AcceptAccountInvitationUseCase(POST /admin/account-invitations/accept,
                     비인증)가 있다(Apps 절 참고).
  api-merchant/      실제 Gradle 서브프로젝트, 독립 배포 가능한 Spring Boot 앱 — webmvc + jooq + security,
                     modules:application + modules:infra-persistence에 의존한다. AuthenticateMerchantUserUseCase
                     (POST /merchant/login), AcceptAccountInvitationUseCase(POST /merchant/account-invitations/accept,
                     비인증, api-admin과 같은 공용 Use Case를 재사용), InviteMerchantSubAccountUseCase
                     (POST /merchant/merchant-users, OWNER/ADMIN), IssueMerchantApiKeyUseCase/
                     RevokeMerchantApiKeyUseCase/ListMerchantApiKeysUseCase(POST·DELETE·GET
                     /merchant/api-keys, OWNER/ADMIN — GET도 VIEWER는 막는다)가
                     있다(Apps 절 참고). 이 앱의 MVP 흐름이 전부 구현됐다.
  batch/             실제 Gradle 서브프로젝트, 독립 배포 가능한 Spring Boot 앱 — spring-boot-starter-batch +
                     jooq + modules:application/infra-persistence/infra-blockchain에 의존한다. Job 셋:
                     confirmBlockchainTransactionJob(BlockchainTransaction 감지·Confirm 폴링 Worker),
                     publishOutboxEventJob(OutboxEvent 발행 Worker, Webhook HTTP 호출 포함),
                     sellToFakeExchangeJob(Fake Exchange 매도 폴링 Worker) 셋 다 10초 주기다
                     (셋의 구현 판단은 IMPLEMENTATION-NOTES.md 참고). 웹 스타터는 여전히 없다.
modules/
  application/       실제 Gradle 서브프로젝트, domain에 의존; ConnectCheckoutWalletUseCase(application.checkout,
                     지갑 연결 슬라이스), CreatePaymentUseCase(결제 생성 슬라이스),
                     SubmitPaymentTransactionUseCase(BlockchainTransaction 생성 슬라이스),
                     ConfirmBlockchainTransactionUseCase(감지·Confirm 슬라이스) + PaymentTransactionValidator
                     + PaymentNetworkConfig(공유 MVP 상수), PublishOutboxEventUseCase(application.outbox,
                     OutboxEvent 발행 슬라이스), SellToFakeExchangeUseCase(application.exchange, Fake Exchange
                     매도 슬라이스 — MVP 완료 경계의 마지막 조각), Identity/API Key Use Case
                     (Authenticate*/IssueInternalUser), BlockchainClient(온체인 조회 Port, 구현체는
                     modules:infra-blockchain) + 그 outbound port들(Architecture 참고).
                     주의: ConnectCheckoutWalletUseCase와 SubmitPaymentTransactionUseCase는
                     구현돼 있지만 어떤 앱에도 배선되지 않았다 — 이 둘을 노출할
                     고객 대면 API가 아직 없어서다("Hosted Checkout API" 절 참고).
  common/            실제 Gradle 서브프로젝트, 의존성 없음, src 비어 있음 — 어떤 레이어에서도 쓸 수 있는 공용
                     유틸리티가 실제로 필요해질 때 채운다(순환 의존을 피하려고 지금은 어떤 modules:*도
                     참조하지 않는다)
  domain/            실제 Gradle 서브프로젝트, 의존성 없음; 8개 결제 애그리게이트 전부 + OutboxEvent + Identity/API Key 애그리게이트(Domain code conventions 참고)
  infra-blockchain/  실제 Gradle 서브프로젝트, domain+application에 의존 — modules:application의
                     BlockchainClient Port를 web3j로 구현하는 Web3jBlockchainClient가 있다
                     (Base Sepolia RPC 조회, 아래 "온체인 Adapter" 참고). apps:batch가 이 모듈에
                     의존하는 첫 앱이다(RPC URL은 apps:batch의 application.yaml에 있다).
  infra-persistence/ 실제 Gradle 서브프로젝트 — modules:application의 outbound port를 구현하는 jOOQ Repository Adapter(Architecture 참고)
  infra-support/     실제 Gradle 서브프로젝트, domain+application에 의존 — 특정 외부 시스템에 묶이지 않는
                     자잘한 outbound Port 구현을 모은다(UuidIdGenerator/BCryptPasswordEncoderAdapter/
                     HmacInvitationTokenHasher/FakeExchangeRateProvider). 원래 앱마다 복제돼 있던 것을
                     모았다(아래 "modules:infra-support" 절 참고).
db-core/             실제 Gradle 서브프로젝트 — Flyway 마이그레이션 + jOOQ 코드 생성(아래 참고)
architecture-tests/  실제 Gradle 서브프로젝트, 테스트 전용(src/main 없음) — 다른 모듈의 컴파일된 클래스에 대한
                     ArchUnit 규칙(Spec 5개, 검사 대상은 modules:* 4개 + apps:* 4개 전부 — "ArchUnit" 절 참고)
```

**루트 프로젝트에는 자체 코드가 없다.** 원래 Spring Initializr 스켈레톤 앱(`PracticePayApplication.kt`, 삭제됨)이었는데, `apps/api-payment`가 실제 결제 API 배포 단위 역할을 넘겨받으면서 중복이 됐다 — 그래서 중복으로 남겨두지 않고 삭제했다. `backend/build.gradle.kts`는 이제 모든 서브프로젝트에 적용되는 횡단 관심사 `allprojects {}` 블록(ktlint + `repositories {}`)만 갖고 있다 — 루트 프로젝트 자체에는 Kotlin이나 Spring Boot 플러그인을 적용하지 않는다. `backend/src/` 아래에 소스를 추가하지 말고, 해당하는 `apps:*` 또는 `modules:*` 서브프로젝트에 추가한다.

## `build-logic`(Gradle Convention Plugin)

11개 서브프로젝트가 `kotlin("jvm")` 버전, Java 25 toolchain, `compilerOptions`, kotest/mockk 좌표, Spring Boot BOM, Spring 앱 공통 의존성을 그대로 반복하던 걸 `backend/build-logic/`(Composite Build로 포함된 빌드)의 Precompiled Script Plugin으로 뽑아냈다 — `buildSrc` 대신 포함된 빌드를 쓴 이유는 `buildSrc`가 바뀌면 루트 빌드 전체가 매번 무효화되지만, 포함된 빌드는 독립된 빌드라 그 캐시 이점이 그대로 유지되기 때문이다. `backend/settings.gradle.kts` 맨 앞의 `pluginManagement { includeBuild("build-logic") }`이 이 빌드를 끌어온다.

`build-logic/src/main/kotlin/*.gradle.kts` 7개:

| 이름 | 내용 | 적용 대상 |
|---|---|---|
| `practicepay.kotlin-common` | `kotlin("jvm")`, Java 25 toolchain, 공통 `compilerOptions`, `useJUnitPlatform()` | 전체 12개 서브프로젝트 |
| `practicepay.kotest` | `testImplementation` kotest-runner-junit5/kotest-assertions-core | 테스트가 있는 곳(db-core 제외) |
| `practicepay.mockk` | `testImplementation` mockk | domain/application/infra-blockchain/infra-support/common/apps 4개(infra-persistence/db-core/architecture-tests는 각자 다른 테스트 전략이라 제외) |
| `practicepay.spring-bom` | `io.spring.dependency-management` + Spring Boot BOM import | infra-persistence/infra-blockchain/infra-support/db-core |
| `practicepay.spring-library` | `practicepay.spring-bom` + `kotlin("plugin.spring")`(Bean을 open으로) | infra-persistence/infra-blockchain/infra-support(db-core는 `@Component` Bean이 없어서 제외) |
| `practicepay.spring-boot-app` | `practicepay.kotlin-common` + `kotlin("plugin.spring")` + `org.springframework.boot` + `io.spring.dependency-management` + 4개 앱 공통 의존성(`spring-boot-starter-jooq`, `spring-boot-starter-flyway`+`flyway-mysql`(부팅 시 스키마 자동 적용, "Flyway" 절 참고), `kotlin-reflect`, `jackson-module-kotlin`, `kotlin-logging-jvm`, `mysql-connector-j`, springmockk, testcontainers 3종, `kotest-extensions-spring`, `junit-platform-launcher`) | api-payment/api-admin/api-merchant/batch — 단 api-payment/api-admin/api-merchant는 아래 `spring-web-app`을 거쳐 간접 적용되고, batch만 직접 적용한다 |
| `practicepay.spring-web-app` | `practicepay.spring-boot-app` + webmvc/security/validation 스타터 3종 + 대응 test 스타터 2종(`spring-boot-starter-webmvc-test`/`spring-boot-starter-security-test`) | api-payment/api-admin/api-merchant(batch는 웹 앱이 아니라서 `spring-boot-app`을 직접 쓴다) |

**모듈마다 다른 부분은 그대로 각 `build.gradle.kts`에 남겨둔다** — `project(":modules:...")` 의존성 목록은 모듈 그래프를 그 파일만 보고 파악할 수 있어야 해서 convention plugin으로 감추지 않는다. 즉 "모듈마다 작은 설정을 중복한다"는 예전 컨벤션은, **버전·플러그인처럼 절대 갈릴 이유가 없는 설정**은 convention plugin으로 걷어내고(`webmvc`/`security`/`validation` 스타터도 세 웹 API 앱에서 똑같이 반복되던 것이라 `spring-web-app`으로 걷어냈다) **모듈마다 실제로 다른 의존성 그래프**는 여전히 각자 명시하는 쪽으로 갈렸다.

**알려진 한계**: `build-logic/src/main/kotlin/*.gradle.kts`(Precompiled Script Plugin)는 `backend/gradle/libs.versions.toml` 버전 카탈로그의 `libs` 접근자를 쓸 수 없다(`build-logic/build.gradle.kts` 자신은 되지만, `kotlin-dsl`이 컴파일하는 이 스크립트들의 컴파일 classpath에는 카탈로그 접근자 클래스가 없다 — Gradle의 알려진 한계). 그래서 이 7개 파일 안의 버전 문자열은 `libs.versions.toml`과 값을 손으로 맞춰야 한다(각 파일 KDoc에 표시해뒀다). 반면 메인 빌드에 남아있는 개별 `build.gradle.kts`(예: `modules/infra-blockchain`의 `libs.web3j.core`, `architecture-tests`의 `libs.archunit`, `db-core`의 `alias(libs.plugins.jooq.codegen)`)는 카탈로그를 정상적으로 쓴다.

## 명령어

`backend/`에서 실행한다(Windows: `gradlew.bat` 사용, POSIX 셸용 래퍼 스크립트 `gradlew`도 있다). 더 이상 빌드/실행할 단일 루트 앱이 없다 — 특정 서브프로젝트를 지정한다:

```
gradlew.bat build                                        # 전체 빌드, 모든 서브프로젝트(컴파일 + 테스트 실행)
gradlew.bat :apps:api-payment:bootRun                     # 특정 앱을 로컬에서 실행(MySQL 필요 — 아래 참고)
gradlew.bat test                                          # 전체 테스트, 모든 서브프로젝트
gradlew.bat :apps:api-payment:test --tests "*PaymentApiApplicationTests.contextLoads"   # 단일 테스트 메서드, 서브프로젝트 하나
gradlew.bat ktlintCheck                                   # 모든 모듈 Lint(`check`/`build`의 일부로도 실행됨)
gradlew.bat ktlintFormat                                  # 모든 모듈 자동 포맷
```

- **빌드 설정은 `backend/gradle.properties`에 있다** — `org.gradle.parallel`/`caching`/`configuration-cache`를 전부 켜뒀고, 한글 주석을 위해 `-Dfile.encoding=UTF-8`을 고정했다. Configuration Cache는 켜기 전에 `build`/`:db-core:jooqCodegen`/`bootRun` 셋에서 저장·재사용이 정상 동작하는 것을 확인했다 — 새 플러그인을 도입한 뒤 문제가 생기면 `--no-configuration-cache`로 우회하고 그 플러그인의 지원 여부부터 확인한다.
- 로컬 MySQL: `compose.yaml`이 `docker compose up`용 `mysql:latest` 서비스를 정의하고, `stablecoin_payment` DB로 시딩된다(스키마와 일치 — 아래 "Database / jOOQ 코드 생성" 참고). `apps:*` 네 앱 전부 테스트에서는 대신 Testcontainers를 자동으로 쓴다(각자 `TestcontainersConfiguration.kt`가 `@ServiceConnection`으로 MySQL 컨테이너를 띄운다) — `apps:batch`도 Confirm Worker가 생기면서 `DataSource`가 필요해져 같은 패턴을 따라간다.
- 툴체인: Java 25, Kotlin 2.3.21, Spring Boot 4.1.0(각 `apps:*`/`db-core`/`modules:infra-persistence` 서브프로젝트 기준 — 루트 프로젝트 자체는 더 이상 Kotlin이나 Spring Boot 플러그인을 적용하지 않는다). 버전은 `backend/gradle/libs.versions.toml`에 모여 있다(위 "build-logic" 절 참고).
- Lint/포맷: **ktlint**를 `org.jlleitschuh.gradle.ktlint` 플러그인(14.2.0)으로 모든 모듈에 적용한다(계층형 `modules:domain`/`modules:application` include를 위해 Gradle이 만드는 Phantom 부모 `:modules`도 포함) — 루트 `build.gradle.kts`의 `allprojects {}`를 통해서다. ktlint는 `build-logic`의 convention plugin으로 옮기지 않고 여기 그대로 뒀다 — `:modules` phantom project는 자기 `build.gradle.kts`가 없어서 convention plugin을 적용할 수 없고, 이미 잘 동작하고 문서화돼 있는 방식을 바꿀 이유가 없었다. `backend/.editorconfig`가 `indent_style = tab`을 고정해서(이 프로젝트의 기존 컨벤션) ktlint가 스페이스로 강제 포맷하지 않게 한다. `ktlintCheck`는 이미 `check`/`build`의 일부로 실행되므로, 빌드가 성공하면 Lint도 깨끗하다는 뜻이다. `db-core/build.gradle.kts`는 `generated-src`(jOOQ가 생성한 코드, 직접 수정하지 않음)를 Lint 대상에서 제외하고, 그걸 읽는 ktlint 태스크에 명시적으로 `dependsOn("jooqCodegen")`을 추가한다 — Gradle의 태스크 입력 검증이, 어떤 디렉토리를 읽는 태스크라면 그 디렉토리를 만드는 태스크에 대한 의존성 선언을 요구하기 때문이다.

## 설정과 비밀값(`application.yaml`)

각 앱의 `application.yaml`에 있는 값은 **전부 로컬 개발용 기본값**이고, 운영에서는 환경변수로 덮어쓴다. 소스에 실제 운영 값(DB 비밀번호, Pepper, 유료 RPC URL)을 적지 않는다.

| 설정 | 환경변수 | 앱 |
|---|---|---|
| `spring.datasource.url`/`username`/`password` | `SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD` | 4개 앱 전부 |
| `app.api-key.pepper` | `APP_API_KEY_PEPPER` | api-payment/api-merchant |
| `app.invitation-token.pepper` | `APP_INVITATION_TOKEN_PEPPER` | api-admin/api-merchant |
| `app.blockchain.base-sepolia.rpc-url` | `APP_BLOCKCHAIN_BASE_SEPOLIA_RPC_URL` | batch |

- **`${ENV:기본값}` 문법이 환경변수 덮어쓰기를 가능하게 하는 게 아니다.** Spring Boot는 환경변수를 `application.yaml`보다 우선하는 property source로 이미 읽고, `APP_INVITATION_TOKEN_PEPPER` 같은 이름을 `app.invitation-token.pepper`로 자동 매핑한다(relaxed binding) — `${...}` 없이 리터럴만 적어둬도 환경변수가 값을 덮어쓰는 것을 실제로 확인했다. 그럼에도 `${...}`로 적는 건 **환경변수 이름을 설정 파일만 보고 알 수 있게** 하고 "이 값은 주입받는 것"임을 드러내기 위해서다.
- **`app.invitation-token.pepper`는 `api-admin`과 `api-merchant`가 반드시 같은 값이어야 한다.** 초대는 발급 시점에 `hash(원문 Token)`을 `account_invitation.token_hash`에 저장하고 수락 시점에 다시 `hash(원문 Token)`으로 조회하는데(`AcceptAccountInvitationUseCase`), 발급 앱과 수락 앱이 다르기 때문이다(가맹점 등록은 `api-admin`이 발급하고 `api-merchant`가 수락한다 — `IMPLEMENTATION-NOTES.md`의 "가맹점 등록 Use Case" 절 참고). Pepper가 어긋나면 초대를 영영 찾지 못하고, 그때 나오는 예외는 원인을 숨기도록 설계된 `InvalidInvitationException`("유효하지 않은 초대")이라 추적이 매우 어렵다 — **한쪽만 교체하지 않는다.** 실제로 `bootRun`으로 발급→수락 전체 흐름을 검증했다.
- **`app.api-key.pepper`도 같은 이유로 `api-payment`와 `api-merchant`가 반드시 같은 값이어야 한다.** `api-merchant`가 발급(`hash(rawApiKey)`)하고 `api-payment`가 인증(`matches(rawApiKey, secretHash)`)한다(`IMPLEMENTATION-NOTES.md`의 "API Key 발급/폐기 Use Case" 절 참고) — 두 앱의 Pepper가 다르면 방금 발급한 Key로 결제 API를 호출해도 401이 난다. 이것도 `bootRun`으로 발급→결제 생성 전체 흐름을 검증했다.
- **Pepper 교체는 기존 데이터를 무효화한다.** `app.api-key.pepper`를 바꾸면 기존 API Key의 `secret_hash`가 전부 맞지 않게 되고(원문이 없어 재계산 불가), `app.invitation-token.pepper`를 바꾸면 아직 수락되지 않은 초대가 전부 무효가 된다. 교체하려면 각각 API Key 재발급/초대 재발급이 함께 필요하다.
- 로컬 개발 기본값은 `compose.yaml`의 MySQL(`localhost:3306/stablecoin_payment`, `root`/`verysecret`)과 Base Sepolia 공개 RPC(`https://sepolia.base.org`)를 가리킨다 — 환경변수를 하나도 설정하지 않아도 `bootRun`이 그대로 동작해야 한다는 뜻이다.

## 테스트

- 테스트 프레임워크는 **Kotest**(`FunSpec` 스타일)다 — JUnit5의 `@Test`/`kotlin-test`가 아니다. `gradlew.bat test`는 JUnit Platform 위의 `kotest-runner-junit5`를 통해 Kotest Spec을 자동으로 수집한다(`useJUnitPlatform()`이 이미 설정돼 있음, 추가 설정 불필요).
- Spring 컨텍스트 테스트는 Spec 본문 안에서 `extensions(SpringExtension)`으로 `io.kotest.extensions.spring.SpringExtension`을 등록하고, 평소처럼 `@SpringBootTest`/`@Import` 애노테이션도 함께 쓴다(`apps/api-payment/src/test/kotlin/paytech/practice/pay/api/payment/PaymentApiApplicationTests.kt` 참고). `@Autowired`로 필드 주입을 받아야 하는 테스트(예: `@WebMvcTest` 슬라이스)는 `FunSpec({ ... })` 트레일링 람다 대신 `FunSpec() { @Autowired lateinit var ...; init { ... } }` 형태를 쓴다 — 람다 생성자로는 `@Autowired` 필드를 선언할 자리가 없어서다(`PaymentControllerTest` 참고).
- Mocking은 Mockito가 아니라 **MockK**(`io.mockk`)를 쓴다. Spring Boot Test 슬라이스에서 Bean을 Mock으로 바꿔야 할 때(`@MockBean`/`@SpyBean` 자리)는 Mockito 전용인 그 애노테이션 대신 MockK판인 `com.ninja-squad:springmockk`의 `@MockkBean`을 쓴다(`PaymentControllerTest` 참고) — 이 프로젝트 전체가 Mockito 없이 MockK 하나로 통일돼 있다.
- Assertion은 `kotest-assertions-core`(`shouldBe` 등)를 쓴다.
- 아키텍처 규칙(예: domain이 Spring/jOOQ에 의존하지 않는다, 아래의 헥사고날 계층 구조)은 **ArchUnit**(`com.tngtech.archunit:archunit`)으로 강제한다 — 별도의 `archunit-junit5` 엔진/`@AnalyzeClasses` 스타일이 아니라, 평범한 Kotest `test { }` 블록 안에서 `ClassFileImporter().importPackages(...)` + `.check(classes)`를 호출하는 방식이다. 프로젝트 전체가 하나의 테스트 작성 컨벤션(Kotest)을 유지하기 위해서다. 모듈 간 규칙(한 모듈의 컴파일된 클래스를 외부에서 검사)은 `architecture-tests`(테스트 전용 Gradle 서브프로젝트)에 둔다 — 아래 절 참고.

## 테스트가 잡지 못하는 층 — 실제로 띄워서 확인한다

**"테스트가 전부 통과한다"가 "동작한다"를 뜻하지 않는 영역이 있다.** 이 프로젝트에서 실제로 그렇게 새어 나간 버그가 셋이고, 셋 다 자동화된 테스트는 초록색인 채로 존재했다. 새 기능을 끝냈다고 판단하기 전에 아래 표에서 해당하는 층이 있는지 확인하고, 있으면 **한 번은 실물로 돌려본다.**

| 무엇이 가려졌나 | 왜 테스트가 못 잡나 | 어떻게 드러났나 |
|---|---|---|
| `BigInteger.toLong()`이 `Long` 범위 초과분을 조용히 잘라 `TokenAmount`가 음수가 됨 | 유닛 테스트가 Mock한 응답은 **실제 RPC 응답의 값 범위**를 재현하지 않는다(18-decimals 토큰 전송량은 흔히 `Long.MAX_VALUE`를 넘는다) | 실제 Base Sepolia RPC에 진짜 Transaction Hash로 조회(아래 "온체인 Adapter" 절) |
| 새로 만든 DB 볼륨에서 **모든 DB 연결 실패**(`RSA public key is not available client side`) | MySQL 9의 `caching_sha2_password`는 첫 인증 성공 후 서버가 캐싱해서, **기존 볼륨에서는 원리적으로 재현되지 않는다.** Testcontainers도 매번 새 컨테이너지만 JDBC 옵션이 달라 드러나지 않았다 | `docker compose down -v` 후 README 세팅 흐름을 처음부터 따라감 |
| 잘못된 요청 본문에 400이 아니라 **401**이 나감(404/405/500도 마찬가지) | 컨테이너의 `/error` **ERROR 디스패치**에서 벌어지는 일인데, `@WebMvcTest`의 MockMvc는 그 디스패치를 재현하지 않는다 | 실제 `bootRun` + `curl` |

- **실물 검증이 필요한 대표적인 층**: 외부 시스템 실제 응답(RPC/HTTP), DB 연결 옵션과 드라이버 동작, Spring Security 필터 체인과 오류 디스패치, 컴포넌트 스캔 배선, 설정값(`application.yaml`) 해석. 반대로 도메인 규칙·Use Case 분기·상태 전이는 유닛 테스트가 충분히 잡는다.
- **`spring.datasource.*`는 테스트가 아예 검증하지 않는다** — 네 앱 모두 테스트에서 Testcontainers `@ServiceConnection`이 datasource를 덮어써서, `application.yaml`의 URL/계정이 깨져 있어도 전체 테스트가 통과한다. 이 값을 건드렸으면 반드시 `bootRun`으로 확인한다.
- 검증에 쓴 임시 데이터(스모크 테스트 결제 행, 임시 DB)는 확인 후 정리하고, **그 과정에서 얻은 교훈은 이 표에 한 줄 추가한다.**
- 실물 검증 중 셸에서 값을 만들어 DB에 넣을 때 주의: Windows Git Bash에서 `openssl base64`는 `\r\n`을 출력해서 `tr -d '\n'`만으로는 **`\r`가 남는다**. 이걸 해시 컬럼에 넣으면 눈에 보이지 않는 1바이트 차이로 인증이 실패하고, 로그로 출력해도 두 값이 똑같아 보인다(길이/HEX를 찍어야 드러난다). 진단하다가 도리어 데이터를 오염시킨 실제 사례다.

## ArchUnit(`architecture-tests`)

`architecture-tests`는 **검사 대상 모듈 전부**(`modules:domain`/`application`/`infra-persistence`/`infra-blockchain`/`infra-support` + `apps:*` 4개)를 `testImplementation`으로 받아서, 컴파일된 클래스에 규칙을 건다. Spec 5개가 각각 하나의 관심사를 맡는다:

| Spec | 무엇을 지키나 |
|---|---|
| `HexagonalLayerTest` | 의존 방향 — `layeredArchitecture()`로 Domain/Application/Outbound Adapter/Inbound Adapter 4계층을 정의하고 "안쪽은 바깥쪽을 모른다"를 강제한다. inbound(`apps:*`)와 outbound(`modules:infra-*`) Adapter가 서로를 모른다는 규칙까지 포함한다 |
| `DomainPurityTest` | 도메인 순수성 — 프레임워크·인프라 라이브러리 금지, 영속성 전용 시각 타입(`LocalDateTime`/`Date`) 금지, **"애그리게이트는 다른 애그리게이트를 `*Id`로만 참조한다"**(커스텀 `ArchCondition`) |
| `ApplicationPurityTest` | 애플리케이션 순수성 — 같은 프레임워크 금지 목록, Repository Port는 인터페이스, Use Case가 다른 Use Case를 직접 호출하지 않음 |
| `PersistenceAdapterTest` | jOOQ 생성 코드가 `infra.persistence.jooq` 밖으로 새지 않음, Adapter는 Spring Bean이자 outbound Port 구현체 |
| `NamingConventionTest` | 이름이 정해지면 자리도 정해진다 — `*UseCase`→`application..`, `*RepositoryAdapter`→`infra.persistence.jooq..`, `@RestController(Advice)`→`..web..` |

- **공용 패키지 상수와 임포트 결과는 `ArchitectureTestSupport.kt`(`Packages` 오브젝트 + `productionClasses`)에 모은다** — Spec마다 패키지 문자열을 직접 적거나 `ClassFileImporter`를 다시 돌리지 않는다.
- **ArchUnit은 대상 클래스가 하나도 없으면 규칙을 조용히 통과시킨다** — 모듈이 classpath에서 빠지거나 패키지가 바뀌면 규칙 전체가 무력화되는데도 빌드는 초록색이 된다. `HexagonalLayerTest`의 `every layer must actually be imported` 테스트와 `layeredArchitecture()`의 기본 동작(빈 레이어는 실패)이 이 상황을 잡는다. 규칙을 새로 추가할 때는 **일부러 깨뜨려서 실제로 실패하는지 한 번 확인한다**(예: 패키지 이름을 존재하지 않는 값으로 바꿔보고 되돌린다).
- `architecture-tests`는 `practicepay.spring-bom`도 적용한다 — 검사 대상 모듈들이 Spring Boot BOM으로 버전을 받는 좌표(`org.jooq:jooq` 등)를 transitive로 끌고 오는데, BOM이 없으면 버전 없이 도착해 `testRuntimeClasspath` resolve 자체가 실패한다.
- 새 인프라 라이브러리를 도입하면 `DomainPurityTest.kt`의 `FORBIDDEN_IN_PURE_LAYERS` 목록에 추가한다(도메인/애플리케이션 두 Spec이 공유한다).

## 아키텍처(헥사고날)

```
inbound adapter → application → domain ← outbound port ← outbound adapter
```

**실제 모듈 계층**: `modules:domain` → `modules:application` → `modules:infra-*`(영속성 Adapter는 `modules/infra-persistence`의 `infra.persistence.jooq` 패키지) → `db-core`의 jOOQ 생성 코드. `docs/architecture/persistence-jooq.md`의 "구조" 절은 `adapter/outbound/persistence/jooq`라는 **계획 단계의 경로**를 적고 있는데, 실제 구현은 위 경로로 갔다 — 같은 계층 구조를 다르게 표기한 것일 뿐이니 경로 문자열을 그대로 따르지 않는다.

- 도메인 코드는 Spring, jOOQ, HTTP 클라이언트, 어떤 블록체인 SDK에도 의존하지 않는다 — 순수 Kotlin 외에는 아무것도 의존하지 않는다.
- 애그리게이트는 다른 애그리게이트를 항상 ID로만 참조하고, 객체 참조로는 참조하지 않는다.
- **CQS(Command Query Separation)**를 메서드 단위로 지킨다: 메서드는 상태를 바꾸고 아무것도 반환하지 않거나(Command — 예: `Payment.ready()`, `submit()`, `succeed()`), 부수효과 없이 데이터를 반환하거나(Query — 예: `payment.status` 조회) 둘 중 하나이지, 둘 다는 아니다. 상태를 바꾸면서 계산된 결과까지 돌려주는 메서드는 추가하지 않는다.
- **영속성 레벨의 CQRS**: Command Repository는 애그리게이트 전체를 저장·복원하고, 복잡한 조회는 애그리게이트 Repository 대신 전용 jOOQ Projection을 거친다. `MerchantListProjection`/`MerchantApiKeyListProjection`이 이 원칙을 적용한 사례다(`IMPLEMENTATION-NOTES.md` 참고).
- 외부 시스템(블록체인 RPC, 거래소, Webhook 전송)은 전부 outbound Port 뒤에 둔다 — Adapter가 Port를 구현하고, 그 반대는 없다.
- 상태 전이 규칙은 도메인 애그리게이트 자신에게만 있다 — Controller나 Repository에는 없다.

### 애플리케이션 계층 컨벤션(`modules:application`)

첫 Use Case인 `CreatePaymentUseCase`(`application.payment`)로 확립됐다 — 앞으로의 Use Case도 이 모양을 따른다:

- **Outbound Port**는 `application.port.outbound`에 순수 Kotlin 인터페이스로 둔다(Port의 메서드가 제네릭이 아닌 것 하나뿐이면 `fun interface`, 예: `IdGenerator`) — Spring/jOOQ 의존성이 없다는 점에서 한 계층 위의 도메인 순수성 규칙과 같다. 애그리게이트당 Command Repository Port 하나(`save`/`findBy...`, "Command Repository는 Aggregate를 저장하고 복원한다"는 원칙과 일치), 그리고 영속성이 아니지만 Use Case에 필요한 횡단 관심사를 위한 보조 Port(`ExchangeRateProvider`, `IdGenerator`, `TransactionManager`)를 둔다.
- **`TransactionManager`**(`fun <T> runInTransaction(block: () -> T): T`)는 Use Case가 애플리케이션 계층에서 Spring의 `@Transactional`에 의존하거나 어떤 영속성 프레임워크가 뒤에 있는지 몰라도, 문서화된 여러 애그리게이트에 걸친 트랜잭션 경계(`docs/architecture/persistence-jooq.md`의 "트랜잭션 경계" 절)를 만족시키는 방법이다. 나머지 두 개의 문서화된 경계(결제 완료, 환전 완료)를 위한 Use Case를 만들 때도 이 Port를 재사용한다 — Use Case마다 별도의 묶음 Repository Port를 새로 만들지 않는다.
- **Use Case는 `execute(command): result` 메서드 하나만 있는 평범한 클래스다** — 아직 구현이 하나 이상 필요한 경우가 없어서 별도의 inbound Port 인터페이스는 두지 않는다. `Command`/`Result`는 같은 패키지에 `<UseCaseName>Command`/`<UseCaseName>Result`로 이름 붙인 작은 데이터 클래스다. 생성 Command의 `execute`가 식별자(또는 그 밖의 최소한의 데이터)를 반환하는 건 Use Case 레벨에서 허용되는 CQS 예외다 — 위의 CQS 규칙은 도메인 애그리게이트 메서드에 대한 것이지 Use Case 진입점에 대한 것이 아니다.
- **멱등성 체크**(아래 "멱등성 키" 참고)는 Port에 아무것도 쓰기 전에 `execute` 시작 지점에서 한다 — 문서화된 키로 조회해서 이미 있으면 그 결과로 바로 반환한다. 이건 최선을 다하는 빠른 경로일 뿐 최종 보증이 아니다 — 동시 요청 사이의 경합을 막는 최후의 방어선은 여전히 DB 자체의 `UNIQUE` 제약이다.
- `docs/`가 아직 풀지 않은 빈틈(예: 가맹점의 수취 지갑/네트워크가 어디서 오는지)은 지금은 새 Port/테이블을 만들어내지 않고 `Command`의 입력값으로 받는다 — 나중에 쉽게 찾아 바꿀 수 있도록 그 `Command`의 KDoc에 이 단순화를 표시해둔다.

### 영속성 Adapter 컨벤션(`modules:infra-persistence`)

결제 생성 슬라이스의 Port를 구현하면서 확립됐다(`infra.persistence.jooq`, 애그리게이트당 서브패키지 하나, 예: `infra.persistence.jooq.payment`) — 앞으로의 Adapter도 이 모양을 따른다:

- Adapter는 `DSLContext` 하나를 생성자로 주입받는 `@Repository`/`@Component` 클래스다 — 모듈 자체 안에서는 수동 Bean 배선이 필요 없다. `modules:infra-persistence`에 의존하는 앱은 자신의 `@SpringBootApplication` 컴포넌트 스캔이 실제로 `infra.persistence.jooq`까지 닿게만 하면 된다(아래 `apps/*` 절 참고 — `api-payment`는 `scanBasePackages`로 이걸 명시했다).
- **`modules:infra-persistence`는 `kotlin("plugin.spring")`이 적용된 상태다** — 직접 `build.gradle.kts`에 선언하지 않고 `id("practicepay.spring-library")`(build-logic convention plugin, 위 "build-logic" 절 참고)를 통해서다. Spring Boot는 인터페이스를 구현한 Bean이라도 기본적으로 JDK 동적 프록시가 아니라 CGLIB(서브클래싱) 프록시를 쓴다(`spring.aop.proxy-target-class=true`가 기본값). Kotlin 클래스는 기본이 `final`이라 CGLIB이 서브클래싱하지 못하고 `Cannot subclass final class ...`로 죽는다 — `kotlin("plugin.spring")`이 `@Component`(`@Repository` 포함, 메타 애노테이션까지 인식)가 붙은 클래스를 자동으로 `open`으로 만들어준다. 이 모듈 자체의 테스트는 Adapter를 직접 `new`해서 Spring DI/AOP를 전혀 거치지 않아 이 문제를 드러내지 않았다 — `apps:api-payment`가 실제 Spring 컨테이너로 이 Adapter들을 부팅하고 나서야 처음 발견됐다.
- **jOOQ가 생성한 테이블 클래스가 여러 도메인 애그리게이트와 이름이 겹친다**(`Payment`, `Merchant`, `CheckoutSession`, `PaymentQuote`, `OutboxEvent` 모두 `paytech.practice.pay.dbcore.jooq.tables.*` 클래스와 `paytech.practice.pay.domain.*` 클래스 양쪽에 존재한다). 모든 Adapter가 같은 방식으로 푼다: 테이블 클래스 자체가 아니라 그 Companion을 거쳐 싱글턴 상수만 import한다(`import ...tables.Payment.Companion.PAYMENT`) — 클래스 자체를 이름으로 참조하지 않으니 도메인 import와 겹칠 게 없다.
- `DATETIME(6)` UTC 컬럼에 대한 `Instant` ↔ `LocalDateTime` 변환은 `infra.persistence.jooq.InstantMapping.kt`의 공유 `toUtcLocalDateTime()`/`toUtcInstant()` 확장 함수를 거친다 — Adapter마다 `ZoneOffset.UTC` 변환을 직접 만들지 않는다.
- 도메인에 대응 값이 없는 컬럼(`payment.order_currency`, `payment_quote.quote_currency`)은 Adapter 경계에서 `"KRW"` 리터럴로 하드코딩해서 채운다 — 이 코드베이스 전체에서 `Money`가 암묵적으로 항상 KRW를 뜻하는 것과 같은 맥락이다(MVP는 KRW→USDC 한 쌍만 지원).
- **알려진 한계: `Payment`/`CheckoutSession`(`version` 낙관적 잠금 컬럼이 있는 두 애그리게이트)의 `save()`는 지금 진짜 낙관적 잠금 보호를 제공하지 않는다.** 도메인 애그리게이트는 `version` 필드를 갖고 있지 않다(영속성 관심사를 도메인 계층에 새지 않으려고 의도적으로 뺐다) — 그래서 Adapter는 UPDATE 직전에 DB의 현재 `version`을 다시 읽어 `current + 1`을 쓴다 — 이건 정확히 같은 Adapter 호출로의 동시 쓰기만 막을 뿐, "이 애그리게이트가 오래된 version에서 읽혔다"는 상황은 잡지 못한다. 기존 애그리게이트를 다시 저장하는 첫 상태 전이 Use Case가 생기면(Port를 통해 예상 version을 전달하거나, DB 쪽 `SELECT ... FOR UPDATE`를 전면적으로 쓰는 방향으로) 반드시 다시 검토한다 — 지금은 `CreatePaymentUseCase`만 `save()`를 부르고 항상 새 애그리게이트만 저장해서 이 한계가 실질적인 영향은 없다.
- **테스트**: `infra-persistence`는 Mock이 아니라 실제 MySQL 통합 테스트를 쓴다 — 테스트 JVM 전체가 공유하는 Testcontainers MySQL 인스턴스(`PersistenceTestSupport`)를, `org.flywaydb.flyway` Gradle 플러그인이 아니라 `flyway-core` Java API로 직접(`Flyway.configure()...migrate()`) 마이그레이트한다(Gradle 9.5.1에서 깨진 건 그 플러그인이지 — 아래 "Database / jOOQ 코드 생성" 참고 — 순수 Java 라이브러리 자체와는 무관하다). 테스트용 `DSLContext`는 Spring Boot의 `JooqAutoConfiguration`이 실제로 구성하는 방식과 똑같이(`DataSourceConnectionProvider` + `TransactionAwareDataSourceProxy` + `spring-boot-jooq` 모듈의 `org.springframework.boot.jooq.autoconfigure.SpringTransactionProvider` — Spring Boot 4.x가 jOOQ 자동 구성을 `spring-boot-autoconfigure`에서 이 전용 모듈로 옮겼다) 배선해서, `TransactionManagerAdapterTest`가 여러 Repository의 쓰기가 실제로 함께 롤백되는지까지 증명할 수 있다.

### 공용 Port 구현(`modules:infra-support`)

자체 모듈을 둘 만큼 크지 않은 outbound Port 구현을 모은 세 번째 `infra-*` 모듈이다. 원래 이 구현들은 **앱마다 자기 `support` 패키지에** 흩어져 있었고(그중 4개는 9곳에 복제까지 돼 있었다), 그것들을 전부 여기로 모았다.

그 결과 **앱에는 outbound Port 구현체가 하나도 없다** — 앱은 Composition Root(`UseCaseConfiguration`)와 inbound Adapter만 갖는다. `architecture-tests`의 `HexagonalLayerTest`가 이 상태를 규칙으로 강제하므로(`apps must not implement outbound ports themselves`), 앞으로 새 Port 구현을 앱 안에 만들면 빌드가 깨진다 — **`modules:infra-*` 중 한 곳에 만든다.**

| 하위 패키지 | 구현 | 스캔하는 앱 |
|---|---|---|
| `infra.support.id` | `UuidIdGenerator`(`IdGenerator`) | api-payment/api-admin/batch |
| `infra.support.security` | `BCryptPasswordEncoderAdapter`(`PasswordEncoder`), `HmacInvitationTokenHasher`(`InvitationTokenHasher`) | api-admin/api-merchant |
| `infra.support.exchange` | `FakeExchangeRateProvider`(`ExchangeRateProvider`) | api-payment/batch |
| `infra.support.apikey` | `HmacApiKeySecretHasher`(`ApiKeySecretHasher`) | api-payment |
| `infra.support.webhook` | `HttpWebhookSender`(`WebhookSender`) | batch |

- **`modules:common`이 아니라 여기인 이유**: 이 클래스들은 전부 `application.port.outbound`의 Port 구현체(`@Component`)라서 `modules:application`과 Spring에 의존한다 — "의존성 없는 공용 유틸리티"라는 `modules:common`의 역할과 맞지 않는다. 헥사고날 관점에서도 outbound Adapter라 `infra-*` 자리가 맞고, `architecture-tests`의 `HexagonalLayerTest`가 정의한 Outbound Adapter 계층(`paytech.practice.pay.infra..`)에 자동으로 포함되는 실질적 이점도 있다. `modules:common`은 여전히 비어 있다.
- Pepper 설정값(`app.api-key.pepper`/`app.invitation-token.pepper`)을 다루는 규칙은 아래 "설정과 비밀값" 절에 있다 — **특히 두 앱이 같은 Pepper를 써야 하는 제약**은 어기면 원인 추적이 어려운 방식으로 깨지므로 반드시 읽는다.
- **포트별로 하위 패키지를 나누고, 앱은 자기가 쓰는 것만 스캔한다**(`infra.persistence.jooq`/`infra.blockchain`을 통째로 스캔하는 것과 다른 점이다). `HmacInvitationTokenHasher`가 `@Value("\${app.invitation-token.pepper}")`로 **필수** 설정값을 요구하기 때문이다 — 초대 흐름이 없는 `api-payment`/`batch`가 이 Bean까지 스캔하면 그 설정이 없다며 컨텍스트가 뜨지 않는다. 앞으로 설정값을 요구하는 Port 구현을 추가할 때도 같은 이유로 하위 패키지를 나눈다.
- **앱은 이 모듈의 클래스를 타입으로 참조하지 않는다** — 컴포넌트 스캔으로만 배선된다(`HexagonalLayerTest`의 "inbound/outbound Adapter는 서로를 모른다" 규칙이 이걸 강제한다).
- **한 앱에서만 쓰는 구현도 여기 둔다**(`HmacApiKeySecretHasher`는 api-payment만, `HttpWebhookSender`는 batch만 쓴다). 처음에는 "중복이 아니니 그 앱에 두고, 두 번째 앱이 필요로 하면 그때 옮긴다"로 갔다가 바꿨다 — 기준을 **중복 제거**가 아니라 **계층 일관성**으로 잡으면 사용처 수와 무관하게 Port 구현은 `infra-*`의 것이고, 그래야 위의 ArchUnit 규칙으로 잠글 수 있기 때문이다. `HmacApiKeySecretHasher`가 `infra.support.security`의 `HmacInvitationTokenHasher`와 같은 HMAC+Pepper 패턴인데도 다른 모듈에 흩어져 있던 것도 이 변경의 계기였다.

### 온체인 Adapter(`modules:infra-blockchain`) — `Web3jBlockchainClient`

`BlockchainClient`(`application.port.outbound`)의 실제 구현이다. web3j(`org.web3j:core:4.14.0`)로 Base Sepolia RPC를 직접 호출한다 — Base가 OP-Stack L2라 표준 EVM JSON-RPC(`eth_getTransactionReceipt`, `eth_blockNumber`, `eth_chainId`)만으로 충분했다. `@Component`(`Web3jBlockchainClient`) + `@Configuration`(`Web3jConfiguration`, `Web3j` Bean을 `app.blockchain.base-sepolia.rpc-url` 설정값으로 만든다) 두 클래스가 `infra.blockchain.web3j` 패키지에 있다 — `modules:infra-persistence`의 jOOQ Adapter와 같은 배선 방식(이 모듈에 의존하는 앱이 컴포넌트 스캔을 `infra.blockchain`까지 넓히기만 하면 된다). **`apps:batch`가 이 모듈에 의존하는 첫 앱이다** — `app.blockchain.base-sepolia.rpc-url`을 `apps:batch/application.yaml`에 정의했고, `apps:batch`의 `UseCaseConfiguration`이 `BlockchainClient`를 필요로 하는 `ConfirmBlockchainTransactionUseCase`를 조립한다(`IMPLEMENTATION-NOTES.md`의 "apps:batch의 Confirm 폴링 Worker" 참고).

설계 판단(Port 자체의 근거는 `BlockchainClient`의 KDoc 참고, 여기는 Adapter 구현 판단만):

- **"조회 대상은 항상 이미 알고 있는 Transaction Hash다" — 들어오는 전송을 감시(Watch)하지 않는다.** `BlockchainTransaction.create`가 `transactionHash`를 필수로 요구하는 것에서 추론했다(체크아웃에서 고객 지갑이 전송을 브로드캐스트한 뒤 그 Hash가 `CheckoutSession`의 `PAYMENT_SUBMITTED` 단계를 통해 PG에 전달되는 흐름을 전제 — `docs/`에 이 전달 경로 자체는 아직 명시돼 있지 않다).
- **`findTransaction` 한 번에 RPC를 세 번 부른다**(`eth_getTransactionReceipt` → 있으면 `eth_blockNumber`/`eth_chainId`) — `confirmationCount`(현재 블록 높이 - 거래 블록 번호 + 1)를 어댑터가 계산해서 담아주므로 호출부는 최신 블록 번호를 따로 조회할 필요가 없다. 세 호출을 배치로 묶지 않은 건 MVP 단순화다.
- **ERC-20 `Transfer` 이벤트는 web3j의 `EventEncoder.encode(...)`로 topic0을 계산해서 필터링한다** — `0xddf252ad...`를 하드코딩하지 않고 `Event("Transfer", [Address(indexed), Address(indexed), Uint256])` 정의로부터 계산한다. 실제 Base Sepolia RPC에 `eth_getLogs`로 조회해 값이 일치하는 것까지 확인했다(아래 "실제 RPC로 검증" 참고).
- **어댑터는 순수한 온체인 사실만 돌려주고, 기대값과 일치하는지 판단하지 않는다** — `tokenTransfers`가 리스트인 이유(한 Receipt에 여러 `Transfer` 로그가 있을 수 있음)는 `BlockchainClient`의 KDoc 참고.
- **`null`(아직 안 채굴됨)과 `BlockchainClientException`(RPC 호출 자체 실패 — `IOException` 또는 JSON-RPC `hasError()`)을 구분한다.**
- **알려진 한계: MVP는 `BASE_SEPOLIA` 하나만 지원한다.** `Web3jConfiguration`이 `Web3j` Bean 하나만 만들어서, 다른 `BlockchainNetwork`로 호출하면 `Web3jBlockchainClient`가 `IllegalArgumentException`을 던진다. 다중 네트워크가 실제로 필요해지면 `BlockchainNetwork`별 `Web3j` Bean 맵으로 넓힌다.

**실제 RPC로 검증(실 구현 후 반드시 필요했던 단계).** 유닛 테스트(MockK로 `Web3j`의 `Request`/`Response` 체인을 모킹, `Web3jBlockchainClientTest`)만으로는 web3j의 실제 응답 JSON 형태에 대한 가정이 맞는지 확인할 수 없어서, `https://sepolia.base.org`(Base Sepolia 공개 RPC)에 대고 실제 최근 블록의 진짜 Transaction Hash로 `Web3jBlockchainClient`를 직접 실행하는 임시 테스트를 한 번 돌렸다(커밋하지 않고 검증 후 삭제) —

- `eth_chainId`가 `84532`(Base Sepolia의 실제 Chain ID)를 정확히 반환하는 것,
- `EventEncoder.encode`로 계산한 topic0이 실제 `eth_getLogs` 응답의 `Transfer` 로그 topic0(`0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef`)과 정확히 일치하는 것을 확인했고,
- **이 검증에서 실제 버그를 하나 잡았다**: `BigInteger.toLong()`은 값이 `Long` 범위를 넘으면 예외 없이 하위 64비트로 조용히 잘라버린다(음수로 뒤집힐 수도 있다) — 18-decimals ERC-20 토큰(대부분의 토큰, USDC의 6-decimals가 오히려 예외)의 전송량은 흔히 `Long.MAX_VALUE`를 넘어서, 실제 Base Sepolia 트랜잭션을 조회하자마자 `TokenAmount는 음수일 수 없습니다: -6446744073709551616` 같은 값으로 터졌다. `toTokenTransferOrNull`에서 `amount`가 `Long` 범위를 넘으면 그 로그 하나만 건너뛰도록 고쳤다(전체 조회를 실패시키지 않는다 — 같은 Receipt에 우리가 찾는 USDC 전송이 함께 있을 수 있어서). 이 사례를 `Web3jBlockchainClientTest`의 회귀 테스트로 남겨뒀다. **유닛 테스트만으로는 못 잡는, 실제 RPC로 검증해야만 드러나는 종류의 버그였다는 점에서 이 단계를 생략하면 안 된다는 근거로 남긴다**(같은 성격의 사례가 이후 둘 더 나왔다 — 위 "테스트가 잡지 못하는 층" 절 참고).

### Apps(`apps:api-payment`, `apps:api-admin`, `apps:api-merchant`, `apps:batch`)

각각 **독립적으로 배포 가능한 Spring Boot 애플리케이션**이다 — 자체 `build.gradle.kts`(`org.springframework.boot` 플러그인 적용), 자체 `@SpringBootApplication` 메인 클래스, 자체 `application.yaml`, 자체 포트를 가진다 — 하나의 공유 앱 안의 패키지가 아니다. 이건 의도적인 선택이었다(모듈러 모놀리스 대안을 두고 사용자와 확인함) — 정확히는 네 앱이 서로 다른 대상(가맹점 서버를 향한 결제 API, 내부 직원용 관리 콘솔, 가맹점 콘솔, 오프라인 배치 Job)을 상대해서 나중에 독립적으로 스케일·배포·장애가 나야 할 수 있어서다 — 이 선택을 끝까지 따른 결과로, `api-payment`와 역할이 겹치던 원래 Spring-Initializr 루트 앱도 다섯 번째 중복 배포 단위로 남겨두지 않고 삭제했다.

- **의존성은 각 앱이 지금 실제로 하는 일에만 맞춘다 — 나중에 할 일까지 미리 넣지 않는다.** 네 앱 전부 실제 Use Case가 생겼다(`CreatePaymentUseCase`/`AuthenticateInternalUserUseCase`/`AuthenticateMerchantUserUseCase`/`ConfirmBlockchainTransactionUseCase`) — 그래서 넷 다 `modules:application`/`modules:infra-persistence`/`spring-boot-starter-jooq`/`DataSource`가 연결돼 있다. `batch`는 여기에 `modules:infra-blockchain`(`BlockchainClient`)도 더 붙는다 — Confirm Worker가 온체인 조회를 직접 하기 때문이다(`IMPLEMENTATION-NOTES.md`의 "apps:batch의 Confirm 폴링 Worker" 참고). 세 API 앱은 `webmvc`+`security`도 갖고 있다(로그인/향후 인증 엔드포인트용); `batch`는 여전히 웹 앱이 아니다. 실제 Use Case가 필요로 할 때만 그 앱의 의존성을 넓히고, 미리 넓히지 않는다.
- **포트**: `api-payment` 8081, `api-admin` 8082, `api-merchant` 8083; `batch`는 `server.port`가 없다(웹 스타터가 없어서 웹 서버 자동 구성이 스스로 꺼진다 — `DataSource`가 생긴 지금도 웹 앱은 아니다).
- **컴포넌트 스캔**: `@SpringBootApplication`의 기본 스캔 범위는 메인 클래스 자신의 패키지와 그 하위 패키지다. 네 앱의 메인 클래스(`paytech.practice.pay.api.payment`/`api.admin`/`api.merchant`/`batch`)는 전부 `modules:infra-persistence`의 Adapter(`paytech.practice.pay.infra.persistence.jooq`)와 *형제* 관계이지 상위가 아니다 — 그래서 넷 다 `@SpringBootApplication(scanBasePackages = [자기 패키지, "paytech.practice.pay.infra.persistence.jooq", ...])`로 필요한 패키지를 모두 명시한다. `batch`는 `modules:infra-blockchain`의 Adapter 패키지(`paytech.practice.pay.infra.blockchain`)도 추가로 스캔한다. 새 앱이 다른 모듈의 Bean을 쓰기 시작하면, Gradle 의존성을 추가하는 것만으로 Bean이 연결된다고 가정하지 말고 같은 방식으로 스캔 범위를 넓힌다.
- 네 앱의 `application.yaml`은 `spring-boot-docker-compose` 자동 감지에 기대지 않고 `spring.datasource.*`를 `db-core`/`compose.yaml`이 이미 쓰는 것과 같은 로컬 개발 MySQL로 직접 가리킨다(`localhost:3306/stablecoin_payment`, `root`/`verysecret`) — 그 자동 감지 메커니즘은 실행 중인 앱 자신의 작업 디렉토리(예: `apps/api-payment/`)에서 `compose.yaml`을 찾지, `backend/`에서 찾지 않아서, 추가 경로 설정 없이는 공유 파일을 찾지 못한다.
- 테스트는 전부 같은 모양을 따른다(`@SpringBootTest` + Kotest `SpringExtension`, 앱마다 `contextLoads` 테스트 하나 — 위 "테스트" 참고). 네 앱 다 만족시켜야 할 `DataSource`가 있어서 `TestcontainersConfiguration`도 추가로 import한다.

## Hosted Checkout API — 계약은 있고 구현은 없다

고객 브라우저가 호출할 체크아웃 API의 계약이 `docs/architecture/checkout-api.md`에 **구현보다 먼저** 정의돼 있다. 이 저장소에서 계약 우선(contract-first)으로 간 첫 사례다 — 프론트엔드가 백엔드 소스를 읽지 않고도 작업할 수 있게 하려는 것이 목적이다.

- **새 앱 `apps:api-checkout`(포트 8084)이 이걸 구현한다** — 기존 세 앱에 얹지 않는다. "앱 하나 = 상대하는 대상 하나" 기준의 네 번째 대상(고객)이고, 인증 모델이 셋 다와 다르다(자격증명 없음 — `checkoutSessionId`가 곧 자격).
- **`ConnectCheckoutWalletUseCase`/`SubmitPaymentTransactionUseCase`는 이미 있다** — 컨트롤러와 Bean만 없다. 새로 만들어야 하는 건 조회용 Projection Use Case와 취소 Use Case다.
- 계약을 바꿀 때는 `docs/`를 먼저 고친다 — 구현이 아직 없으므로 지금은 그 문서가 유일한 기준이다.

## 기능별 구현 기록은 별도 문서에 있다

Use Case·Adapter를 하나씩 구현하며 내린 판단(왜 그 상수를 골랐는지, 어떤 gap을 남겼는지, 실물 검증에서 무엇이 드러났는지)은 **`backend/IMPLEMENTATION-NOTES.md`**에 기능별 절로 모여 있다. 이 문서는 "앞으로 따를 규칙"만 담는다.

- **새 Use Case를 만들 때**: 이 문서의 컨벤션 절(애플리케이션 계층/영속성 Adapter/공용 Port 구현/Apps)을 따르고, 구현이 끝나면 `IMPLEMENTATION-NOTES.md`에 절을 추가한다.
- **비슷한 상황의 선례를 찾을 때**: `IMPLEMENTATION-NOTES.md`를 본다. 다만 새 작업을 하려고 그 문서를 통째로 읽을 필요는 없다.
- **재사용 가능한 규칙이 나오면 이 문서에 쓴다** — 기능별 기록 쪽에 묻어두지 않는다.

## IntelliJ HTTP Client(`.http` 파일)로 API 수동 테스트하기

각 `apps/*`에 `requests.http`가 있다(`apps/api-payment/requests.http`, `apps/api-admin/requests.http`, `apps/api-merchant/requests.http`) — IntelliJ가 인식하는 형식이다(에디터에서 열면 요청 옆에 ▶ 실행 아이콘이 뜬다). `backend/http-client.env.json`이 세 앱의 `baseUrl`과 로그인 아이디/비밀번호/API Key 같은 공용 변수를 "local" 환경으로 묶어 둔다 — 요청을 실행하기 전에 에디터 오른쪽 위에서 환경을 "local"로 고른다.

- **먼저 해당 앱을 띄운다**: `gradlew.bat :apps:api-payment:bootRun`처럼 `.http` 파일이 있는 앱을 실행해야 요청이 응답을 받는다.
- **자격증명은 `db/seed/seed_dev_identity_data.sql`이 심는다** — 스키마 마이그레이션(`db/migration/`)을 적용한 뒤 `db/seed/`의 두 파일도 같은 방식(`docker exec ... < 파일`)으로 적용해야 `.http` 파일의 요청들이 실제로 인증에 성공한다. 시드는 Flyway 마이그레이션이 아니라서(운영에서 자동 제외되도록 분리했다) 스키마와 함께 자동으로 적용되지 않는다(아래 "Database / jOOQ 코드 생성"의 "시드 데이터" 참고). 로그인 비밀번호는 `dev-admin`/`dev-owner` 둘 다 `DevPassword123!`이고, 결제 API Key는 `sk_test_devkey01_dev-secret-value`(scope: `PAYMENT_CREATE`+`PAYMENT_READ`)다 — 전부 로컬 개발 전용 값이고, 시드 파일 맨 위 주석에도 같은 내용이 있다.
- 각 `.http` 파일은 성공 케이스 하나와 실패(인증 실패/검증 실패) 케이스 몇 개를 같이 담아뒀다 — `> {% client.test(...) %}` 응답 스크립트로 상태 코드를 자체 검증한다. `api-payment`의 결제 생성 요청은 `{{$timestamp}}`로 `merchantOrderId`를 매번 다르게 만들어서 재실행해도 멱등성 키가 겹치지 않게 했고, 멱등성 자체를 확인하는 요청은 고정된 `merchantOrderId`로 두 번 반복해 같은 `paymentId`가 나오는지 보게 했다.
- `api-admin`/`api-merchant` 로그인이 성공하면 세션 쿠키(`JSESSIONID`)가 응답에 실린다 — IntelliJ HTTP Client는 같은 환경 안에서 쿠키를 자동으로 유지하므로, 로그인 다음에 그 세션이 필요한 요청을 이어서 만들면 별도 처리 없이 인증된 채로 나간다.

## 도메인 코드 컨벤션

`docs/domain/domain-model.md`의 애그리게이트가 전부 만들어졌다: `Merchant`, `Payment`, `CheckoutSession`, `BlockchainTransaction`, `ExchangeOrder`, `SettlementReceivable`, `WebhookDelivery`, 그리고 `PaymentQuote`, `OutboxEvent`(`domain.outbox`), Identity/API Key 애그리게이트(`domain.identity`의 `InternalUser`, `MerchantUser`, `AccountInvitation`; `domain.apikey`의 `MerchantApiKey`). 앞으로의 애그리게이트도 같은 모양을 따른다.

`Merchant`와 `OutboxEvent`는 `docs/domain/state-transitions.md`에 없다 — 둘 다 DB 스키마(CHECK 제약과 컬럼 구조)에서 직접 상태 전이를 추론했고, 그 근거를 각 상태 Enum의 KDoc에 남겼다 — `docs/domain/state-transitions.md` 자체에는 추가하지 않았다(그 문서는 검토된 비즈니스 규칙을 담는 곳이지 구현하며 추론한 내용을 담는 곳이 아니다).

- **Value Object**는 Kotlin `@JvmInline value class`로 원시값 하나를 감싸고, `init { require(...) }` 블록에서 검증하며, 어떤 DB 컬럼에 대응하는지와 그 타입이 왜 존재하는지 설명하는 KDoc을 단다(`PaymentId`, `MerchantId`, `Money`, `TokenAmount`, `WalletAddress`, `Asset`, `BlockchainNetwork`, `MerchantOrderId`, `LoginId`, `Email`, `ApiKeyPrefix` 등 참고). 같은 개념이 두 번째로 필요해지면 애그리게이트마다 중복시키지 말고 VO를 재사용한다(예: `WalletAddress`/`BlockchainNetwork`/`HttpUrl`은 `domain.shared`에, `AccountStatus`/`LoginId`/`Email`은 `domain.identity`에 있고 `InternalUser`와 `MerchantUser`가 공유한다).
- **애그리게이트**는 `private` 생성자와 Companion 팩토리 둘을 노출한다: 완전히 새 인스턴스를 위한 `create(...)`(또는 더 구체적인 이름의 생성 팩토리, 예: `Merchant.create`, `MerchantUser.inviteInitialOwner`/`inviteSubAccount`, `InternalUser.bootstrap`/`invite` — 초기 상태를 고정하고 Nullable 필드를 기본값 `null`로 둔다), 저장된 값으로부터 복원하는 `reconstitute(...)`(모든 필드를 명시적으로 받는다). 호출부가 일관되지 않은 상태로 애그리게이트를 조립할 수 있는 공개 생성자는 절대 노출하지 않는다.
- **상태 전이 메서드**는 Command다(CQS): 작은 private `checkTransition(allowed, target)` 헬퍼로 현재 상태를 검증하고, 잘못된 전이면 `IllegalStateException`을 던진다 — 문서화된 곳에서는 `docs/domain/domain-model.md`의 시그니처를 정확히 따른다(문서에 없는 파라미터를 추가하지 않는다, 예: `fail()`은 `reason`과 `failedAt`만 받지 메시지는 받지 않는다). 문서가 애그리게이트의 전이를 전혀 다루지 않는 경우(`Merchant`, 그리고 공유되는 `AccountStatus` 흐름을 넘어서는 "Identity & access" 전반)에는 스키마의 status CHECK 제약과 컬럼 구조에서 전이를 추론하고, 그 근거를 Enum의 KDoc에 남긴다.
- **create()/reconstitute() 패턴의 의도적인 예외 두 가지** — 둘 다 데이터 형태 자체가 "새로 만든 것"과 "복원한 것"의 의미 있는 구분을 지원하지 않기 때문이다:
  - `PaymentQuote`는 공개 생성자를 가진 평범한 `data class`다 — 불변 스냅샷이라서다(`status`도, 전이 메서드도 없다; `payment_quote` 테이블에도 `updated_at`/`version`이 없다).
  - `AccountInvitation`의 전이 메서드(`expire()`, `revoke()`)는 다른 모든 애그리게이트와 달리 타임스탬프 파라미터를 받지 않는다 — `account_invitation` 테이블에 그걸 쓸 `updated_at`/`version` 컬럼이 없어서다.
- **이건 학습용 프로젝트다**: 식별자는 영문을 유지하지만, 도메인 코드의 KDoc과 `require`/`check` 검증 메시지는 **한글**로 쓴다. 무엇을 하는지가 아니라 *왜*(DB 매핑, 비즈니스 규칙, "EIP-55 checksum 검증은 하지 않는다" 같은 범위 제한)를 설명한다.

## 멱등성 키

**엔티티별 키 목록은 `docs/database/database-design.md`의 "주요 Unique"에 있다** — 여기에 복제하지 않는다(예전에 같은 표를 양쪽에 두고 있었고, 그동안 `docs/` 쪽이 계정·API Key 4건을 누락한 채로 어긋나 있었다).

구현할 때 지키는 규칙:

- **그 DB 제약을 쓰고, 별도의 중복 제거 로직을 새로 만들지 않는다.**
- Use Case의 사전 조회(예: `loginId` 중복 확인)는 **최선을 다하는 빠른 경로일 뿐 최종 보증이 아니다** — 동시 요청 사이의 경합을 막는 최후의 방어선은 `UNIQUE` 제약 자체다. 사전 조회를 넣었다고 제약을 빼지 않는다.
- 새 애그리게이트를 만들면 멱등성 키를 함께 정하고 `docs/database/database-design.md`에 추가한다.

## Database / jOOQ 코드 생성

`db-core`가 DB 스키마를 소유하고 실제 MySQL 인스턴스로부터 jOOQ Kotlin 코드를 생성한다. **공식** `org.jooq.jooq-codegen-gradle` 플러그인을 쓰는 실제 Gradle 서브프로젝트다(jOOQ Core는 Spring Boot 4.1.0 BOM을 통해 3.21.5이고, Codegen 플러그인의 최신 배포 버전은 3.20.3이다 — 플러그인과 jOOQ Core 사이에 마이너 버전 하나가 뒤처지는 건 알려진 사실이지 여러분이 만든 불일치가 아니다).

작업 흐름(`backend/`에서):

```
docker compose up -d                                    # MySQL 시작(DB: stablecoin_payment, root 비밀번호: verysecret)

# 1) 스키마 마이그레이션 — Flyway가 적용하고 flyway_schema_history에 기록한다.
#    앱을 띄우면 자동으로 적용되지만(아래 "Flyway" 참고), jooqCodegen은 앱을 빌드하기
#    전에 테이블이 있어야 해서(앱 빌드 → codegen → 테이블 순환) 최초 1회는 앱 없이
#    적용해야 한다 — compose.yaml의 flyway 도구 서비스를 쓴다.
docker compose run --rm flyway migrate

# 2) 로컬 개발용 시드 — 운영에는 절대 적용하지 않는다(db/seed/, 아래 "시드 데이터" 참고).
#    순서가 있다: seed_dev_data.sql이 만드는 가맹점에 seed_dev_identity_data.sql이 계정을 얹는다.
docker exec -i backend-mysql-1 mysql --default-character-set=utf8mb4 -uroot -pverysecret stablecoin_payment < db-core/src/main/resources/db/seed/seed_dev_data.sql
docker exec -i backend-mysql-1 mysql --default-character-set=utf8mb4 -uroot -pverysecret stablecoin_payment < db-core/src/main/resources/db/seed/seed_dev_identity_data.sql

gradlew.bat :db-core:jooqCodegen                          # db-core/build/generated-src/jooq/main에 생성(gitignore 대상, 커밋하지 않음)
gradlew.bat :db-core:build                                 # jooqCodegen이 먼저 실행되고(compileKotlin.dependsOn으로 연결), 그다음 컴파일된다
```

### Flyway — 앱이 부팅 시 스키마를 적용한다

네 앱 모두 `spring-boot-starter-flyway`(+ `flyway-mysql`)를 갖고 있어서(`practicepay.spring-boot-app` convention plugin), **부팅 시 `classpath:db/migration`의 마이그레이션을 자동으로 적용한다.** 이미 최신이면 아무것도 하지 않는다.

- **기본 위치 `classpath:db/migration`을 바꾸지 않는다** — 개발용 시드(`db/seed/`)가 운영에서 자동 제외되는 근거가 이 기본값이다(아래 "시드 데이터" 참고). 마이그레이션 파일은 `db-core`의 리소스인데, 앱이 `modules:infra-persistence` → `db-core`로 이어지는 의존성을 통해 classpath에 갖고 있어서 그대로 발견된다(실제 `bootRun`으로 확인).
- **`flyway-mysql`은 starter에 없어서 따로 추가했다** — Flyway 10부터 DB별 지원이 별도 모듈로 분리됐다.
- **네 앱이 같은 스키마를 공유하므로 넷 다 마이그레이션을 시도한다.** Flyway가 실행 중 DB 잠금을 잡아서 동시에 떠도 안전하지만, 운영에서는 배포 파이프라인의 별도 단계(또는 한 앱만)로 적용하는 쪽이 낫다 — 스키마 소유권이 네 배포 단위에 흩어져 있는 건 MVP 단계의 단순화다.
- **jooqCodegen과의 순서 문제**: codegen은 실제 테이블을 읽어야 하고 앱 빌드는 codegen 결과에 의존하므로, "앱을 띄워 마이그레이션한다"로는 최초 부트스트랩이 순환에 빠진다. 그래서 위 작업 흐름은 앱 없이 도는 **`compose.yaml`의 `flyway` 도구 서비스**(`docker compose run --rm flyway migrate`)로 최초 적용을 한다. Flyway가 이력을 남기므로 이후 앱 부팅 시에는 "No migration necessary"가 되고 두 경로가 충돌하지 않는다(실제로 확인).
  - 이 서비스에는 `profiles: ['tools']`가 붙어 있어 `docker compose up -d`로는 뜨지 않는다(1회성 도구다). `migrate` 말고 `info`/`baseline`/`repair` 같은 다른 Flyway 명령도 같은 방식으로 쓴다 — 접속 정보는 `FLYWAY_*` 환경변수로 이미 들어가 있다.
  - `docker run`을 직접 쓰지 않는 이유: 볼륨 마운트에 절대 경로가 필요해서 셸/OS마다 문법이 달라진다(Git Bash에서는 `MSYS_NO_PATHCONV=1`과 `$(pwd -W)`가 필요했다). compose가 상대 경로와 네트워크를 대신 처리해준다.
- **`org.flywaydb.flyway` Gradle 플러그인은 여전히 쓰지 않는다.** 최신 배포(11.8.2)가 Gradle 9에서 제거된 `JavaPluginConvention`을 호출해서 이 프로젝트의 Gradle 9.5.1에서 태스크가 실패한다(업스트림 미해결: https://github.com/flyway/flyway/issues/3798). Docker 이미지를 쓰는 이유이기도 하다 — 나중에 플러그인이 고쳐졌는지 확인해볼 수 있다.
- **`mysql` CLI로 스키마를 직접 적용하지 않는다**(시드는 예외다 — Flyway 관리 대상이 아니다). CLI로 적용하면 `flyway_schema_history`에 기록이 남지 않아, 앱을 띄울 때 Flyway가 `Found non-empty schema(s) ... but no schema history table`로 **기동을 거부한다**(아무것도 변경하지 않고 중단한다).
  - Flyway 도입 전에 CLI로 적용해 둔 기존 DB가 있다면, 데이터를 지우지 않고 되살리는 방법은 **baseline**이다 — 이미 적용된 최고 버전을 이력에 기록하고 그 이후만 적용하게 만든다:

    ```
    docker compose run --rm flyway -baselineVersion=5 baseline
    ```

    되돌리려면 `flyway_schema_history` 테이블을 지우면 된다. DB를 새로 만들어도 되지만(`docker compose down -v`) 시드를 다시 심어야 한다.
- **버전 번호가 V1/V3/V5로 비어 있는 건 정상이다.** 원래 V2/V4였던 개발용 시드를 `db/seed/`로 옮기면서 생긴 자리다(아래 "시드 데이터" 참고). 이미 적용된 이력을 깨뜨리지 않으려고 남은 파일의 번호는 그대로 뒀다 — Flyway는 버전이 연속이 아니어도 정상 동작한다.

### 시드 데이터(`db/seed/`) — 운영에 적용하지 않는다

`seed_dev_data.sql`(가맹점 `mrc_test_001`)과 `seed_dev_identity_data.sql`(로그인 계정·API Key)은 **Flyway 마이그레이션이 아니다.** 위 작업 흐름처럼 `mysql` CLI로 직접 적용한다(순서 있음 — 두 번째 파일이 첫 번째가 만든 가맹점을 참조한다).

- **`db/migration/`이 아니라 별도 폴더에 둔 이유**: `spring-boot-starter-flyway`의 기본 위치가 `classpath:db/migration`이라, 시드가 그 밖에 있으면 **운영에서 설정을 아무것도 하지 않아도 자동으로 제외된다.** 운영 설정에서 시드 위치를 빼는 걸 "잊지 않아야" 안전한 구조가 아니라, **잊어도 안전한** 구조를 택한 것이다 — 개발 계정이 운영에 실리는 사고는 되돌리기 어렵다.
- 시드는 스키마도 jOOQ 코드 생성도 건드리지 않으므로 "Migration → MySQL Schema → jOOQ Code Generation" 파이프라인에 있을 이유가 애초에 없다.
- **`modules:infra-persistence`의 통합 테스트는 영향받지 않는다** — `PersistenceTestSupport`가 원래부터 `db/migration`만 가리켜서 시드가 자동으로 빠지고, 테스트는 시드 행에 의존하지 않는다(각자 필요한 데이터를 직접 넣는다).
- **이미 V2/V4를 적용해 둔 로컬 DB가 있다면** `flyway_schema_history`에 그 기록이 남아 있어서, 나중에 Spring Boot Flyway를 붙였을 때 "적용됐는데 파일이 없다"는 validate 오류가 난다 — 로컬 DB를 재생성하면 해결된다(`docker compose down -v` 후 위 작업 흐름을 다시 실행).
- **`mysql` CLI로 마이그레이션을 적용할 때는 항상 `--default-character-set=utf8mb4`를 넘긴다.** 넘기지 않으면 CLI의 기본 `latin1` 클라이언트 문자셋이 MySQL로 들어가는 한글 `COMMENT`/시드 텍스트를 조용히 깨뜨린다(손상은 jOOQ가 읽을 때가 아니라 쓸 때 일어난다 — 이미 한 번 겪었고, DB를 지우고 다시 시딩해야 했다).
- `jooq { configuration { jdbc { url = ... } } }`의 URL에도 Codegen 커넥션 자체를 위한 값싼 추가 보험으로 `useUnicode=true&characterEncoding=UTF-8`을 붙여둔다.
- `compileKotlin`은 자동으로 `jooqCodegen`에 의존하지 않고, 공식 플러그인도 자신의 출력 디렉토리를 Kotlin 소스셋에 자동으로 추가하지 않는다 — 둘 다 `db-core/build.gradle.kts`에서 명시적으로 연결했다(`tasks.named("compileKotlin") { dependsOn("jooqCodegen") }` + `sourceSets { main { kotlin { srcDir(...) } } }`). 새로 공식 플러그인을 설정하면 이게 자동으로 연결된다고 가정하지 않는다.
- 생성된 코드는 `paytech.practice.pay.dbcore.jooq` 패키지 아래에 있고, 아래 "Persistence 규칙" 절에 따라 향후 영속성 Adapter(`modules/infra-persistence`) 안에서만 써야 한다 — `domain`/`application`에서는 절대 쓰지 않는다.
- `jooqCodegen`은 `internal_user`와 `merchant_user`에 대해 (무해한) `Ambiguous key name` 경고를 출력한다 — 두 테이블 모두 자기 자신/서로에게 FK가 여러 개 걸려 있어서(`created_by_internal_user_seq`, `invited_by_internal_user_seq`, `invited_by_merchant_user_seq`), jOOQ가 모든 암묵적 Join 편의 접근자에 유일한 이름을 자동으로 붙이지 못한다. 빌드는 여전히 성공하고 생성된 `Table`/`Record` 클래스에도 영향이 없다 — 몇몇 선택적인 경로 탐색 편의 메서드만 생략될 뿐이다.

## Persistence 규칙 — 원본은 `docs/`에 있다

**기술 기준·Command/Query 분리·낙관적 잠금·타입 매핑·트랜잭션 경계의 원본은 `docs/architecture/persistence-jooq.md`다** — 여기에 옮겨 적지 않는다(예전에 그 문서를 항목별로 재진술한 절이 있었는데, 두 곳이 어긋날 위험만 만들어서 걷어냈다). 영속성 작업 전에 그 문서를 먼저 읽고, 실제 Adapter 작성 규칙은 위 "영속성 Adapter 컨벤션" 절을 따른다.

`docs/`가 명시하지 않아 여기서 정한 것만 남긴다:

- **Record ↔ 도메인 객체는 명시적인 Mapper로 변환한다** — 암묵적/리플렉션 매핑을 쓰지 않는다. `docs/`는 "Record를 Adapter 밖으로 내보내지 않는다"까지만 정한다.
- **MySQL `ENUM`을 절대 쓰지 않는다**(상태는 Kotlin Enum이 뒷받침하는 `VARCHAR`), **금액·환율에 `FLOAT`/`DOUBLE`을 절대 쓰지 않는다.** `docs/`의 타입 매핑 표는 쓸 타입만 적고 금지 타입은 적지 않아서, 그 금지를 여기에 남긴다.
- **비동기 부수효과(Webhook, 이벤트)는 트랜잭션 안에서 직접 발행하지 않고 Transactional Outbox(`outbox_event`)를 거친다.**
