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
                     AcceptAccountInvitationUseCase(POST /admin/account-invitations/accept, 비인증)가 있다
                     (Apps 절 참고).
  api-merchant/      실제 Gradle 서브프로젝트, 독립 배포 가능한 Spring Boot 앱 — webmvc + jooq + security,
                     modules:application + modules:infra-persistence에 의존한다. AuthenticateMerchantUserUseCase
                     (POST /merchant/login)와 AcceptAccountInvitationUseCase(POST /merchant/account-invitations/accept,
                     비인증, api-admin과 같은 공용 Use Case를 재사용)가 있다(Apps 절 참고). 가맹점 등록, 하위
                     계정 발급, API Key 등 나머지 흐름은 아직 Use Case가 없다.
  batch/             실제 Gradle 서브프로젝트, 독립 배포 가능한 Spring Boot 앱 — spring-boot-starter-batch +
                     jooq + modules:application/infra-persistence/infra-blockchain에 의존한다. Job 셋:
                     confirmBlockchainTransactionJob(BlockchainTransaction 감지·Confirm 폴링 Worker),
                     publishOutboxEventJob(OutboxEvent 발행 Worker, Webhook HTTP 호출 포함),
                     sellToFakeExchangeJob(Fake Exchange 매도 폴링 Worker) 셋 다 10초 주기다(Apps 절의
                     "apps:batch의 Confirm 폴링 Worker"/"apps:batch의 OutboxEvent 발행 Worker"/"apps:batch의
                     Fake Exchange 매도 폴링 Worker" 참고). 웹 스타터는 여전히 없다.
modules/
  application/       실제 Gradle 서브프로젝트, domain에 의존; ConnectCheckoutWalletUseCase(application.checkout,
                     지갑 연결 슬라이스), CreatePaymentUseCase(결제 생성 슬라이스),
                     SubmitPaymentTransactionUseCase(BlockchainTransaction 생성 슬라이스),
                     ConfirmBlockchainTransactionUseCase(감지·Confirm 슬라이스) + PaymentTransactionValidator
                     + PaymentNetworkConfig(공유 MVP 상수), PublishOutboxEventUseCase(application.outbox,
                     OutboxEvent 발행 슬라이스), SellToFakeExchangeUseCase(application.exchange, Fake Exchange
                     매도 슬라이스 — MVP 완료 경계의 마지막 조각), Identity/API Key Use Case
                     (Authenticate*/IssueInternalUser), BlockchainClient(온체인 조회 Port, 구현체는
                     modules:infra-blockchain) + 그 outbound port들(Architecture 참고)
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
- 로컬 MySQL: `compose.yaml`이 `docker compose up`용 `mysql:latest` 서비스를 정의하고, `stablecoin_payment` DB로 시딩된다(스키마와 일치 — 아래 "Database / jOOQ code generation" 참고). `apps:*` 네 앱 전부 테스트에서는 대신 Testcontainers를 자동으로 쓴다(각자 `TestcontainersConfiguration.kt`가 `@ServiceConnection`으로 MySQL 컨테이너를 띄운다) — `apps:batch`도 Confirm Worker가 생기면서 `DataSource`가 필요해져 같은 패턴을 따라간다.
- 툴체인: Java 25, Kotlin 2.3.21, Spring Boot 4.1.0(각 `apps:*`/`db-core`/`modules:infra-persistence` 서브프로젝트 기준 — 루트 프로젝트 자체는 더 이상 Kotlin이나 Spring Boot 플러그인을 적용하지 않는다). 버전은 `backend/gradle/libs.versions.toml`에 모여 있다(위 "build-logic" 절 참고).
- Lint/포맷: **ktlint**를 `org.jlleitschuh.gradle.ktlint` 플러그인(14.2.0)으로 모든 모듈에 적용한다(계층형 `modules:domain`/`modules:application` include를 위해 Gradle이 만드는 Phantom 부모 `:modules`도 포함) — 루트 `build.gradle.kts`의 `allprojects {}`를 통해서다. ktlint는 `build-logic`의 convention plugin으로 옮기지 않고 여기 그대로 뒀다 — `:modules` phantom project는 자기 `build.gradle.kts`가 없어서 convention plugin을 적용할 수 없고, 이미 잘 동작하고 문서화돼 있는 방식을 바꿀 이유가 없었다. `backend/.editorconfig`가 `indent_style = tab`을 고정해서(이 프로젝트의 기존 컨벤션) ktlint가 스페이스로 강제 포맷하지 않게 한다. `ktlintCheck`는 이미 `check`/`build`의 일부로 실행되므로, 빌드가 성공하면 Lint도 깨끗하다는 뜻이다. `db-core/build.gradle.kts`는 `generated-src`(jOOQ가 생성한 코드, 직접 수정하지 않음)를 Lint 대상에서 제외하고, 그걸 읽는 ktlint 태스크에 명시적으로 `dependsOn("jooqCodegen")`을 추가한다 — Gradle의 태스크 입력 검증이, 어떤 디렉토리를 읽는 태스크라면 그 디렉토리를 만드는 태스크에 대한 의존성 선언을 요구하기 때문이다.

## 설정과 비밀값(`application.yaml`)

각 앱의 `application.yaml`에 있는 값은 **전부 로컬 개발용 기본값**이고, 운영에서는 환경변수로 덮어쓴다. 소스에 실제 운영 값(DB 비밀번호, Pepper, 유료 RPC URL)을 적지 않는다.

| 설정 | 환경변수 | 앱 |
|---|---|---|
| `spring.datasource.url`/`username`/`password` | `SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD` | 4개 앱 전부 |
| `app.api-key.pepper` | `APP_API_KEY_PEPPER` | api-payment |
| `app.invitation-token.pepper` | `APP_INVITATION_TOKEN_PEPPER` | api-admin/api-merchant |
| `app.blockchain.base-sepolia.rpc-url` | `APP_BLOCKCHAIN_BASE_SEPOLIA_RPC_URL` | batch |

- **`${ENV:기본값}` 문법이 환경변수 덮어쓰기를 가능하게 하는 게 아니다.** Spring Boot는 환경변수를 `application.yaml`보다 우선하는 property source로 이미 읽고, `APP_INVITATION_TOKEN_PEPPER` 같은 이름을 `app.invitation-token.pepper`로 자동 매핑한다(relaxed binding) — `${...}` 없이 리터럴만 적어둬도 환경변수가 값을 덮어쓰는 것을 실제로 확인했다. 그럼에도 `${...}`로 적는 건 **환경변수 이름을 설정 파일만 보고 알 수 있게** 하고 "이 값은 주입받는 것"임을 드러내기 위해서다.
- **`app.invitation-token.pepper`는 `api-admin`과 `api-merchant`가 반드시 같은 값이어야 한다.** 초대는 발급 시점에 `hash(원문 Token)`을 `account_invitation.token_hash`에 저장하고 수락 시점에 다시 `hash(원문 Token)`으로 조회하는데(`AcceptAccountInvitationUseCase`), 발급 앱과 수락 앱이 다르기 때문이다(가맹점 사용자 초대는 `api-merchant`가 수락하지만 발급은 다른 앱이 한다 — 가맹점 등록 Use Case가 생기면 실제로 갈린다). Pepper가 어긋나면 초대를 영영 찾지 못하고, 그때 나오는 예외는 원인을 숨기도록 설계된 `InvalidInvitationException`("유효하지 않은 초대")이라 추적이 매우 어렵다 — **한쪽만 교체하지 않는다.**
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

계획된 모듈 계층(`docs/architecture/persistence-jooq.md` 참고): `domain` → `application` → `adapter/outbound/persistence/jooq` → `generated-src/jooq`.

- 도메인 코드는 Spring, jOOQ, HTTP 클라이언트, 어떤 블록체인 SDK에도 의존하지 않는다 — 순수 Kotlin 외에는 아무것도 의존하지 않는다.
- 애그리게이트는 다른 애그리게이트를 항상 ID로만 참조하고, 객체 참조로는 참조하지 않는다.
- **CQS(Command Query Separation)**를 메서드 단위로 지킨다: 메서드는 상태를 바꾸고 아무것도 반환하지 않거나(Command — 예: `Payment.ready()`, `submit()`, `succeed()`), 부수효과 없이 데이터를 반환하거나(Query — 예: `payment.status` 조회) 둘 중 하나이지, 둘 다는 아니다. 상태를 바꾸면서 계산된 결과까지 돌려주는 메서드는 추가하지 않는다.
- **영속성 레벨의 CQRS**: Command Repository는 애그리게이트 전체를 저장·복원하고, 복잡한 조회는 애그리게이트 Repository 대신 전용 jOOQ Projection을 거친다 — Persistence conventions 참고.
- 외부 시스템(블록체인 RPC, 거래소, Webhook 전송)은 전부 outbound Port 뒤에 둔다 — Adapter가 Port를 구현하고, 그 반대는 없다.
- 상태 전이 규칙은 도메인 애그리게이트 자신에게만 있다 — Controller나 Repository에는 없다.

### 애플리케이션 계층 컨벤션(`modules:application`)

첫 Use Case인 `CreatePaymentUseCase`(`application.payment`)로 확립됐다 — 앞으로의 Use Case도 이 모양을 따른다:

- **Outbound Port**는 `application.port.outbound`에 순수 Kotlin 인터페이스로 둔다(Port의 메서드가 제네릭이 아닌 것 하나뿐이면 `fun interface`, 예: `IdGenerator`) — Spring/jOOQ 의존성이 없다는 점에서 한 계층 위의 도메인 순수성 규칙과 같다. 애그리게이트당 Command Repository Port 하나(`save`/`findBy...`, "Command Repository는 Aggregate를 저장하고 복원한다"는 원칙과 일치), 그리고 영속성이 아니지만 Use Case에 필요한 횡단 관심사를 위한 보조 Port(`ExchangeRateProvider`, `IdGenerator`, `TransactionManager`)를 둔다.
- **`TransactionManager`**(`fun <T> runInTransaction(block: () -> T): T`)는 Use Case가 애플리케이션 계층에서 Spring의 `@Transactional`에 의존하거나 어떤 영속성 프레임워크가 뒤에 있는지 몰라도, 문서화된 여러 애그리게이트에 걸친 트랜잭션 경계(`docs/architecture/persistence-jooq.md`의 "트랜잭션 경계" 절)를 만족시키는 방법이다. 나머지 두 개의 문서화된 경계(결제 완료, 환전 완료)를 위한 Use Case를 만들 때도 이 Port를 재사용한다 — Use Case마다 별도의 묶음 Repository Port를 새로 만들지 않는다.
- **Use Case는 `execute(command): result` 메서드 하나만 있는 평범한 클래스다** — 아직 구현이 하나 이상 필요한 경우가 없어서 별도의 inbound Port 인터페이스는 두지 않는다. `Command`/`Result`는 같은 패키지에 `<UseCaseName>Command`/`<UseCaseName>Result`로 이름 붙인 작은 데이터 클래스다. 생성 Command의 `execute`가 식별자(또는 그 밖의 최소한의 데이터)를 반환하는 건 Use Case 레벨에서 허용되는 CQS 예외다 — 위의 CQS 규칙은 도메인 애그리게이트 메서드에 대한 것이지 Use Case 진입점에 대한 것이 아니다.
- **멱등성 체크**(아래 "Idempotency keys" 참고)는 Port에 아무것도 쓰기 전에 `execute` 시작 지점에서 한다 — 문서화된 키로 조회해서 이미 있으면 그 결과로 바로 반환한다. 이건 최선을 다하는 빠른 경로일 뿐 최종 보증이 아니다 — 동시 요청 사이의 경합을 막는 최후의 방어선은 여전히 DB 자체의 `UNIQUE` 제약이다.
- `docs/`가 아직 풀지 않은 빈틈(예: 가맹점의 수취 지갑/네트워크가 어디서 오는지)은 지금은 새 Port/테이블을 만들어내지 않고 `Command`의 입력값으로 받는다 — 나중에 쉽게 찾아 바꿀 수 있도록 그 `Command`의 KDoc에 이 단순화를 표시해둔다.

### 영속성 Adapter 컨벤션(`modules:infra-persistence`)

결제 생성 슬라이스의 Port를 구현하면서 확립됐다(`infra.persistence.jooq`, 애그리게이트당 서브패키지 하나, 예: `infra.persistence.jooq.payment`) — 앞으로의 Adapter도 이 모양을 따른다:

- Adapter는 `DSLContext` 하나를 생성자로 주입받는 `@Repository`/`@Component` 클래스다 — 모듈 자체 안에서는 수동 Bean 배선이 필요 없다. `modules:infra-persistence`에 의존하는 앱은 자신의 `@SpringBootApplication` 컴포넌트 스캔이 실제로 `infra.persistence.jooq`까지 닿게만 하면 된다(아래 `apps/*` 절 참고 — `api-payment`는 `scanBasePackages`로 이걸 명시했다).
- **`modules:infra-persistence`는 `kotlin("plugin.spring")`이 적용된 상태다** — 직접 `build.gradle.kts`에 선언하지 않고 `id("practicepay.spring-library")`(build-logic convention plugin, 위 "build-logic" 절 참고)를 통해서다. Spring Boot는 인터페이스를 구현한 Bean이라도 기본적으로 JDK 동적 프록시가 아니라 CGLIB(서브클래싱) 프록시를 쓴다(`spring.aop.proxy-target-class=true`가 기본값). Kotlin 클래스는 기본이 `final`이라 CGLIB이 서브클래싱하지 못하고 `Cannot subclass final class ...`로 죽는다 — `kotlin("plugin.spring")`이 `@Component`(`@Repository` 포함, 메타 애노테이션까지 인식)가 붙은 클래스를 자동으로 `open`으로 만들어준다. 이 모듈 자체의 테스트는 Adapter를 직접 `new`해서 Spring DI/AOP를 전혀 거치지 않아 이 문제를 드러내지 않았다 — `apps:api-payment`가 실제 Spring 컨테이너로 이 Adapter들을 부팅하고 나서야 처음 발견됐다.
- **jOOQ가 생성한 테이블 클래스가 여러 도메인 애그리게이트와 이름이 겹친다**(`Payment`, `Merchant`, `CheckoutSession`, `PaymentQuote`, `OutboxEvent` 모두 `paytech.practice.pay.dbcore.jooq.tables.*` 클래스와 `paytech.practice.pay.domain.*` 클래스 양쪽에 존재한다). 모든 Adapter가 같은 방식으로 푼다: 테이블 클래스 자체가 아니라 그 Companion을 거쳐 싱글턴 상수만 import한다(`import ...tables.Payment.Companion.PAYMENT`) — 클래스 자체를 이름으로 참조하지 않으니 도메인 import와 겹칠 게 없다.
- `DATETIME(6)` UTC 컬럼에 대한 `Instant` ↔ `LocalDateTime` 변환은 `infra.persistence.jooq.InstantMapping.kt`의 공유 `toUtcLocalDateTime()`/`toUtcInstant()` 확장 함수를 거친다 — Adapter마다 `ZoneOffset.UTC` 변환을 직접 만들지 않는다.
- 도메인에 대응 값이 없는 컬럼(`payment.order_currency`, `payment_quote.quote_currency`)은 Adapter 경계에서 `"KRW"` 리터럴로 하드코딩해서 채운다 — 이 코드베이스 전체에서 `Money`가 암묵적으로 항상 KRW를 뜻하는 것과 같은 맥락이다(MVP는 KRW→USDC 한 쌍만 지원).
- **알려진 한계: `Payment`/`CheckoutSession`(`version` 낙관적 잠금 컬럼이 있는 두 애그리게이트)의 `save()`는 지금 진짜 낙관적 잠금 보호를 제공하지 않는다.** 도메인 애그리게이트는 `version` 필드를 갖고 있지 않다(영속성 관심사를 도메인 계층에 새지 않으려고 의도적으로 뺐다) — 그래서 Adapter는 UPDATE 직전에 DB의 현재 `version`을 다시 읽어 `current + 1`을 쓴다 — 이건 정확히 같은 Adapter 호출로의 동시 쓰기만 막을 뿐, "이 애그리게이트가 오래된 version에서 읽혔다"는 상황은 잡지 못한다. 기존 애그리게이트를 다시 저장하는 첫 상태 전이 Use Case가 생기면(Port를 통해 예상 version을 전달하거나, DB 쪽 `SELECT ... FOR UPDATE`를 전면적으로 쓰는 방향으로) 반드시 다시 검토한다 — 지금은 `CreatePaymentUseCase`만 `save()`를 부르고 항상 새 애그리게이트만 저장해서 이 한계가 실질적인 영향은 없다.
- **테스트**: `infra-persistence`는 Mock이 아니라 실제 MySQL 통합 테스트를 쓴다 — 테스트 JVM 전체가 공유하는 Testcontainers MySQL 인스턴스(`PersistenceTestSupport`)를, `org.flywaydb.flyway` Gradle 플러그인이 아니라 `flyway-core` Java API로 직접(`Flyway.configure()...migrate()`) 마이그레이트한다(Gradle 9.5.1에서 깨진 건 그 플러그인이지 — 아래 "Database / jOOQ code generation" 참고 — 순수 Java 라이브러리 자체와는 무관하다). 테스트용 `DSLContext`는 Spring Boot의 `JooqAutoConfiguration`이 실제로 구성하는 방식과 똑같이(`DataSourceConnectionProvider` + `TransactionAwareDataSourceProxy` + `spring-boot-jooq` 모듈의 `org.springframework.boot.jooq.autoconfigure.SpringTransactionProvider` — Spring Boot 4.x가 jOOQ 자동 구성을 `spring-boot-autoconfigure`에서 이 전용 모듈로 옮겼다) 배선해서, `TransactionManagerAdapterTest`가 여러 Repository의 쓰기가 실제로 함께 롤백되는지까지 증명할 수 있다.

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

`BlockchainClient`(`application.port.outbound`)의 실제 구현이다. web3j(`org.web3j:core:4.14.0`)로 Base Sepolia RPC를 직접 호출한다 — Base가 OP-Stack L2라 표준 EVM JSON-RPC(`eth_getTransactionReceipt`, `eth_blockNumber`, `eth_chainId`)만으로 충분했다. `@Component`(`Web3jBlockchainClient`) + `@Configuration`(`Web3jConfiguration`, `Web3j` Bean을 `app.blockchain.base-sepolia.rpc-url` 설정값으로 만든다) 두 클래스가 `infra.blockchain.web3j` 패키지에 있다 — `modules:infra-persistence`의 jOOQ Adapter와 같은 배선 방식(이 모듈에 의존하는 앱이 컴포넌트 스캔을 `infra.blockchain`까지 넓히기만 하면 된다). **`apps:batch`가 이 모듈에 의존하는 첫 앱이다** — `app.blockchain.base-sepolia.rpc-url`을 `apps:batch/application.yaml`에 정의했고, `apps:batch`의 `UseCaseConfiguration`이 `BlockchainClient`를 필요로 하는 `ConfirmBlockchainTransactionUseCase`를 조립한다(아래 "apps:batch의 Confirm 폴링 Worker" 참고).

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

### "체크아웃 지갑 연결" Use Case(`ConnectCheckoutWalletUseCase`, `application.checkout`)

고객이 체크아웃 페이지에서 외부 EVM 지갑을 연결하는 시점을 구현한다. `SubmitPaymentTransactionUseCase`가 "이미 `WALLET_CONNECTED`인 CheckoutSession"을 전제하고 시작했던 지점을 이 Use Case가 그보다 앞서 채운다 — 지금까지 만든 결제 흐름 Use Case 중 시간순으로 가장 이르다.

- **처음으로 `application.payment`가 아니라 `application.checkout` 패키지를 새로 만들었다.** `CheckoutSession`만 다루고 `Payment`/`BlockchainTransaction`은 건드리지 않아서, `Identity` Use Case들이 `application.identity`에 따로 있는 것과 같은 이유로 아그리게이트별 패키지로 분리했다 — 앞선 세 Use Case가 전부 `application.payment`에 있었던 건 전부 `Payment`가 걸린 다중 Aggregate 트랜잭션이었기 때문이지, "결제 관련은 다 `payment` 패키지"라는 규칙이 아니다.
- **단일 Aggregate Use Case라 `TransactionManager`가 필요 없다** — `CheckoutSessionRepository.save` 한 번으로 끝난다. `Repository`/`Command`/`Result`/`Use Case` 넷만 있으면 되는, 지금까지 중 가장 단순한 슬라이스다.
- **`CREATED` 상태였으면 `open()`을 먼저 호출한 뒤 `connectWallet()`으로 넘어간다.** `CheckoutSession.open()`을 부르는 별도의 "체크아웃 페이지 조회" Use Case/API는 만들지 않았다 — 페이지 조회는 상태를 바꾸지 않는 `GET`으로 남겨두는 게 REST 관례에 맞고, 고객이 실제로 처음 행동을 취하는 순간(지갑 연결)을 `open()`이 뜻하는 "체크아웃 페이지를 열었다"로 간주하는 쪽을 택했다 — `docs/`에 이 판단의 근거는 없다(추론한 설계 판단).
- **`CheckoutSessionNotFoundException`을 `application.payment`에서 `application.checkout`으로 옮겼다.** 원래 `SubmitPaymentTransactionUseCase`를 만들 때 그 패키지에 넣었는데, 이 Use Case도 똑같이 필요해지면서 위치가 어색해졌다 — 두 Use Case 다 이 패키지를 import해서 쓴다.
- **지갑 재연결(다른 지갑으로 바꾸기)은 도메인에 없다** — `WALLET_CONNECTED` 이후 다시 호출하면 `CheckoutSession.connectWallet()`의 `checkTransition`이 그대로 `IllegalStateException`을 던진다. 새 도메인 메서드가 필요한 범위 밖 기능이라 손대지 않았다.
- **테스트**: `ConnectCheckoutWalletUseCaseTest`(단위, CREATED에서 한 번에 연결/이미 OPEN인 경우/존재하지 않는 세션/이미 WALLET_CONNECTED인 경우/CANCELLED인 경우).

### "BlockchainTransaction 생성" Use Case(`SubmitPaymentTransactionUseCase`)

고객 지갑이 USDC 전송을 브로드캐스트한 뒤, 체크아웃 프런트엔드가 그 Transaction Hash를 PG에 제출하는 시점을 구현한다. `ConfirmBlockchainTransactionUseCase`가 "이미 있는 BlockchainTransaction을 다시 확인하는 폴링"이라면, 이 Use Case는 그 BlockchainTransaction을 최초로 만드는 자리다 — `ConfirmBlockchainTransactionUseCase`의 KDoc이 범위 밖으로 남겨뒀던 지점을 채운다. 같은 자리(`application.payment`)에 있다.

- **네 번째 트랜잭션 경계를 새로 정의했다**: `BlockchainTransaction(SUBMITTED) + CheckoutSession(PAYMENT_SUBMITTED) + Payment(PROCESSING)`. `docs/architecture/persistence-jooq.md`가 명시한 세 경계(결제 생성/결제 완료/환전 완료) 중 어디에도 해당하지 않는다 — 이 Use Case가 세 Aggregate가 "고객이 결제를 제출했다"는 하나의 사실을 함께 반영해야 한다고 판단해서 원자적으로 묶었다. `OutboxEvent`는 포함하지 않는다 — 문서가 Outbox를 명시한 경계는 "결제 생성"과 "결제 완료" 둘뿐이라, 여기서 Webhook을 새로 만들어내지 않는다(알려진 gap).
- **고객 지갑 연결(`CheckoutSession.connectWallet`, `OPEN → WALLET_CONNECTED`)은 범위 밖이다.** 이 Use Case는 `CheckoutSession.connectedWallet`이 이미 채워져 있다고 전제하고 그대로 재사용한다 — 지갑 연결 자체는 별도 Use Case가 먼저 처리해야 한다(아직 없음).
- **중복 제출은 멱등하게 처리하고, Hash 재사용은 명시적으로 막는다.** 같은 `(network, transactionHash)`로 이미 `BlockchainTransaction`이 있으면, 같은 Payment의 것이면 새로 만들지 않고 기존 결과를 그대로 돌려주고(재전송/중복 클릭 대응), 다른 Payment의 것이면 `DuplicateTransactionHashException`을 던진다 — `uk_blockchain_network_hash` Unique 제약과 대응하는 애플리케이션 레벨 확인이다. `ConfirmBlockchainTransactionUseCase`의 `PaymentTransactionValidator`가 "중복 여부는 여기서 다시 확인하지 않는다"고 미뤄뒀던 게 바로 이 지점이다.
- **새 공용 상수 `PaymentNetworkConfig`를 도입했다** — 네트워크별 Chain ID, 허용 USDC Contract 주소, 필요 Confirm 수(`REQUIRED_CONFIRMATION_COUNT = 12`, `docs/`에 값이 없어 고정한 MVP 상수)를 한 곳에 모았다. 원래 `ConfirmBlockchainTransactionUseCase`가 Contract 주소를 자기 것으로 갖고 있었는데, 이 Use Case도 "제출 시점의 기대값"으로 같은 값이 필요해져서 공용으로 뺐다 — 두 Use Case가 각자 상수를 들고 있으면 나중에 값이 어긋날 위험이 있었다.
- **새 Repository Port 메서드 둘을 추가했다**: `CheckoutSessionRepository.findById`(기존엔 `findByPaymentId`뿐이었다 — 이 Use Case는 체크아웃 프런트엔드가 아는 `checkoutSessionId`로 시작한다), `BlockchainTransactionRepository.findByNetworkAndTransactionHash`(중복 확인용).
- **테스트**: `SubmitPaymentTransactionUseCaseTest`(단위, 정상 생성/저장된 필드 확인/같은 결제 재제출 멱등성/다른 결제의 Hash 재사용 차단/존재하지 않는 CheckoutSession/WALLET_CONNECTED가 아닌 상태), `CheckoutSessionRepositoryAdapterTest` + `BlockchainTransactionRepositoryAdapterTest`의 새 조회 메서드 케이스(Testcontainers MySQL 통합).

### "BlockchainTransaction 감지·Confirm" Use Case(`ConfirmBlockchainTransactionUseCase`)

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

### Apps(`apps:api-payment`, `apps:api-admin`, `apps:api-merchant`, `apps:batch`)

각각 **독립적으로 배포 가능한 Spring Boot 애플리케이션**이다 — 자체 `build.gradle.kts`(`org.springframework.boot` 플러그인 적용), 자체 `@SpringBootApplication` 메인 클래스, 자체 `application.yaml`, 자체 포트를 가진다 — 하나의 공유 앱 안의 패키지가 아니다. 이건 의도적인 선택이었다(모듈러 모놀리스 대안을 두고 사용자와 확인함) — 정확히는 네 앱이 서로 다른 대상(가맹점 서버를 향한 결제 API, 내부 직원용 관리 콘솔, 가맹점 콘솔, 오프라인 배치 Job)을 상대해서 나중에 독립적으로 스케일·배포·장애가 나야 할 수 있어서다 — 이 선택을 끝까지 따른 결과로, `api-payment`와 역할이 겹치던 원래 Spring-Initializr 루트 앱도 다섯 번째 중복 배포 단위로 남겨두지 않고 삭제했다.

- **의존성은 각 앱이 지금 실제로 하는 일에만 맞춘다 — 나중에 할 일까지 미리 넣지 않는다.** 네 앱 전부 실제 Use Case가 생겼다(`CreatePaymentUseCase`/`AuthenticateInternalUserUseCase`/`AuthenticateMerchantUserUseCase`/`ConfirmBlockchainTransactionUseCase`) — 그래서 넷 다 `modules:application`/`modules:infra-persistence`/`spring-boot-starter-jooq`/`DataSource`가 연결돼 있다. `batch`는 여기에 `modules:infra-blockchain`(`BlockchainClient`)도 더 붙는다 — Confirm Worker가 온체인 조회를 직접 하기 때문이다(아래 "apps:batch의 Confirm 폴링 Worker" 참고). 세 API 앱은 `webmvc`+`security`도 갖고 있다(로그인/향후 인증 엔드포인트용); `batch`는 여전히 웹 앱이 아니다. 실제 Use Case가 필요로 할 때만 그 앱의 의존성을 넓히고, 미리 넓히지 않는다.
- **포트**: `api-payment` 8081, `api-admin` 8082, `api-merchant` 8083; `batch`는 `server.port`가 없다(웹 스타터가 없어서 웹 서버 자동 구성이 스스로 꺼진다 — `DataSource`가 생긴 지금도 웹 앱은 아니다).
- **컴포넌트 스캔**: `@SpringBootApplication`의 기본 스캔 범위는 메인 클래스 자신의 패키지와 그 하위 패키지다. 네 앱의 메인 클래스(`paytech.practice.pay.api.payment`/`api.admin`/`api.merchant`/`batch`)는 전부 `modules:infra-persistence`의 Adapter(`paytech.practice.pay.infra.persistence.jooq`)와 *형제* 관계이지 상위가 아니다 — 그래서 넷 다 `@SpringBootApplication(scanBasePackages = [자기 패키지, "paytech.practice.pay.infra.persistence.jooq", ...])`로 필요한 패키지를 모두 명시한다. `batch`는 `modules:infra-blockchain`의 Adapter 패키지(`paytech.practice.pay.infra.blockchain`)도 추가로 스캔한다. 새 앱이 다른 모듈의 Bean을 쓰기 시작하면, Gradle 의존성을 추가하는 것만으로 Bean이 연결된다고 가정하지 말고 같은 방식으로 스캔 범위를 넓힌다.
- 네 앱의 `application.yaml`은 `spring-boot-docker-compose` 자동 감지에 기대지 않고 `spring.datasource.*`를 `db-core`/`compose.yaml`이 이미 쓰는 것과 같은 로컬 개발 MySQL로 직접 가리킨다(`localhost:3306/stablecoin_payment`, `root`/`verysecret`) — 그 자동 감지 메커니즘은 실행 중인 앱 자신의 작업 디렉토리(예: `apps/api-payment/`)에서 `compose.yaml`을 찾지, `backend/`에서 찾지 않아서, 추가 경로 설정 없이는 공유 파일을 찾지 못한다.
- 테스트는 전부 같은 모양을 따른다(`@SpringBootTest` + Kotest `SpringExtension`, 앱마다 `contextLoads` 테스트 하나 — 위 "테스트" 참고). 네 앱 다 만족시켜야 할 `DataSource`가 있어서 `TestcontainersConfiguration`도 추가로 import한다.

### `api-payment`의 결제 생성 컨트롤러

`POST /api/v1/payments`(`docs/architecture/identity-access-api-key.md`의 "대표 사용 API")가 `CreatePaymentUseCase`를 HTTP로 노출하는 첫 inbound Adapter다. 패키지는 `api.payment.web`(컨트롤러/요청·응답 DTO/예외 핸들러), `api.payment.config`(Use Case를 Bean으로 조립하는 Composition Root), `api.payment.security`(API Key 인증 Filter)로 나눴다. 한때 `api.payment.support`에 outbound port 구현을 뒀지만 전부 `modules:infra-support`로 옮겨서 지금은 없다 — 앱은 Port를 구현하지 않는다("공용 Port 구현" 절 참고).

- **`UseCaseConfiguration`**: `CreatePaymentUseCase`는 `modules:application`에 있고 그 모듈은 Spring에 의존하지 않아서 `@Component`를 직접 달 수 없다 — 그래서 이 `@Configuration` 클래스가 outbound port Bean들을 주입받아 `@Bean` 메서드로 대신 조립한다. 앞으로 Use Case가 늘어나면 이 클래스에 `@Bean` 메서드를 추가한다(Use Case 하나마다 별도 Configuration 클래스를 만들 필요는 없다).
- **`IdGenerator`/`ExchangeRateProvider`의 구현이 없었다** — 둘 다 영속성 관심사가 아니라서 `modules:infra-persistence`가 구현하지 않았다. `support.UuidIdGenerator`(UUID 기반)와 `support.FakeExchangeRateProvider`(고정 환율, `docs/decisions/ADR-004-fake-exchange.md`의 Fake Exchange를 대표)를 이 앱 안에 직접 만들어 채웠다 — 둘 다 다른 앱이 필요로 하게 되면 그때 공유 위치로 옮길 수 있는, 지금은 이 정도로 충분한 임시 구현이라고 KDoc에 명시했다.
- **`PaymentApiExceptionHandler`**(`@RestControllerAdvice`)가 `application`/`domain` 예외를 HTTP 상태로 옮긴다: `MerchantNotFoundException` → 404, `MerchantCannotAcceptPaymentsException` → 409, Value Object의 `init { require(...) }` 검증 실패(`IllegalArgumentException`) → 400, `@Valid` 실패(`MethodArgumentNotValidException`) → 400. 이 매핑은 inbound Adapter의 책임이다 — Use Case나 Value Object는 HTTP를 전혀 모른다.
- **`merchantId`는 요청 본문이 아니라 인증된 `MerchantApiKey`에서 온다** — 아래 "`api-payment`의 API Key 인증" 참고. 처음 이 컨트롤러를 만들 때는 API Key 인증이 없어서 `merchantId`를 요청 본문에 직접 받는 임시 gap이 있었는데, 이제 해소됐다.
- **테스트**: `PaymentControllerTest`는 `@WebMvcTest(PaymentController::class)`로 웹 계층만 띄운다(DB 없음) — `CreatePaymentUseCase`는 `com.ninja-squad:springmockk`의 `@MockkBean`으로 Mock했다(위 "테스트" 참고). `@Autowired` 필드 주입이 필요해서 이 파일만 `FunSpec() { init { ... } }` 형태를 쓴다. 여기에 더해 실제 `bootRun` + `curl`로 시딩된 `mrc_test_001` 가맹점을 상대로 결제 생성 → 멱등 재요청(같은 `paymentId` 반환, 중복 행 없음) → DB 직접 조회까지 한 번 수동으로 검증했다(자동화된 테스트로 남기지는 않음).

### `api-payment`의 API Key 인증

`docs/architecture/identity-access-api-key.md`의 "6.4 저장 정책" 권장 흐름을 그대로 구현한다: `Authorization: Bearer sk_test_<prefixToken>_<secret>` 수신 → Prefix 추출 → Prefix로 후보 Key 조회 → 전체 Key를 서버 측 Pepper와 함께 해시 → `secret_hash` 비교 → 상태·환경·Merchant 상태 확인 → `last_used_at` 갱신. `AuthenticateInternalUserUseCase`/`AuthenticateMerchantUserUseCase`(자격증명 검증 → 신원 반환)와 같은 모양이지만, 로그인이 아니라 **보호된 요청마다** 실행된다는 점이 다르다 — 실패 잠금도 없다(사람이 타이핑하는 비밀번호가 아니라서).

- **API Key 형식**: `key_prefix`(예: `sk_test_ab12cd34`, `ApiKeyPrefix`의 KDoc 예시) 뒤에 `_<secret>`을 붙인 게 전체 Key다. `AuthenticateApiKeyUseCase.extractPrefix`는 `_`로 최대 4조각까지만 자른다(`split(limit = 4)`) — `secret`이 `_`를 포함해도 깨지지 않는다.
- **`ApiKeySecretHasher`를 `PasswordEncoder`와 의도적으로 분리했다** — 사람 비밀번호는 BCrypt 같은 느린 적응형 해시가 맞지만, API Key는 매 요청 검증이라 그럴 필요가 없다. 문서가 명시한 대로 `HmacApiKeySecretHasher`(`modules:infra-support`의 `infra.support.apikey`, 원래는 `apps:api-payment` 안에 있었다)가 HMAC-SHA-256 + 서버 측 Pepper로 구현한다. Pepper는 `application.yaml`의 `app.api-key.pepper`에서 오고, 지금 값은 `db-core`의 `verysecret` DB 비밀번호와 같은 성격의 로컬 개발용 평문 placeholder다 — 실제 배포 전 환경변수/Secret Manager로 옮겨야 한다. 해시 비교는 타이밍 공격을 막기 위해 `String.equals` 대신 `MessageDigest.isEqual`(상수 시간 비교)로 한다.
- **`MerchantApiKeyRepositoryAdapter`(`modules:infra-persistence`)는 이 프로젝트에서 처음으로 자식 컬렉션 테이블을 다루는 Adapter다.** `MerchantApiKey.scopes`는 `merchant_api_key_scope`(복합 PK, 자기 생명주기 없는 값 컬렉션)에 저장된다. 도메인에 Scope를 바꾸는 메서드가 없어서(발급 시 정해지면 끝) `save`의 INSERT 경로에서만 Scope 행을 쓰고, UPDATE 경로(`revoke`/`expire`/`recordUsage`)는 건드리지 않는다.
- **인증은 Filter가 한다, 컨트롤러가 아니다.** `ApiKeyAuthenticationFilter`(`OncePerRequestFilter`)가 `Authorization` 헤더를 읽어 매 요청 `AuthenticateApiKeyUseCase`를 부르고, 성공하면 이번 요청의 `SecurityContext`에 `UsernamePasswordAuthenticationToken(principal = ApiKeyPrincipal(merchantId, merchantApiKeyId), authorities = ["SCOPE_<ApiKeyScope>", ...])`를 심는다. 실패해도 예외를 던지지 않고 `SecurityContext`만 비운 채 다음 필터로 넘긴다 — 그 뒤 `authorizeHttpRequests`가 401/403을 결정한다.
- **`/error`는 세 API 앱 모두 `permitAll`이다** — 컨테이너가 오류 응답을 만들 때 도는 ERROR 디스패치 경로인데, 여기에 인증을 요구하면 실제 오류가 전부 401로 가려진다(인증 필터는 `OncePerRequestFilter` 기본값상 ERROR 디스패치에서 실행되지 않아 `SecurityContext`가 비어 있다). 잘못된 요청 본문은 여기에 더해 `HttpMessageNotReadableException` 핸들러가 `/error` 경로를 아예 타지 않고 `ErrorResponse` 형식으로 400을 반환한다. 인증 실패 자체는 그대로 401이다 — `/error`를 열어도 인가가 우회되지 않는 것은 실제 `bootRun`으로 확인했다(위 "테스트가 잡지 못하는 층" 참고).
- **`SecurityConfig`**: `POST /api/v1/payments`에 `hasAuthority("SCOPE_PAYMENT_CREATE")`를 요구한다. `SessionCreationPolicy.STATELESS`로 세션을 아예 안 만든다 — `apps:api-admin`/`apps:api-merchant`의 세션 쿠키 로그인과 근본적으로 다른 인증 방식이라서다. **여기서 CSRF를 끄는 건 admin/merchant처럼 "아직 안 켠 gap"이 아니라 애초에 필요 없다** — CSRF는 브라우저가 쿠키를 자동으로 실어 보내는 상황을 노리는 공격인데, 이 앱은 세션 쿠키를 쓰지 않는 순수 Bearer 토큰 인증이라 공격 대상 자체가 성립하지 않는다.
- **`ApiKeyAuthenticationEntryPoint`**가 인증 실패 401 응답을 `PaymentApiExceptionHandler`와 같은 `ErrorResponse` JSON 형식으로 통일한다 — 없으면 Spring Security 기본 엔트리 포인트가 다른 형식을 준다.
- **`PaymentController`는 `merchantId`를 `@AuthenticationPrincipal ApiKeyPrincipal`에서 받는다** — 요청 본문에는 더 이상 없다.
- **테스트**: `AuthenticateApiKeyUseCaseTest`(단위, 정상/형식 오류/Prefix 미존재/Secret 불일치/폐기/만료/`LIVE` 환경/Merchant 상태 불가를 전부 커버), `MerchantApiKeyRepositoryAdapterTest`(Testcontainers MySQL 통합, Scope 왕복까지 확인), `PaymentControllerTest`는 `@Import(SecurityConfig::class)`로 실제 인가 규칙까지 검증한다(`SecurityMockMvcRequestPostProcessors.authentication(...)`으로 `Authentication`을 직접 주입 — `authenticateApiKeyUseCase`는 `SecurityConfig`의 Bean 그래프를 만족시키기 위한 Mock일 뿐 실제로 호출되지 않는다). 여기에 더해 실제 `bootRun` + `curl`로 HMAC 해시를 미리 심어둔 테스트 Key를 상대로 헤더 없음(401) → Secret 틀림(401) → 정상 Key로 결제 생성(201, `last_used_at` 갱신 확인)까지 수동으로 검증했다.

**Spring Boot 4.1 / Jackson 3.x로 넘어오며 자주 걸리는 패키지 함정 두 가지**(둘 다 `apps:api-payment`에서 처음 부딪혔다):
- `ObjectMapper`는 `com.fasterxml.jackson.databind`가 아니라 **`tools.jackson.databind`**에 있다 — Jackson 3.x부터 그룹 ID/패키지가 `tools.jackson`으로 바뀌었다(`jackson-module-kotlin`도 `tools.jackson.module:jackson-module-kotlin`). 이 좌표는 `build-logic`의 `practicepay.spring-boot-app` convention plugin(위 "build-logic" 절 참고)에 이미 그 흔적이 있다.
- `@WebMvcTest`는 `org.springframework.boot.test.autoconfigure.web.servlet`이 아니라 **`org.springframework.boot.webmvc.test.autoconfigure`**에 있다 — Spring Boot 4.x가 `spring-boot-autoconfigure`를 기술별 전용 모듈로 쪼갠 것과 같은 개편이다(`SpringTransactionProvider`가 `spring-boot-jooq` 모듈로 옮겨진 것과 동일한 패턴 — 위 "영속성 Adapter 컨벤션" 참고). 새로운 Spring Boot 4.x 애노테이션/클래스를 쓸 때는 예전 패키지 경로를 그대로 가정하지 않는다.

### `api-admin`의 내부 운영자 로그인 컨트롤러

`POST /admin/login`(`docs/architecture/identity-access-api-key.md`의 "3.4 로그인 경로" 권장 경로)이 `AuthenticateInternalUserUseCase`를 HTTP로 노출한다. `api-payment`와 같은 패키지 구조(`api.admin.web`/`api.admin.config`/`api.admin.support`)를 따른다.

- **`AuthenticateInternalUserUseCase`**(`application.identity`)는 로그인 아이디/비밀번호만 검증하고 인증된 신원(`AuthenticateInternalUserResult`)만 돌려준다 — 세션은 전혀 다루지 않는다. `InternalUserRepository.findByLoginId` → 계정 상태 확인(`LOCKED`이고 잠금이 아직 안 풀렸으면 `AccountLockedException`, `ACTIVE`가 아니면 `InvalidCredentialsException`) → `PasswordEncoder.matches`로 비밀번호 확인 → 실패면 `InternalUser.recordFailedLogin` 기록(연속 [`MAX_FAILED_LOGIN_ATTEMPTS`]번째면 `InternalUser.lock`도 호출) 후 저장, 성공이면 `recordSuccessfulLogin` 저장. 로그인 아이디가 없거나 계정이 `INVITED`/`SUSPENDED`/`TERMINATED`인 경우도 전부 같은 `InvalidCredentialsException`으로 묶는다 — 계정 존재 여부나 상태를 호출부에 드러내지 않기 위해서다. `MAX_FAILED_LOGIN_ATTEMPTS`(5)/`LOCK_DURATION`(15분)은 `docs/`에 값이 없어서 `CreatePaymentUseCase`의 `SPREAD_RATE`처럼 이 Use Case가 상수로 고정한 MVP 값이다.
- **세션은 `AdminLoginController`가 만든다.** 로그인이 성공하면 `UsernamePasswordAuthenticationToken` + `ROLE_<InternalUserRole>` 권한으로 Spring Security `SecurityContext`를 만들고 `SecurityContextRepository`(`HttpSessionSecurityContextRepository`)로 세션에 저장한다 — 이후 요청은 이 세션 쿠키로 인증된다. `docs/`가 이 앱을 "PG 내부 관리자 **화면**"이라고 부르는 것에 맞춰(가맹점 서버 간 API Key/Bearer 인증인 `MerchantApiKey`와 다르게) 세션 쿠키 방식을 선택했다 — JWT 등 다른 방식으로 정해진 문서 근거는 없다.
- **`SecurityConfig`**: `/admin/login`만 인증 없이 열고 나머지는 인증을 요구한다. **알려진 gap: CSRF 보호를 꺼뒀다.** 세션 쿠키 인증에서 원래는 반드시 켜야 하지만, 이 학습용 MVP 단계에서는 아직 CSRF 토큰 발급/검증 흐름을 만들지 않았다 — 실제 프론트엔드가 이 API를 붙이기 전에 반드시 켜야 한다.
- **`InternalUserRepositoryAdapter`**(`modules:infra-persistence`)는 `PaymentRepositoryAdapter`와 같은 모양·같은 낙관적 잠금 한계를 가진다(위 "영속성 Adapter 컨벤션" 참고) — `internal_user`도 `version` 컬럼이 있는데 도메인 `InternalUser`는 그걸 모른다.
- **테스트**: `AuthenticateInternalUserUseCaseTest`(단위, 성공/미존재/오답/5회 오답 잠금/잠금 중 시도/잠금 만료 후 재시도/`INVITED` 계정을 전부 커버), `InternalUserRepositoryAdapterTest`(Testcontainers MySQL 통합), `AdminLoginControllerTest`(`@WebMvcTest(AdminLoginController::class)` + `@Import(SecurityConfig::class)` — 컨트롤러가 `SecurityContextRepository` Bean도 필요해서 `PaymentControllerTest`와 달리 `SecurityConfig`를 명시적으로 Import한다). 여기에 더해 실제 `bootRun` + `curl`로 BCrypt 해시를 미리 심어둔 테스트 계정을 상대로 로그인 성공(세션 쿠키 발급 확인) → 오답 5회 반복 → 잠김(`AccountLockedException`, DB의 `user_status=LOCKED` 확인)까지 수동으로 검증했다.

### `api-admin`의 내부 운영자 발급 컨트롤러

`POST /admin/internal-users`(`docs/architecture/identity-access-api-key.md`의 "3.3 발급 정책": "내부 운영자 계정은 SUPER_ADMIN만 발급할 수 있다")가 새 `IssueInternalUserUseCase`를 HTTP로 노출한다. `docs/`에 이 경로 자체가 정해져 있진 않아 `/admin/login`과 같은 리소스 계층에 `POST /api/v1/payments`와 같은 REST 관례로 새로 정했다.

- **발급 = `InternalUser(INVITED)` + `AccountInvitation(PENDING)`을 한 트랜잭션으로.** `docs/database/database-design.md`의 가맹점 등록 트랜잭션 예시(`Merchant + MerchantUser(OWNER, INVITED) + AccountInvitation`)와 같은 모양이다 — `IssueInternalUserUseCase`가 `InternalUser.invite(...)`와 `AccountInvitation.forInternalUser(...)`를 만들어 `TransactionManager.runInTransaction { }` 안에서 함께 저장한다(`CreatePaymentUseCase`와 같은 다중 Aggregate 생성 패턴). 초대를 수락해 비밀번호를 설정하고 `INVITED → ACTIVE`로 전이하는 흐름(활성화)은 별도 Use Case `AcceptAccountInvitationUseCase`로 구현했다(아래 "초대 수락(활성화) Use Case" 절 참고) — 로그인 흐름이 발급보다 먼저 별도로 구현됐던 것과 같은 이유로, 발급과는 다른 시점에 별개로 만들어졌다.
- **초대 Token은 저장하지 않고 Hash만 저장한다** — `AccountInvitation`의 KDoc과 그대로 일치한다. 원문 Token은 `IdGenerator.newId()`로 만든다(별도의 "랜덤 문자열 생성" Port를 새로 만들지 않고 기존 Port를 재사용했다). Hash는 새 Port `InvitationTokenHasher`(`hash`/`matches`, `ApiKeySecretHasher`와 완전히 같은 모양)로 만들고, `api-admin`의 `HmacInvitationTokenHasher`가 HMAC-SHA-256 + Pepper로 구현한다 — **API Key Pepper(`app.api-key.pepper`)와는 별도의 설정값(`app.invitation-token.pepper`)을 쓴다**, 한쪽 비밀값이 새도 다른 쪽까지 같이 위험해지지 않도록 하려는 의도적 분리다. `INVITATION_VALIDITY`(7일)는 `docs/`에 값이 없어 `CreatePaymentUseCase`의 `PAYMENT_VALIDITY`와 같은 성격의 MVP 상수로 고정했다. 응답의 `invitationToken`은 API Key 원문과 같은 규칙(`docs/`의 "6.4 저장 정책")으로 **이 응답에서만** 원문으로 보인다.
- **`loginId`/`email` 중복은 사전에 막는다.** 둘 다 `internal_user`의 DB Unique 제약(`uk_internal_user_login_id`/`uk_internal_user_email`)이 걸려 있어, 체크 없이 두면 raw SQL 에러가 새 나간다 — `InternalUserRepository`에 (기존 `findByLoginId`에 더해) `findByEmail`을 추가해서 둘 다 사전 조회하고, 겹치면 `DuplicateInternalUserException`(409)을 던진다. `CreatePaymentUseCase`의 멱등성 체크와 같은 성격의 한계다(DB Unique 제약만큼 원자적이지 않다).
- **호출자 식별을 위해 `InternalUserPrincipal`을 새로 도입했다.** `AdminLoginController`는 원래 `Authentication.principal`에 로그인 아이디 문자열만 심었는데, 발급 감사 정보(`createdByInternalUserId`)로 쓸 `InternalUserId`가 필요해서 `apps:api-payment`의 `ApiKeyPrincipal` 패턴을 그대로 가져와 `InternalUserPrincipal(internalUserId, loginId, role)`을 로그인 성공 시 principal로 심도록 `AdminLoginController`를 바꿨다. `InternalUserIssuanceController`는 `@AuthenticationPrincipal InternalUserPrincipal`로 발급자를 바로 받는다 — `PaymentController`가 `merchantId`를 요청 본문 대신 `ApiKeyPrincipal`에서 가져오는 것과 같은 이유다.
- **`SecurityConfig`에 역할 기반 인가가 처음 등장했다.** `authorize("/admin/internal-users", hasRole("SUPER_ADMIN"))`를 `anyRequest`보다 먼저 추가했다(Spring Security는 먼저 매칭되는 규칙을 쓴다). `SUPER_ADMIN`이 아닌 인증된 세션이 호출하면 Spring Security 기본 `AccessDeniedHandler`가 403을 돌려준다 — `apps:api-payment`의 Scope 인가(`PaymentControllerTest`의 403 케이스)와 같은 수준으로, 커스텀 JSON 바디를 만들지 않는다. 세션이 아예 없으면(로그인 안 함) 이 앱은 커스텀 `AuthenticationEntryPoint`가 없어서 Spring Security 기본 동작대로 403이 돈다(실제 `bootRun` + `curl`로 확인) — `api-payment`가 `ApiKeyAuthenticationEntryPoint`로 401 JSON 바디를 통일한 것과 달리, `api-admin`은 아직 이 부분을 커스텀하지 않았다.
- **예외 핸들러 이름을 바꿨다.** `AdminAuthExceptionHandler` → `AdminApiExceptionHandler`(로그인 전용이 아니게 됐으므로 `PaymentApiExceptionHandler`와 이름 패턴을 맞췄다) — `DuplicateInternalUserException`(409)과 `IllegalArgumentException`(400, Value Object `require()` 실패나 `InternalUserRole.valueOf()` 실패를 공통 처리, `PaymentApiExceptionHandler`와 완전히 같은 패턴)을 새로 추가했다.
- **`IdGenerator`가 `api-admin`에 처음 필요해졌다** — 당시에는 `apps:api-payment`의 `UuidIdGenerator`를 복제해 각 앱이 자기 `support` 패키지에 자체 구현을 갖게 했지만, 지금은 `modules:infra-support`의 공유 구현을 쓴다(아래 "modules:infra-support" 절 참고).
- **`AccountInvitationRepositoryAdapter`**(`modules:infra-persistence`)는 `account_invitation`에 `version` 컬럼이 없어서(`AccountInvitation`의 KDoc 참고) `InternalUserRepositoryAdapter`와 달리 낙관적 잠금 없이 단순 UPDATE로 상태 전이를 반영한다. 발급(INSERT) 시점부터 Port 계약(`save`가 상태 전이도 반영해야 함)을 절반만 구현해 두지 않으려고 `accept`/`expire`/`revoke` 이후의 UPDATE 경로도 함께 만들어 뒀는데, `AcceptAccountInvitationUseCase`가 그 `accept` UPDATE 경로를 처음 실제로 호출하는 지점이 됐다(아래 "초대 수락(활성화) Use Case" 절 참고).
- **테스트**: `IssueInternalUserUseCaseTest`(단위, 정상 발급/로그인 아이디 중복/이메일 중복), `AccountInvitationRepositoryAdapterTest`+`InternalUserRepositoryAdapterTest`의 `findByEmail` 케이스(Testcontainers MySQL 통합), `InternalUserIssuanceControllerTest`(`@WebMvcTest` + `@Import(SecurityConfig::class)`, `PaymentControllerTest`의 `SecurityMockMvcRequestPostProcessors.authentication(...)` 패턴으로 `InternalUserPrincipal`을 주입해 `SUPER_ADMIN`/`OPERATOR` 인가까지 검증). 여기에 더해 실제 `bootRun` + `curl`로 SUPER_ADMIN 로그인 → 발급(201, `invitationToken` 확인, DB에 `internal_user`+`account_invitation` 행 생성 확인) → 중복 loginId/email(둘 다 409) → 세션 없음(403) → 잘못된 role(400)까지 검증한 뒤 DB 행을 정리했다.
- **단위 테스트에서 걸린 함정: MockK의 `any()`가 값 클래스(Value Class)를 만들지 못할 수 있다.** `every { internalUserRepository.findByEmail(any()) } returns null`처럼 `Email` 타입 매개변수에 `any()`를 쓰면, MockK가 매처 서명을 만들려고 무작위 문자열로 `Email` 인스턴스를 생성하려 시도하는데 `Email`의 `init { require(value.contains("@")) }` 검증에 걸려 `IllegalArgumentException`이 난다(`LoginId`처럼 검증이 "공백 아님" 정도로 느슨한 값 클래스는 무작위 문자열이 통과해서 문제가 없다). 해결: `any()` 대신 실제 값(`findByEmail(EMAIL)`)으로 정확히 매칭한다 — 이런 종류의 값 클래스 매개변수에는 앞으로도 `any()`를 피한다.

### 가맹점 등록 Use Case(`RegisterMerchantUseCase`, `application.identity`)와 `api-admin`의 등록 컨트롤러

`POST /admin/merchants`(`docs/architecture/identity-access-api-key.md`의 "4.3 가맹점 등록과 OWNER 생성": "가맹점 등록 트랜잭션에서 `Merchant`와 최초 `MerchantUser(OWNER)`를 함께 생성한다")가 새 `RegisterMerchantUseCase`를 HTTP로 노출한다. `Merchant.create`/`MerchantUser.inviteInitialOwner`/`AccountInvitation.forMerchantUser` 도메인 팩토리는 전부 이전부터 있었다 — 이 Use Case가 그 셋을 실제로 처음 함께 호출하는 자리다.

- **`IssueInternalUserUseCase`의 "발급 + 초대" 패턴을 Aggregate 셋으로 넓혔다.** `Merchant(ACTIVE)` + `MerchantUser(OWNER, INVITED)` + `AccountInvitation(PENDING)`을 `TransactionManager.runInTransaction { }` 안에서 함께 저장한다 — `docs/database/database-design.md`의 "계정 생성 트랜잭션" 예시가 정확히 이 모양이다(아래 OutboxEvent 관련 예외 참고). 초대 수락(`INVITED → ACTIVE`)은 기존 `AcceptAccountInvitationUseCase`를 그대로 재사용한다 — 이미 `InvitationAccountType.MERCHANT_USER`를 처리하고 `api-merchant`가 `POST /merchant/account-invitations/accept`로 노출해 둔 상태라 새로 만들 게 없었다.
- **`docs/database/database-design.md`의 예시와 달리 `OutboxEvent`는 만들지 않는다 — 의도적인 이탈이다.** 그 문서의 "가맹점 등록" 트랜잭션 예시는 `OutboxEvent INSERT`를 포함하지만, `PublishOutboxEventUseCase.resolveMerchant()`는 오늘 `aggregateType="Payment"`만 지원해서 다른 타입을 만들면 `apps:batch`의 발행 Worker가 매 폴링마다 예외를 던지며 영원히 재시도하는 상태로 남는다(발행 대상에서 스스로 빠지지 않는다). 애초에 이 프로젝트에는 이메일 발송 인프라가 없어서 그 `OutboxEvent`가 실제로 무엇을 전달할지도 정해진 바 없다 — `IssueInternalUserUseCase`가 이미 같은 이유로 `InternalUser` 초대에 `OutboxEvent`를 만들지 않은 선례를 그대로 따랐다: `invitationToken` 원문을 API 응답으로 직접 돌려주고, 호출한 내부 운영자가 OWNER에게 수동으로(Out-of-band) 전달한다.
- **`merchantCode` 중복은 사전에 막지만, `ownerLoginId`/`ownerEmail`은 확인하지 않는다.** `merchant_code`는 `uk_merchant_merchant_code` 전역 Unique라서 `MerchantRepository.findByCode`로 사전 조회하고 겹치면 `DuplicateMerchantException`(409)을 던진다(`IssueInternalUserUseCase`의 `loginId`/`email` 중복 확인과 같은 한계 — DB Unique 제약만큼 원자적이지 않다). 반면 `merchant_user`의 Unique 제약은 `merchant_seq + login_id`/`merchant_seq + email`로 가맹점 안에서만 유일해서(`backend/CLAUDE.md`의 "Idempotency keys"), 이 Use Case가 항상 새로 만드는 `merchant_seq`에는 애초에 충돌할 기존 행이 없다 — 그래서 OWNER 쪽은 사전 조회 자체가 불필요하다.
- **`MerchantRepository` Port에 `save`를 처음 추가했다.** 원래 "조회만 필요해 `findBy...`만 정의한다 — 등록·상태 변경 Use Case가 추가될 때 `save` 등을 함께 확장한다"고 Port KDoc에 미리 적어뒀던 그 시점이다. `MerchantRepositoryAdapter.save`는 `PaymentRepositoryAdapter`와 같은 모양·같은 낙관적 잠금 한계를 가진다.
- **누가 호출할 수 있는지: `SUPER_ADMIN`뿐 아니라 `OPERATOR`도 허용한다.** `POST /admin/internal-users`(`SUPER_ADMIN` 전용)와 다른 부분이다 — "3.2 MVP 역할"이 `OPERATOR`의 업무를 "가맹점·결제·운영 업무"로 정의해서, 내부 계정 발급과 달리 가맹점 등록은 `OPERATOR`의 정상 업무 범위로 판단했다. `SecurityConfig`에 `authorize("/admin/merchants", hasAnyRole("SUPER_ADMIN", "OPERATOR"))`를 추가했다.
- **테스트**: `RegisterMerchantUseCaseTest`(단위, 정상 등록/가맹점 코드 중복), `MerchantRepositoryAdapterTest`에 `save` 케이스 추가(신규 삽입/기존 행 상태 갱신, Testcontainers MySQL 통합), `MerchantRegistrationControllerTest`(`@WebMvcTest` + `@Import(SecurityConfig::class)`, `SUPER_ADMIN`/`OPERATOR` 둘 다 201, `VIEWER`는 403, 인증 없음은 401/403). 여기에 더해 실제 `bootRun`(`api-admin` + `api-merchant` 동시 기동) + `curl`로 SUPER_ADMIN 로그인 → 가맹점 등록(201, `invitationToken` 확인) → **그 토큰을 `api-merchant`의 `POST /merchant/account-invitations/accept`에 그대로 제출해 실제로 수락 성공(200)** → 새 OWNER로 `api-merchant` 로그인 성공 → 가맹점 코드 재등록 시도(409)까지 발급→수락→로그인 전체 흐름을 앱 두 개에 걸쳐 검증했다. 이 마지막 단계가 `api-admin`/`api-merchant`의 `app.invitation-token.pepper`가 실제로 일치해야 한다는 제약(위 "설정과 비밀값" 절)이 처음으로 실전에서 작동하는 지점이었다 — 검증 후 DB 행은 정리했다.

### `api-merchant`의 가맹점 관리자 로그인 컨트롤러

`POST /merchant/login`(`docs/architecture/identity-access-api-key.md`의 "4.5 로그인 경로" 권장 경로)이 `AuthenticateMerchantUserUseCase`를 HTTP로 노출한다. `api-admin`의 로그인 컨트롤러와 거의 모든 게 같다(같은 패키지 구조, 같은 `SecurityConfig`/세션 쿠키 방식, 같은 CSRF-꺼짐 gap, 같은 잠금 정책 상수) — 차이만 적는다:

- **가맹점부터 특정해야 한다.** `login_id`는 가맹점 안에서만 유일하다(`merchant_seq + login_id`, "Idempotency keys" 참고) — `InternalUser`처럼 `loginId`만으로 계정을 찾을 수 없다. 그래서 `MerchantLoginRequest`/`AuthenticateMerchantUserCommand`는 `merchantCode`(사람이 읽는 가맹점 코드)를 함께 받고, Use Case가 `MerchantRepository.findByCode`로 가맹점을 먼저 확정한 다음 `MerchantUserRepository.findByMerchantIdAndLoginId`로 계정을 찾는다. 가맹점 코드가 틀려도 같은 `InvalidCredentialsException`을 던진다(가맹점 존재 여부도 노출하지 않는다) — 이걸 위해 `MerchantRepository` Port에 `findByCode`를 추가했다(기존엔 `findById`만 있었다).
- **가맹점 자체의 상태는 로그인 가능 여부에 영향을 주지 않는다.** `Merchant`가 `SUSPENDED`여도 그 가맹점의 관리자는 이유를 확인하러 로그인할 수 있어야 한다는 판단이다 — 문서에 명시된 규칙은 아니고, `AuthenticateMerchantUserUseCase`의 KDoc에 그렇게 남겨뒀다.
- **`MerchantUserRepositoryAdapter`**(`modules:infra-persistence`)는 `InternalUserRepositoryAdapter`와 같은 모양이지만 FK가 하나 더 있다 — `merchant_seq`(소속 가맹점)에 더해 `invited_by_internal_user_seq`/`invited_by_merchant_user_seq`(둘 다 nullable, 초대자 감사 정보)까지 resolve한다.

### 초대 수락(활성화) Use Case(`AcceptAccountInvitationUseCase`, `application.identity`)

`IssueInternalUserUseCase`가 만든 `InternalUser(INVITED)` + `AccountInvitation(PENDING)`을
대상으로, 초대받은 사람이 원문 Token과 새 비밀번호를 제출해 `INVITED → ACTIVE`로
전이시키는 흐름이다(`docs/domain/state-transitions.md`의 "활성화": "유효한 초대,
초대 만료 전, 비밀번호 설정 완료"). `api-admin`/`api-merchant` 둘 다에서 쓰인다 —
`docs/architecture/identity-access-api-key.md`가 `InternalUser`/`MerchantUser`
둘 다 같은 `INVITED → ACTIVE` 상태 흐름을 공유한다고 정의했고, 실제로
`AccountInvitation`이 이미 `accountType`으로 둘을 구분하며 두 애그리게이트의
`activate(passwordHash, activatedAt)` 시그니처가 완전히 같아서, Use Case 하나로
합쳐 만들었다 — 거의 동일한 로직을 두 Use Case로 중복시키지 않는다.

- **`Command.expectedAccountType`으로 앱 경계를 강제한다.** `api-admin`은 항상
  `InvitationAccountType.INTERNAL_USER`로, `api-merchant`는 항상
  `InvitationAccountType.MERCHANT_USER`로 고정해서 호출한다 — 실제
  `AccountInvitation.accountType`이 다르면 다른 앱 경계의 초대 Token을 잘못
  제출한 것으로 보고 거부한다(가맹점 사용자 초대 Token을 `api-admin`
  엔드포인트에 제출해도 통과하지 않는다).
- **새 예외 `InvalidInvitationException`은 `InvalidCredentialsException`과 완전히
  같은 철학이다.** Token 없음/`accountType` 불일치/`PENDING`이 아님(이미
  수락·만료·폐기됨)/만료 시각 지남 — 네 경우를 전부 같은 메시지로 가린다. 어느
  조건에서 실패했는지 드러내면 다른 사람의 초대 Token 존재 여부를 무차별
  대입으로 탐색할 여지가 생긴다.
- **만료된 초대를 발견해도 `AccountInvitation.expire()`를 호출해 `EXPIRED`로
  갱신하지는 않는다.** `docs/database/database-design.md`의
  `idx_account_invitation_pending(invitation_status, expires_at)` 인덱스가
  암시하는 별도의 만료 Sweep Worker의 책임으로 남겨뒀다(아직 없음, 알려진
  gap) — 이 Use Case는 만료 여부를 읽기 전용으로만 판단하고 상태를 바꾸지
  않는다.
- **Token을 URL 경로가 아니라 요청 본문으로 받는다**(`POST
  /admin/account-invitations/accept`, `POST /merchant/account-invitations/accept`
  — `docs/`에 이 경로 자체가 정해져 있지 않아 새로 정했다) — 접근 로그에 민감한
  Token 원문이 남지 않게 하려는 의도적 선택이다(`docs/`의 "6.4 저장 정책"이 API
  Key 원문 노출을 최소화하는 것과 같은 정신).
- **두 경로 다 `SecurityConfig`에서 `permitAll`이다** — 호출자는 아직 인증되지
  않은 상태(Token만 갖고 있다)라서, `/admin/login`/`/merchant/login`과 같은
  자리에 둔다.
- **빠져 있던 조회 Port 3개를 추가했다**: `AccountInvitationRepository.
  findByTokenHash`(`account_invitation.token_hash`가 이미 `UNIQUE` 인덱스라
  `MerchantApiKey`의 Prefix→Hash 2단계 조회와 달리 곧바로 정확히 일치하는 값으로
  조회한다 — 스키마가 이미 그렇게 설계돼 있었을 뿐 새로 판단한 게 아니다),
  `InternalUserRepository.findById`, `MerchantUserRepository.findById`(둘 다
  `AccountInvitation.internalUserId`/`merchantUserId`로 대상 계정을 로드하는 데
  쓴다 — `MerchantUserRepositoryAdapter`에 이미 있었지만 지금까지 안 쓰이던
  private `resolveMerchantId(merchantSeq)` 헬퍼를 이 메서드가 처음 실제로 쓴다).
- **`api-merchant`에는 `InvitationTokenHasher` 구현체가 아직 없었다** — 당시에는
  `api-admin`의 `HmacInvitationTokenHasher`를 복제해 `api-merchant/support/`에
  추가했지만, 지금은 두 앱 다 `modules:infra-support`의 공유 구현을 쓴다
  (아래 "modules:infra-support" 절 참고). `api-merchant/application.yaml`의
  `app.invitation-token.pepper` 설정은 그대로 필요하다 — 그 값을 읽는 Bean이
  공유 모듈로 옮겨졌을 뿐 설정 자체는 앱마다 있어야 한다.
- **`AccountInvitation + (InternalUser 또는 MerchantUser)`를 함께 저장하는
  트랜잭션 경계는 `docs/architecture/persistence-jooq.md`가 명시한 세 경계
  어디에도 없다** — `IssueInternalUserUseCase`가 발급 시점에 이미 같은 방식으로
  새 경계를 정의한 선례를 그대로 따랐다.
- **테스트**: `AcceptAccountInvitationUseCaseTest`(단위, InternalUser 정상
  수락/MerchantUser 정상 수락/존재하지 않는 Token/accountType 불일치/이미
  ACCEPTED/만료됨), `AccountInvitationRepositoryAdapterTest`의
  `findByTokenHash` 케이스 + `InternalUserRepositoryAdapterTest`/
  `MerchantUserRepositoryAdapterTest`의 `findById` 케이스(Testcontainers MySQL
  통합), `AcceptAccountInvitationControllerTest`(api-admin/api-merchant 각각,
  `@WebMvcTest` + `@Import(SecurityConfig::class)` — 비인증 요청도 성공해야
  함을 검증). 여기에 더해 실제 `bootRun` + `curl`로 `api-admin`에서 발급 API →
  그 응답의 `invitationToken`을 그대로 수락 API에 제출 → `INVITED → ACTIVE`
  전이(DB `user_status=ACTIVE` 확인) → 그 계정으로 실제 `/admin/login` 로그인
  성공까지 발급→수락→로그인 흐름 전체를 처음으로 끝까지 검증했다.

### `apps:batch`의 Confirm 폴링 Worker

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

### `apps:batch`의 OutboxEvent 발행 Worker

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

### "Fake Exchange 매도" Use Case(`SellToFakeExchangeUseCase`, `application.exchange`)

`docs/architecture/mvp-scope.md`의 전체 흐름 중 마지막 구간 `Fake Exchange 매도 →
SettlementReceivable READY`와, `docs/architecture/persistence-jooq.md`가 정의한
세 번째이자 마지막 트랜잭션 경계 "환전 완료"(`ExchangeOrder COMPLETED +
SettlementReceivable READY + OutboxEvent`)를 구현한다. 이 Use Case가 성공하면
MVP 완료 경계(`Payment=SUCCEEDED`, `ExchangeOrder=COMPLETED`,
`SettlementReceivable=READY`)가 처음으로 끝까지 채워진다. `ExchangeOrder`/
`SettlementReceivable` 도메인 애그리게이트 자체는 이전부터 구현돼 있었다 — 이
Use Case가 실제로 그 둘을 만들고 완료시키는 첫 호출부다.

- **이미 `SUCCEEDED`인 Payment 하나를 대상으로 한 매도 시도 한 번이다** —
  `ConfirmBlockchainTransactionUseCase`/`PublishOutboxEventUseCase`와 같은 모양.
  `docs/decisions/ADR-004-fake-exchange.md`는 트리거 방식을 명시하지 않지만,
  이 코드베이스에 이미 두 번 반복된 확립된 패턴(Use Case는 대상 하나에 대한
  시도 한 번, `apps:batch`의 Worker가 반복 호출)을 세 번째로 그대로 따랐다 —
  새 아이디어를 만들지 않았다.
- **Fake Exchange는 `ExchangeOrder.create()` 직후 곧바로 `complete()`를 호출해
  `SUBMITTED`/`PROCESSING`을 건너뛴다**(`ExchangeOrder.complete`의 KDoc, ADR-004에
  이미 그렇게 설계돼 있었다). `clientOrderId`는 `"sell_" + paymentId`로
  Payment ID에서 결정론적으로 만든다 — 같은 Payment로 재시도해도 항상 같은 값이라
  `uk_exchange_client_order_id` Unique 제약과 충돌하지 않는다.
- **Gross/Fee/Adjustment 금액 계산을 이 Use Case에 인라인했다, 별도 파일을 만들지
  않았다.** `docs/domain/domain-model.md`는 이 계산을 `SettlementAmountCalculator`라는
  별도 Domain Service로 분류하지만, 바로 옆에 나열된 `PaymentAmountCalculator`
  (KRW→USDC 변환)도 실제로는 별도 파일 없이 `CreatePaymentUseCase`에 인라인돼
  있다 — 그 기존 선례를 그대로 따랐다. `grossAmount`는 정산 기준 금액이라 정의상
  매도 시점이 아니라 원래 주문 시점 KRW 금액(`Payment.orderAmount`)을 그대로
  쓴다 — 결제 시점과 매도 시점 사이 시장 환율이 움직인 차이는
  `SettlementReceivable.exchangeProfitLossAmount`(매도로 실제 확보한 KRW −
  grossAmount)에 담긴다. `SETTLEMENT_FEE_RATE`(1.5%)도 `CreatePaymentUseCase`의
  `SPREAD_RATE`와 같은 성격의 MVP 상수다(`docs/`에 값이 없어 고정).
- **"환전 완료" Webhook용 `OutboxEvent`는 `aggregateType="Payment"`를 재사용한다,
  새 aggregateType을 만들지 않았다.** `PublishOutboxEventUseCase.resolveMerchant()`가
  오늘 `"Payment"`만 지원해서(다른 aggregateType이 생기면 그때 분기를 넓힌다고
  이미 KDoc에 적혀 있었다), 여기서 `eventType="payment.settled"`로만 구분하고
  `aggregateType`/`aggregateId`는 `ConfirmBlockchainTransactionUseCase`의
  `payment.succeeded` 이벤트와 똑같이 Payment를 가리키게 했다 —
  `PublishOutboxEventUseCase`를 고치지 않고 그대로 재사용했다.
- **`PaymentRepository.findPendingExchangeSettlement()`를 새로 추가했다** —
  `payment_status='SUCCEEDED'`이면서 아직 `exchange_order` 행이 없는 Payment를
  찾는다(`PAYMENT`에 `NOT EXISTS(SELECT 1 FROM EXCHANGE_ORDER WHERE
  EXCHANGE_ORDER.PAYMENT_SEQ = PAYMENT.PAYMENT_SEQ)`). `payment` 레코드에
  정산 상태를 절대 추가하지 않는다는 루트 `CLAUDE.md`의 규칙 때문에 Payment
  테이블만으로는 "이미 매도 처리됐는지"를 판단할 수 없어 불가피하게 크로스
  애그리게이트 조회가 됐다 — Confirm Worker/Outbox 발행과 달리
  `docs/database/database-design.md`에 이 폴링만을 위한 전용 인덱스가 명시돼
  있지는 않다(알려진 gap, 다만 이 MVP 데이터량에서는 풀스캔으로도 문제없다).
- **새 outbound Port 둘을 추가했다**: `ExchangeOrderRepository`(`save`/
  `findByPaymentId`), `SettlementReceivableRepository`(`save`/`findByPaymentId`) —
  둘 다 `payment_seq` Unique 제약(`uk_exchange_payment`/
  `uk_settlement_receivable_payment`)과 대응하는 멱등성 조회다.
- **새 Adapter `ExchangeOrderRepositoryAdapter`/`SettlementReceivableRepositoryAdapter`**
  (`modules:infra-persistence`, 새 패키지 `infra.persistence.jooq.exchange`/
  `.settlement`)는 `WebhookDeliveryRepositoryAdapter`와 같은 모양·같은 낙관적
  잠금 한계를 가진다 — 둘 다 `version` 컬럼이 있다. `quote_currency`/
  `settlement_currency` 컬럼은 `PaymentRepositoryAdapter`의 `order_currency`
  하드코딩과 같은 이유로 `"KRW"` 리터럴로 채운다.
- **`apps:batch`에도 `FakeExchangeRateProvider`가 필요해졌다** — 당시에는
  `apps:api-payment`의 구현을 복제했고("필요해지면 그때 공유 모듈로 옮긴다"),
  실제로 그 시점이 와서 지금은 `modules:infra-support`의 공유 구현을 쓴다
  (아래 "modules:infra-support" 절 참고).
- **테스트**: `SellToFakeExchangeUseCaseTest`(단위, 정상 처리/멱등 재실행/
  존재하지 않는 Payment/SUCCEEDED가 아닌 상태), `ExchangeOrderRepositoryAdapterTest`
  + `SettlementReceivableRepositoryAdapterTest`(Testcontainers MySQL 통합, insert/
  상태 전이 update/`findByPaymentId`), `PaymentRepositoryAdapterTest`에
  `findPendingExchangeSettlement` 케이스 추가(SUCCEEDED+ExchangeOrder 없음
  포함/SUCCEEDED+ExchangeOrder 있음 제외/SUCCEEDED 아님 제외).

### `apps:batch`의 Fake Exchange 매도 폴링 Worker

`apps:batch`의 세 번째 Job이며, 앞선 두 Worker와 완전히 같은 골격(`Job`/`Step`/
`Tasklet`, `JobOperator`, `ResourcelessTransactionManager`,
`spring.batch.job.enabled: false`, 10초 `@Scheduled` 폴링, 하나 실패해도 나머지
계속)을 그대로 재사용한다 — 그 골격 자체의 근거는 위 "apps:batch의 Confirm 폴링
Worker" 절 참고. `SellPendingPaymentsToFakeExchangeTasklet`이
`PaymentRepository.findPendingExchangeSettlement()`로 대상을 뽑아
`SellToFakeExchangeUseCase`를 하나씩 호출한다.

- **테스트**: `SellPendingPaymentsToFakeExchangeTaskletTest`(단위, 대상 전부
  호출/하나 실패해도 나머지 계속/빈 목록은 no-op — `ConfirmPendingBlockchainTransactionsTaskletTest`/
  `PublishPendingOutboxEventsTaskletTest`와 같은 케이스 구성).

### IntelliJ HTTP Client(`.http` 파일)로 API 수동 테스트하기

각 `apps/*`에 `requests.http`가 있다(`apps/api-payment/requests.http`, `apps/api-admin/requests.http`, `apps/api-merchant/requests.http`) — IntelliJ가 인식하는 형식이다(에디터에서 열면 요청 옆에 ▶ 실행 아이콘이 뜬다). `backend/http-client.env.json`이 세 앱의 `baseUrl`과 로그인 아이디/비밀번호/API Key 같은 공용 변수를 "local" 환경으로 묶어 둔다 — 요청을 실행하기 전에 에디터 오른쪽 위에서 환경을 "local"로 고른다.

- **먼저 해당 앱을 띄운다**: `gradlew.bat :apps:api-payment:bootRun`처럼 `.http` 파일이 있는 앱을 실행해야 요청이 응답을 받는다.
- **자격증명은 `db/seed/seed_dev_identity_data.sql`이 심는다** — 스키마 마이그레이션(`db/migration/`)을 적용한 뒤 `db/seed/`의 두 파일도 같은 방식(`docker exec ... < 파일`)으로 적용해야 `.http` 파일의 요청들이 실제로 인증에 성공한다. 시드는 Flyway 마이그레이션이 아니라서(운영에서 자동 제외되도록 분리했다) 스키마와 함께 자동으로 적용되지 않는다(아래 "Database / jOOQ code generation"의 "시드 데이터" 참고). 로그인 비밀번호는 `dev-admin`/`dev-owner` 둘 다 `DevPassword123!`이고, 결제 API Key는 `sk_test_devkey01_dev-secret-value`(scope: `PAYMENT_CREATE`+`PAYMENT_READ`)다 — 전부 로컬 개발 전용 값이고, 시드 파일 맨 위 주석에도 같은 내용이 있다.
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

각각이 주어진 키(들)에 대해 유일성/멱등성을 강제한다 — 이걸 쓰고, 별도의 중복 제거 로직을 새로 만들지 않는다:

| Entity | Key |
|---|---|
| Payment 생성 | `merchant_seq + merchant_order_id` |
| BlockchainTransaction | `network_code + transaction_hash` |
| ExchangeOrder | `client_order_id` |
| SettlementReceivable | `payment_seq` |
| WebhookDelivery | `event_id + merchant_seq` |
| OutboxEvent | `event_id` |
| InternalUser | `login_id`(별도로 `email`도 유일) |
| MerchantUser | `merchant_seq + login_id`(별도로 `merchant_seq + email`도 유일) |
| AccountInvitation | `token_hash` |
| MerchantApiKey | `key_prefix` |

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
- 생성된 코드는 `paytech.practice.pay.dbcore.jooq` 패키지 아래에 있고, 아래 Persistence conventions에 따라 향후 영속성 Adapter(`modules/infra-persistence`) 안에서만 써야 한다 — `domain`/`application`에서는 절대 쓰지 않는다.
- `jooqCodegen`은 `internal_user`와 `merchant_user`에 대해 (무해한) `Ambiguous key name` 경고를 출력한다 — 두 테이블 모두 자기 자신/서로에게 FK가 여러 개 걸려 있어서(`created_by_internal_user_seq`, `invited_by_internal_user_seq`, `invited_by_merchant_user_seq`), jOOQ가 모든 암묵적 Join 편의 접근자에 유일한 이름을 자동으로 붙이지 못한다. 빌드는 여전히 성공하고 생성된 `Table`/`Record` 클래스에도 영향이 없다 — 몇몇 선택적인 경로 탐색 편의 메서드만 생략될 뿐이다.

## Persistence 컨벤션(구현되면)

- MySQL 8.x, jOOQ만 사용 — **JPA/Hibernate는 쓰지 않는다**. 생성된 jOOQ Record는 영속성 Adapter 내부에서만 쓰고 domain/application 계층으로 새어나가지 않으며, Record와 도메인 객체 사이는 명시적인 Mapper로 변환한다(암묵적/리플렉션 매핑 없음).
- Command Repository는 애그리게이트 전체를 저장·복원하고, 복잡한 조회는 애그리게이트 Repository 대신 전용 jOOQ Projection 쿼리를 거친다.
- 낙관적 잠금: 변경 가능한 애그리게이트의 테이블은 `version BIGINT`를 갖고, UPDATE는 ID + 예상 현재 상태 + 예상 version을 조건으로 건다.
- 타입 매핑: KRW → `BIGINT`/`Money`, USDC → Minor Unit `BIGINT`/`TokenAmount`(예: `72.992701 USDC = 72,992,701`), 환율 → `DECIMAL(24,12)`/`BigDecimal`, 타임스탬프 → `DATETIME(6)` UTC, 상태 컬럼 → Kotlin Enum이 뒷받침하는 `VARCHAR`(MySQL `ENUM`은 절대 쓰지 않고, 금액이나 환율에 `FLOAT`/`DOUBLE`도 절대 쓰지 않는다).
- 주요 트랜잭션 경계: 결제 생성은 `Payment + PaymentQuote + CheckoutSession + OutboxEvent`를 묶고, 결제 완료는 `BlockchainTransaction + Payment(SUCCEEDED) + OutboxEvent`를 묶고, 환전 완료는 `ExchangeOrder(COMPLETED) + SettlementReceivable(READY) + OutboxEvent`를 묶는다.
- 비동기 부수효과(Webhook, 이벤트)는 트랜잭션 내 직접 발행이 아니라 Transactional Outbox 패턴(`outbox_event` 테이블)을 거친다.
