# CLAUDE.md (backend)

`backend/`에서 작업할 때의 지침이다. 공통 결제 도메인(애그리게이트, 상태 머신, MVP 범위)은 루트 `../CLAUDE.md`를 참고한다 — 이 문서는 백엔드 구현 컨벤션만 다룬다.

## 현재 구현 상태

`modules:domain`, `modules:application`, `modules:infra-persistence`, `db-core`, `architecture-tests`, 그리고 `apps:*` 4개 전부 실제 Gradle 서브프로젝트다(`settings.gradle.kts` 참고). 나머지는 아직 비어 있는 Placeholder로, 아무것도 연결돼 있지 않다. Placeholder 폴더에 코드가 있다고 가정하지 말고, 참조하기 전에 먼저 확인한다. 이 구조가 이미 여러 번 재편됐으니 의존하기 전에 다시 확인한다:

```
apps/
  api-payment/       실제 Gradle 서브프로젝트, 독립 배포 가능한 Spring Boot 앱(자체 메인 클래스, 자체 포트) —
                     webmvc + jooq + security이고 modules:application + modules:infra-persistence에 의존한다.
                     CreatePaymentUseCase(POST /api/v1/payments)를 MerchantApiKey Bearer 인증으로 보호한다
                     (Apps 절 참고).
  api-admin/         실제 Gradle 서브프로젝트, 독립 배포 가능한 Spring Boot 앱 — webmvc + jooq + security,
                     modules:application + modules:infra-persistence에 의존한다. AuthenticateInternalUserUseCase
                     (POST /admin/login)와 IssueInternalUserUseCase(POST /admin/internal-users, SUPER_ADMIN 전용)가
                     있다(Apps 절 참고). 초대 수락(활성화) 등 나머지 흐름은 아직 Use Case가 없다.
  api-merchant/      실제 Gradle 서브프로젝트, 독립 배포 가능한 Spring Boot 앱 — webmvc + jooq + security,
                     modules:application + modules:infra-persistence에 의존한다. AuthenticateMerchantUserUseCase와
                     그걸 노출하는 컨트롤러(POST /merchant/login)가 있다(Apps 절 참고). 가맹점 등록, 하위
                     계정 발급, API Key 등 나머지 흐름은 아직 Use Case가 없다.
  batch/             실제 Gradle 서브프로젝트, 독립 배포 가능한 Spring Boot 앱 — spring-boot-starter-batch,
                     웹 스타터 없음, 아직 정의된 Job 없음(예: 향후 OutboxEvent 발행 Worker의 자리)
modules/
  application/       실제 Gradle 서브프로젝트, domain에 의존; CreatePaymentUseCase(결제 생성 슬라이스), Identity/API Key
                     Use Case(Authenticate*/IssueInternalUser) + 그 outbound port들(Architecture 참고)
  common/            (Placeholder)
  domain/            실제 Gradle 서브프로젝트, 의존성 없음; 8개 결제 애그리게이트 전부 + OutboxEvent + Identity/API Key 애그리게이트(Domain code conventions 참고)
  infra-blockchain/  (Placeholder)
  infra-persistence/ 실제 Gradle 서브프로젝트 — modules:application의 outbound port를 구현하는 jOOQ Repository Adapter(Architecture 참고)
db-core/             실제 Gradle 서브프로젝트 — Flyway 마이그레이션 + jOOQ 코드 생성(아래 참고)
architecture-tests/  실제 Gradle 서브프로젝트, 테스트 전용(src/main 없음) — 다른 모듈의 컴파일된 클래스에 대한 ArchUnit 규칙
```

**루트 프로젝트에는 자체 코드가 없다.** 원래 Spring Initializr 스켈레톤 앱(`PracticePayApplication.kt`, 삭제됨)이었는데, `apps/api-payment`가 실제 결제 API 배포 단위 역할을 넘겨받으면서 중복이 됐다 — 그래서 중복으로 남겨두지 않고 삭제했다. `backend/build.gradle.kts`는 이제 모든 서브프로젝트에 적용되는 횡단 관심사 `allprojects {}` 블록(ktlint + `repositories {}`)만 갖고 있다 — 루트 프로젝트 자체에는 Kotlin이나 Spring Boot 플러그인을 적용하지 않는다. `backend/src/` 아래에 소스를 추가하지 말고, 해당하는 `apps:*` 또는 `modules:*` 서브프로젝트에 추가한다.

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

- 로컬 MySQL: `compose.yaml`이 `docker compose up`용 `mysql:latest` 서비스를 정의하고, `stablecoin_payment` DB로 시딩된다(스키마와 일치 — 아래 "Database / jOOQ code generation" 참고). `apps:api-payment`의 테스트는 대신 Testcontainers를 자동으로 쓴다(자체 `TestcontainersConfiguration.kt`가 `@ServiceConnection`으로 MySQL 컨테이너를 띄운다); `apps:api-admin`/`apps:api-merchant`/`apps:batch`는 아직 DB를 건드리지 않아서 둘 다 필요 없다.
- 툴체인: Java 25, Kotlin 2.3.21, Spring Boot 4.1.0(각 `apps:*`/`db-core`/`modules:infra-persistence` 서브프로젝트 기준 — 루트 프로젝트 자체는 더 이상 Kotlin이나 Spring Boot 플러그인을 적용하지 않는다).
- Lint/포맷: **ktlint**를 `org.jlleitschuh.gradle.ktlint` 플러그인(14.2.0)으로 모든 모듈에 적용한다(계층형 `modules:domain`/`modules:application` include를 위해 Gradle이 만드는 Phantom 부모 `:modules`도 포함) — 루트 `build.gradle.kts`의 `allprojects {}`를 통해서다. ktlint 설정이 모듈마다 달라질 이유가 없어서, 이 프로젝트의 "모듈마다 작은 설정을 중복한다"는 스타일의 유일한 예외다. `backend/.editorconfig`가 `indent_style = tab`을 고정해서(이 프로젝트의 기존 컨벤션) ktlint가 스페이스로 강제 포맷하지 않게 한다. `ktlintCheck`는 이미 `check`/`build`의 일부로 실행되므로, 빌드가 성공하면 Lint도 깨끗하다는 뜻이다. `db-core/build.gradle.kts`는 `generated-src`(jOOQ가 생성한 코드, 직접 수정하지 않음)를 Lint 대상에서 제외하고, 그걸 읽는 ktlint 태스크에 명시적으로 `dependsOn("jooqCodegen")`을 추가한다 — Gradle의 태스크 입력 검증이, 어떤 디렉토리를 읽는 태스크라면 그 디렉토리를 만드는 태스크에 대한 의존성 선언을 요구하기 때문이다.

## 테스트

- 테스트 프레임워크는 **Kotest**(`FunSpec` 스타일)다 — JUnit5의 `@Test`/`kotlin-test`가 아니다. `gradlew.bat test`는 JUnit Platform 위의 `kotest-runner-junit5`를 통해 Kotest Spec을 자동으로 수집한다(`useJUnitPlatform()`이 이미 설정돼 있음, 추가 설정 불필요).
- Spring 컨텍스트 테스트는 Spec 본문 안에서 `extensions(SpringExtension)`으로 `io.kotest.extensions.spring.SpringExtension`을 등록하고, 평소처럼 `@SpringBootTest`/`@Import` 애노테이션도 함께 쓴다(`apps/api-payment/src/test/kotlin/paytech/practice/pay/api/payment/PaymentApiApplicationTests.kt` 참고). `@Autowired`로 필드 주입을 받아야 하는 테스트(예: `@WebMvcTest` 슬라이스)는 `FunSpec({ ... })` 트레일링 람다 대신 `FunSpec() { @Autowired lateinit var ...; init { ... } }` 형태를 쓴다 — 람다 생성자로는 `@Autowired` 필드를 선언할 자리가 없어서다(`PaymentControllerTest` 참고).
- Mocking은 Mockito가 아니라 **MockK**(`io.mockk`)를 쓴다. Spring Boot Test 슬라이스에서 Bean을 Mock으로 바꿔야 할 때(`@MockBean`/`@SpyBean` 자리)는 Mockito 전용인 그 애노테이션 대신 MockK판인 `com.ninja-squad:springmockk`의 `@MockkBean`을 쓴다(`PaymentControllerTest` 참고) — 이 프로젝트 전체가 Mockito 없이 MockK 하나로 통일돼 있다.
- Assertion은 `kotest-assertions-core`(`shouldBe` 등)를 쓴다.
- 아키텍처 규칙(예: domain이 Spring/jOOQ에 의존하지 않는다, 아래의 헥사고날 계층 구조)은 **ArchUnit**(`com.tngtech.archunit:archunit`)으로 강제한다 — 별도의 `archunit-junit5` 엔진/`@AnalyzeClasses` 스타일이 아니라, 평범한 Kotest `test { }` 블록 안에서 `ClassFileImporter().importPackages(...)` + `.check(classes)`를 호출하는 방식이다. 프로젝트 전체가 하나의 테스트 작성 컨벤션(Kotest)을 유지하기 위해서다. 모듈 간 규칙(한 모듈의 컴파일된 클래스를 외부에서 검사)은 `architecture-tests`(테스트 전용 Gradle 서브프로젝트; 검사 대상 모듈을 `testImplementation` 의존성으로 추가한다 — `DomainPurityTest` 참고)에 둔다.

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
- **`modules:infra-persistence/build.gradle.kts`는 `kotlin("plugin.spring")`을 적용한다** — Spring Boot는 인터페이스를 구현한 Bean이라도 기본적으로 JDK 동적 프록시가 아니라 CGLIB(서브클래싱) 프록시를 쓴다(`spring.aop.proxy-target-class=true`가 기본값). Kotlin 클래스는 기본이 `final`이라 CGLIB이 서브클래싱하지 못하고 `Cannot subclass final class ...`로 죽는다 — `kotlin("plugin.spring")`이 `@Component`(`@Repository` 포함, 메타 애노테이션까지 인식)가 붙은 클래스를 자동으로 `open`으로 만들어준다. 이 모듈 자체의 테스트는 Adapter를 직접 `new`해서 Spring DI/AOP를 전혀 거치지 않아 이 문제를 드러내지 않았다 — `apps:api-payment`가 실제 Spring 컨테이너로 이 Adapter들을 부팅하고 나서야 처음 발견됐다.
- **jOOQ가 생성한 테이블 클래스가 여러 도메인 애그리게이트와 이름이 겹친다**(`Payment`, `Merchant`, `CheckoutSession`, `PaymentQuote`, `OutboxEvent` 모두 `paytech.practice.pay.dbcore.jooq.tables.*` 클래스와 `paytech.practice.pay.domain.*` 클래스 양쪽에 존재한다). 모든 Adapter가 같은 방식으로 푼다: 테이블 클래스 자체가 아니라 그 Companion을 거쳐 싱글턴 상수만 import한다(`import ...tables.Payment.Companion.PAYMENT`) — 클래스 자체를 이름으로 참조하지 않으니 도메인 import와 겹칠 게 없다.
- `DATETIME(6)` UTC 컬럼에 대한 `Instant` ↔ `LocalDateTime` 변환은 `infra.persistence.jooq.InstantMapping.kt`의 공유 `toUtcLocalDateTime()`/`toUtcInstant()` 확장 함수를 거친다 — Adapter마다 `ZoneOffset.UTC` 변환을 직접 만들지 않는다.
- 도메인에 대응 값이 없는 컬럼(`payment.order_currency`, `payment_quote.quote_currency`)은 Adapter 경계에서 `"KRW"` 리터럴로 하드코딩해서 채운다 — 이 코드베이스 전체에서 `Money`가 암묵적으로 항상 KRW를 뜻하는 것과 같은 맥락이다(MVP는 KRW→USDC 한 쌍만 지원).
- **알려진 한계: `Payment`/`CheckoutSession`(`version` 낙관적 잠금 컬럼이 있는 두 애그리게이트)의 `save()`는 지금 진짜 낙관적 잠금 보호를 제공하지 않는다.** 도메인 애그리게이트는 `version` 필드를 갖고 있지 않다(영속성 관심사를 도메인 계층에 새지 않으려고 의도적으로 뺐다) — 그래서 Adapter는 UPDATE 직전에 DB의 현재 `version`을 다시 읽어 `current + 1`을 쓴다 — 이건 정확히 같은 Adapter 호출로의 동시 쓰기만 막을 뿐, "이 애그리게이트가 오래된 version에서 읽혔다"는 상황은 잡지 못한다. 기존 애그리게이트를 다시 저장하는 첫 상태 전이 Use Case가 생기면(Port를 통해 예상 version을 전달하거나, DB 쪽 `SELECT ... FOR UPDATE`를 전면적으로 쓰는 방향으로) 반드시 다시 검토한다 — 지금은 `CreatePaymentUseCase`만 `save()`를 부르고 항상 새 애그리게이트만 저장해서 이 한계가 실질적인 영향은 없다.
- **테스트**: `infra-persistence`는 Mock이 아니라 실제 MySQL 통합 테스트를 쓴다 — 테스트 JVM 전체가 공유하는 Testcontainers MySQL 인스턴스(`PersistenceTestSupport`)를, `org.flywaydb.flyway` Gradle 플러그인이 아니라 `flyway-core` Java API로 직접(`Flyway.configure()...migrate()`) 마이그레이트한다(Gradle 9.5.1에서 깨진 건 그 플러그인이지 — 아래 "Database / jOOQ code generation" 참고 — 순수 Java 라이브러리 자체와는 무관하다). 테스트용 `DSLContext`는 Spring Boot의 `JooqAutoConfiguration`이 실제로 구성하는 방식과 똑같이(`DataSourceConnectionProvider` + `TransactionAwareDataSourceProxy` + `spring-boot-jooq` 모듈의 `org.springframework.boot.jooq.autoconfigure.SpringTransactionProvider` — Spring Boot 4.x가 jOOQ 자동 구성을 `spring-boot-autoconfigure`에서 이 전용 모듈로 옮겼다) 배선해서, `TransactionManagerAdapterTest`가 여러 Repository의 쓰기가 실제로 함께 롤백되는지까지 증명할 수 있다.

### Apps(`apps:api-payment`, `apps:api-admin`, `apps:api-merchant`, `apps:batch`)

각각 **독립적으로 배포 가능한 Spring Boot 애플리케이션**이다 — 자체 `build.gradle.kts`(`org.springframework.boot` 플러그인 적용), 자체 `@SpringBootApplication` 메인 클래스, 자체 `application.yaml`, 자체 포트를 가진다 — 하나의 공유 앱 안의 패키지가 아니다. 이건 의도적인 선택이었다(모듈러 모놀리스 대안을 두고 사용자와 확인함) — 정확히는 네 앱이 서로 다른 대상(가맹점 서버를 향한 결제 API, 내부 직원용 관리 콘솔, 가맹점 콘솔, 오프라인 배치 Job)을 상대해서 나중에 독립적으로 스케일·배포·장애가 나야 할 수 있어서다 — 이 선택을 끝까지 따른 결과로, `api-payment`와 역할이 겹치던 원래 Spring-Initializr 루트 앱도 다섯 번째 중복 배포 단위로 남겨두지 않고 삭제했다.

- **의존성은 각 앱이 지금 실제로 하는 일에만 맞춘다 — 나중에 할 일까지 미리 넣지 않는다.** `api-payment`/`api-admin`/`api-merchant`는 각각 실제 Use Case(`CreatePaymentUseCase`/`AuthenticateInternalUserUseCase`/`AuthenticateMerchantUserUseCase`)가 있어서 `modules:application`/`modules:infra-persistence`/`spring-boot-starter-jooq`/`DataSource`가 연결돼 있다. `batch`만 아직 `spring-boot-starter-batch`뿐이고 웹 스타터도, jOOQ/DataSource도 없다 — 실제 Job이 필요로 할 때만 넓힌다. 세 API 앱은 전부 `webmvc`+`security`도 갖고 있다(로그인/향후 인증 엔드포인트용). 실제 Use Case가 필요로 할 때만 그 앱의 의존성을 넓히고, 미리 넓히지 않는다.
- **포트**: `api-payment` 8081, `api-admin` 8082, `api-merchant` 8083; `batch`는 `server.port`가 없다(웹 앱이 아니다 — 웹 스타터 없는 `spring-boot-starter-batch`는 웹 서버 자동 구성을 스스로 끄고, `DataSource` Bean이 없으면 `BatchAutoConfiguration` 자체도 물러나서, 지금은 그냥 웹도 Job도 없는 앱으로 부팅된다).
- **컴포넌트 스캔**: `@SpringBootApplication`의 기본 스캔 범위는 메인 클래스 자신의 패키지와 그 하위 패키지다. 세 API 앱의 메인 클래스(`paytech.practice.pay.api.payment`/`api.admin`/`api.merchant`)는 전부 `modules:infra-persistence`의 Adapter(`paytech.practice.pay.infra.persistence.jooq`)와 *형제* 관계이지 상위가 아니다 — 그래서 셋 다 `@SpringBootApplication(scanBasePackages = [자기 패키지, "paytech.practice.pay.infra.persistence.jooq"])`로 두 패키지를 모두 명시한다. 새 앱이 다른 모듈의 Bean을 쓰기 시작하면, Gradle 의존성을 추가하는 것만으로 Bean이 연결된다고 가정하지 말고 같은 방식으로 스캔 범위를 넓힌다.
- `DataSource`가 있는 세 API 앱의 `application.yaml`은 `spring-boot-docker-compose` 자동 감지에 기대지 않고 `spring.datasource.*`를 `db-core`/`compose.yaml`이 이미 쓰는 것과 같은 로컬 개발 MySQL로 직접 가리킨다(`localhost:3306/stablecoin_payment`, `root`/`verysecret`) — 그 자동 감지 메커니즘은 실행 중인 앱 자신의 작업 디렉토리(예: `apps/api-payment/`)에서 `compose.yaml`을 찾지, `backend/`에서 찾지 않아서, 추가 경로 설정 없이는 공유 파일을 찾지 못한다.
- 테스트는 전부 같은 모양을 따른다(`@SpringBootTest` + Kotest `SpringExtension`, 앱마다 `contextLoads` 테스트 하나 — 위 "테스트" 참고). `DataSource`가 있는 세 API 앱은 만족시켜야 할 `DataSource`가 있어서 `TestcontainersConfiguration`도 추가로 import하고, `batch`는 아직 필요 없다.

### `api-payment`의 결제 생성 컨트롤러

`POST /api/v1/payments`(`docs/architecture/identity-access-api-key.md`의 "대표 사용 API")가 `CreatePaymentUseCase`를 HTTP로 노출하는 첫 inbound Adapter다. 패키지는 `api.payment.web`(컨트롤러/요청·응답 DTO/예외 핸들러), `api.payment.config`(Use Case를 Bean으로 조립하는 Composition Root), `api.payment.support`(아직 다른 모듈이 구현하지 않은 두 outbound port의 임시 구현)로 나눴다.

- **`UseCaseConfiguration`**: `CreatePaymentUseCase`는 `modules:application`에 있고 그 모듈은 Spring에 의존하지 않아서 `@Component`를 직접 달 수 없다 — 그래서 이 `@Configuration` 클래스가 outbound port Bean들을 주입받아 `@Bean` 메서드로 대신 조립한다. 앞으로 Use Case가 늘어나면 이 클래스에 `@Bean` 메서드를 추가한다(Use Case 하나마다 별도 Configuration 클래스를 만들 필요는 없다).
- **`IdGenerator`/`ExchangeRateProvider`의 구현이 없었다** — 둘 다 영속성 관심사가 아니라서 `modules:infra-persistence`가 구현하지 않았다. `support.UuidIdGenerator`(UUID 기반)와 `support.FakeExchangeRateProvider`(고정 환율, `docs/decisions/ADR-004-fake-exchange.md`의 Fake Exchange를 대표)를 이 앱 안에 직접 만들어 채웠다 — 둘 다 다른 앱이 필요로 하게 되면 그때 공유 위치로 옮길 수 있는, 지금은 이 정도로 충분한 임시 구현이라고 KDoc에 명시했다.
- **`PaymentApiExceptionHandler`**(`@RestControllerAdvice`)가 `application`/`domain` 예외를 HTTP 상태로 옮긴다: `MerchantNotFoundException` → 404, `MerchantCannotAcceptPaymentsException` → 409, Value Object의 `init { require(...) }` 검증 실패(`IllegalArgumentException`) → 400, `@Valid` 실패(`MethodArgumentNotValidException`) → 400. 이 매핑은 inbound Adapter의 책임이다 — Use Case나 Value Object는 HTTP를 전혀 모른다.
- **`merchantId`는 요청 본문이 아니라 인증된 `MerchantApiKey`에서 온다** — 아래 "`api-payment`의 API Key 인증" 참고. 처음 이 컨트롤러를 만들 때는 API Key 인증이 없어서 `merchantId`를 요청 본문에 직접 받는 임시 gap이 있었는데, 이제 해소됐다.
- **테스트**: `PaymentControllerTest`는 `@WebMvcTest(PaymentController::class)`로 웹 계층만 띄운다(DB 없음) — `CreatePaymentUseCase`는 `com.ninja-squad:springmockk`의 `@MockkBean`으로 Mock했다(위 "테스트" 참고). `@Autowired` 필드 주입이 필요해서 이 파일만 `FunSpec() { init { ... } }` 형태를 쓴다. 여기에 더해 실제 `bootRun` + `curl`로 시딩된 `mrc_test_001` 가맹점을 상대로 결제 생성 → 멱등 재요청(같은 `paymentId` 반환, 중복 행 없음) → DB 직접 조회까지 한 번 수동으로 검증했다(자동화된 테스트로 남기지는 않음).

### `api-payment`의 API Key 인증

`docs/architecture/identity-access-api-key.md`의 "6.4 저장 정책" 권장 흐름을 그대로 구현한다: `Authorization: Bearer sk_test_<prefixToken>_<secret>` 수신 → Prefix 추출 → Prefix로 후보 Key 조회 → 전체 Key를 서버 측 Pepper와 함께 해시 → `secret_hash` 비교 → 상태·환경·Merchant 상태 확인 → `last_used_at` 갱신. `AuthenticateInternalUserUseCase`/`AuthenticateMerchantUserUseCase`(자격증명 검증 → 신원 반환)와 같은 모양이지만, 로그인이 아니라 **보호된 요청마다** 실행된다는 점이 다르다 — 실패 잠금도 없다(사람이 타이핑하는 비밀번호가 아니라서).

- **API Key 형식**: `key_prefix`(예: `sk_test_ab12cd34`, `ApiKeyPrefix`의 KDoc 예시) 뒤에 `_<secret>`을 붙인 게 전체 Key다. `AuthenticateApiKeyUseCase.extractPrefix`는 `_`로 최대 4조각까지만 자른다(`split(limit = 4)`) — `secret`이 `_`를 포함해도 깨지지 않는다.
- **`ApiKeySecretHasher`를 `PasswordEncoder`와 의도적으로 분리했다** — 사람 비밀번호는 BCrypt 같은 느린 적응형 해시가 맞지만, API Key는 매 요청 검증이라 그럴 필요가 없다. 문서가 명시한 대로 `apps:api-payment`의 `HmacApiKeySecretHasher`가 HMAC-SHA-256 + 서버 측 Pepper로 구현한다. Pepper는 `application.yaml`의 `app.api-key.pepper`에서 오고, 지금 값은 `db-core`의 `verysecret` DB 비밀번호와 같은 성격의 로컬 개발용 평문 placeholder다 — 실제 배포 전 환경변수/Secret Manager로 옮겨야 한다. 해시 비교는 타이밍 공격을 막기 위해 `String.equals` 대신 `MessageDigest.isEqual`(상수 시간 비교)로 한다.
- **`MerchantApiKeyRepositoryAdapter`(`modules:infra-persistence`)는 이 프로젝트에서 처음으로 자식 컬렉션 테이블을 다루는 Adapter다.** `MerchantApiKey.scopes`는 `merchant_api_key_scope`(복합 PK, 자기 생명주기 없는 값 컬렉션)에 저장된다. 도메인에 Scope를 바꾸는 메서드가 없어서(발급 시 정해지면 끝) `save`의 INSERT 경로에서만 Scope 행을 쓰고, UPDATE 경로(`revoke`/`expire`/`recordUsage`)는 건드리지 않는다.
- **인증은 Filter가 한다, 컨트롤러가 아니다.** `ApiKeyAuthenticationFilter`(`OncePerRequestFilter`)가 `Authorization` 헤더를 읽어 매 요청 `AuthenticateApiKeyUseCase`를 부르고, 성공하면 이번 요청의 `SecurityContext`에 `UsernamePasswordAuthenticationToken(principal = ApiKeyPrincipal(merchantId, merchantApiKeyId), authorities = ["SCOPE_<ApiKeyScope>", ...])`를 심는다. 실패해도 예외를 던지지 않고 `SecurityContext`만 비운 채 다음 필터로 넘긴다 — 그 뒤 `authorizeHttpRequests`가 401/403을 결정한다.
- **`SecurityConfig`**: `POST /api/v1/payments`에 `hasAuthority("SCOPE_PAYMENT_CREATE")`를 요구한다. `SessionCreationPolicy.STATELESS`로 세션을 아예 안 만든다 — `apps:api-admin`/`apps:api-merchant`의 세션 쿠키 로그인과 근본적으로 다른 인증 방식이라서다. **여기서 CSRF를 끄는 건 admin/merchant처럼 "아직 안 켠 gap"이 아니라 애초에 필요 없다** — CSRF는 브라우저가 쿠키를 자동으로 실어 보내는 상황을 노리는 공격인데, 이 앱은 세션 쿠키를 쓰지 않는 순수 Bearer 토큰 인증이라 공격 대상 자체가 성립하지 않는다.
- **`ApiKeyAuthenticationEntryPoint`**가 인증 실패 401 응답을 `PaymentApiExceptionHandler`와 같은 `ErrorResponse` JSON 형식으로 통일한다 — 없으면 Spring Security 기본 엔트리 포인트가 다른 형식을 준다.
- **`PaymentController`는 `merchantId`를 `@AuthenticationPrincipal ApiKeyPrincipal`에서 받는다** — 요청 본문에는 더 이상 없다.
- **테스트**: `AuthenticateApiKeyUseCaseTest`(단위, 정상/형식 오류/Prefix 미존재/Secret 불일치/폐기/만료/`LIVE` 환경/Merchant 상태 불가를 전부 커버), `MerchantApiKeyRepositoryAdapterTest`(Testcontainers MySQL 통합, Scope 왕복까지 확인), `PaymentControllerTest`는 `@Import(SecurityConfig::class)`로 실제 인가 규칙까지 검증한다(`SecurityMockMvcRequestPostProcessors.authentication(...)`으로 `Authentication`을 직접 주입 — `authenticateApiKeyUseCase`는 `SecurityConfig`의 Bean 그래프를 만족시키기 위한 Mock일 뿐 실제로 호출되지 않는다). 여기에 더해 실제 `bootRun` + `curl`로 HMAC 해시를 미리 심어둔 테스트 Key를 상대로 헤더 없음(401) → Secret 틀림(401) → 정상 Key로 결제 생성(201, `last_used_at` 갱신 확인)까지 수동으로 검증했다.

**Spring Boot 4.1 / Jackson 3.x로 넘어오며 자주 걸리는 패키지 함정 두 가지**(둘 다 `apps:api-payment`에서 처음 부딪혔다):
- `ObjectMapper`는 `com.fasterxml.jackson.databind`가 아니라 **`tools.jackson.databind`**에 있다 — Jackson 3.x부터 그룹 ID/패키지가 `tools.jackson`으로 바뀌었다(`jackson-module-kotlin`도 `tools.jackson.module:jackson-module-kotlin`). 이 프로젝트의 루트 `build.gradle.kts` 의존성 목록에 이미 그 흔적이 있다.
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

- **발급 = `InternalUser(INVITED)` + `AccountInvitation(PENDING)`을 한 트랜잭션으로.** `docs/database/database-design.md`의 가맹점 등록 트랜잭션 예시(`Merchant + MerchantUser(OWNER, INVITED) + AccountInvitation`)와 같은 모양이다 — `IssueInternalUserUseCase`가 `InternalUser.invite(...)`와 `AccountInvitation.forInternalUser(...)`를 만들어 `TransactionManager.runInTransaction { }` 안에서 함께 저장한다(`CreatePaymentUseCase`와 같은 다중 Aggregate 생성 패턴). **초대를 수락해 비밀번호를 설정하고 `INVITED → ACTIVE`로 전이하는 흐름(활성화)은 아직 없다** — 로그인 흐름이 발급보다 먼저 별도로 구현됐던 것과 같은 이유로, 활성화는 별개의 후속 작업이다.
- **초대 Token은 저장하지 않고 Hash만 저장한다** — `AccountInvitation`의 KDoc과 그대로 일치한다. 원문 Token은 `IdGenerator.newId()`로 만든다(별도의 "랜덤 문자열 생성" Port를 새로 만들지 않고 기존 Port를 재사용했다). Hash는 새 Port `InvitationTokenHasher`(`hash`/`matches`, `ApiKeySecretHasher`와 완전히 같은 모양)로 만들고, `api-admin`의 `HmacInvitationTokenHasher`가 HMAC-SHA-256 + Pepper로 구현한다 — **API Key Pepper(`app.api-key.pepper`)와는 별도의 설정값(`app.invitation-token.pepper`)을 쓴다**, 한쪽 비밀값이 새도 다른 쪽까지 같이 위험해지지 않도록 하려는 의도적 분리다. `INVITATION_VALIDITY`(7일)는 `docs/`에 값이 없어 `CreatePaymentUseCase`의 `PAYMENT_VALIDITY`와 같은 성격의 MVP 상수로 고정했다. 응답의 `invitationToken`은 API Key 원문과 같은 규칙(`docs/`의 "6.4 저장 정책")으로 **이 응답에서만** 원문으로 보인다.
- **`loginId`/`email` 중복은 사전에 막는다.** 둘 다 `internal_user`의 DB Unique 제약(`uk_internal_user_login_id`/`uk_internal_user_email`)이 걸려 있어, 체크 없이 두면 raw SQL 에러가 새 나간다 — `InternalUserRepository`에 (기존 `findByLoginId`에 더해) `findByEmail`을 추가해서 둘 다 사전 조회하고, 겹치면 `DuplicateInternalUserException`(409)을 던진다. `CreatePaymentUseCase`의 멱등성 체크와 같은 성격의 한계다(DB Unique 제약만큼 원자적이지 않다).
- **호출자 식별을 위해 `InternalUserPrincipal`을 새로 도입했다.** `AdminLoginController`는 원래 `Authentication.principal`에 로그인 아이디 문자열만 심었는데, 발급 감사 정보(`createdByInternalUserId`)로 쓸 `InternalUserId`가 필요해서 `apps:api-payment`의 `ApiKeyPrincipal` 패턴을 그대로 가져와 `InternalUserPrincipal(internalUserId, loginId, role)`을 로그인 성공 시 principal로 심도록 `AdminLoginController`를 바꿨다. `InternalUserIssuanceController`는 `@AuthenticationPrincipal InternalUserPrincipal`로 발급자를 바로 받는다 — `PaymentController`가 `merchantId`를 요청 본문 대신 `ApiKeyPrincipal`에서 가져오는 것과 같은 이유다.
- **`SecurityConfig`에 역할 기반 인가가 처음 등장했다.** `authorize("/admin/internal-users", hasRole("SUPER_ADMIN"))`를 `anyRequest`보다 먼저 추가했다(Spring Security는 먼저 매칭되는 규칙을 쓴다). `SUPER_ADMIN`이 아닌 인증된 세션이 호출하면 Spring Security 기본 `AccessDeniedHandler`가 403을 돌려준다 — `apps:api-payment`의 Scope 인가(`PaymentControllerTest`의 403 케이스)와 같은 수준으로, 커스텀 JSON 바디를 만들지 않는다. 세션이 아예 없으면(로그인 안 함) 이 앱은 커스텀 `AuthenticationEntryPoint`가 없어서 Spring Security 기본 동작대로 403이 돈다(실제 `bootRun` + `curl`로 확인) — `api-payment`가 `ApiKeyAuthenticationEntryPoint`로 401 JSON 바디를 통일한 것과 달리, `api-admin`은 아직 이 부분을 커스텀하지 않았다.
- **예외 핸들러 이름을 바꿨다.** `AdminAuthExceptionHandler` → `AdminApiExceptionHandler`(로그인 전용이 아니게 됐으므로 `PaymentApiExceptionHandler`와 이름 패턴을 맞췄다) — `DuplicateInternalUserException`(409)과 `IllegalArgumentException`(400, Value Object `require()` 실패나 `InternalUserRole.valueOf()` 실패를 공통 처리, `PaymentApiExceptionHandler`와 완전히 같은 패턴)을 새로 추가했다.
- **`IdGenerator`가 `api-admin`에 처음 필요해져서** `apps:api-payment`의 `UuidIdGenerator`를 그대로 복제해 `api-admin`의 `support` 패키지에도 만들었다(각 앱이 자기 `support` 패키지에 자체 구현을 갖는 기존 관례를 따랐다 — 공유 모듈로 옮기지 않았다).
- **`AccountInvitationRepositoryAdapter`**(`modules:infra-persistence`)는 `account_invitation`에 `version` 컬럼이 없어서(`AccountInvitation`의 KDoc 참고) `InternalUserRepositoryAdapter`와 달리 낙관적 잠금 없이 단순 UPDATE로 상태 전이를 반영한다. 지금은 발급(INSERT)만 실제로 쓰이지만, Port 계약(`save`가 상태 전이도 반영해야 함)을 절반만 구현해 두지 않으려고 `accept`/`expire`/`revoke` 이후의 UPDATE 경로도 함께 만들어 뒀다(수락/만료/폐기 Use Case는 아직 없다).
- **테스트**: `IssueInternalUserUseCaseTest`(단위, 정상 발급/로그인 아이디 중복/이메일 중복), `AccountInvitationRepositoryAdapterTest`+`InternalUserRepositoryAdapterTest`의 `findByEmail` 케이스(Testcontainers MySQL 통합), `InternalUserIssuanceControllerTest`(`@WebMvcTest` + `@Import(SecurityConfig::class)`, `PaymentControllerTest`의 `SecurityMockMvcRequestPostProcessors.authentication(...)` 패턴으로 `InternalUserPrincipal`을 주입해 `SUPER_ADMIN`/`OPERATOR` 인가까지 검증). 여기에 더해 실제 `bootRun` + `curl`로 SUPER_ADMIN 로그인 → 발급(201, `invitationToken` 확인, DB에 `internal_user`+`account_invitation` 행 생성 확인) → 중복 loginId/email(둘 다 409) → 세션 없음(403) → 잘못된 role(400)까지 검증한 뒤 DB 행을 정리했다.
- **단위 테스트에서 걸린 함정: MockK의 `any()`가 값 클래스(Value Class)를 만들지 못할 수 있다.** `every { internalUserRepository.findByEmail(any()) } returns null`처럼 `Email` 타입 매개변수에 `any()`를 쓰면, MockK가 매처 서명을 만들려고 무작위 문자열로 `Email` 인스턴스를 생성하려 시도하는데 `Email`의 `init { require(value.contains("@")) }` 검증에 걸려 `IllegalArgumentException`이 난다(`LoginId`처럼 검증이 "공백 아님" 정도로 느슨한 값 클래스는 무작위 문자열이 통과해서 문제가 없다). 해결: `any()` 대신 실제 값(`findByEmail(EMAIL)`)으로 정확히 매칭한다 — 이런 종류의 값 클래스 매개변수에는 앞으로도 `any()`를 피한다.

### `api-merchant`의 가맹점 관리자 로그인 컨트롤러

`POST /merchant/login`(`docs/architecture/identity-access-api-key.md`의 "4.5 로그인 경로" 권장 경로)이 `AuthenticateMerchantUserUseCase`를 HTTP로 노출한다. `api-admin`의 로그인 컨트롤러와 거의 모든 게 같다(같은 패키지 구조, 같은 `SecurityConfig`/세션 쿠키 방식, 같은 CSRF-꺼짐 gap, 같은 잠금 정책 상수) — 차이만 적는다:

- **가맹점부터 특정해야 한다.** `login_id`는 가맹점 안에서만 유일하다(`merchant_seq + login_id`, "Idempotency keys" 참고) — `InternalUser`처럼 `loginId`만으로 계정을 찾을 수 없다. 그래서 `MerchantLoginRequest`/`AuthenticateMerchantUserCommand`는 `merchantCode`(사람이 읽는 가맹점 코드)를 함께 받고, Use Case가 `MerchantRepository.findByCode`로 가맹점을 먼저 확정한 다음 `MerchantUserRepository.findByMerchantIdAndLoginId`로 계정을 찾는다. 가맹점 코드가 틀려도 같은 `InvalidCredentialsException`을 던진다(가맹점 존재 여부도 노출하지 않는다) — 이걸 위해 `MerchantRepository` Port에 `findByCode`를 추가했다(기존엔 `findById`만 있었다).
- **가맹점 자체의 상태는 로그인 가능 여부에 영향을 주지 않는다.** `Merchant`가 `SUSPENDED`여도 그 가맹점의 관리자는 이유를 확인하러 로그인할 수 있어야 한다는 판단이다 — 문서에 명시된 규칙은 아니고, `AuthenticateMerchantUserUseCase`의 KDoc에 그렇게 남겨뒀다.
- **`MerchantUserRepositoryAdapter`**(`modules:infra-persistence`)는 `InternalUserRepositoryAdapter`와 같은 모양이지만 FK가 하나 더 있다 — `merchant_seq`(소속 가맹점)에 더해 `invited_by_internal_user_seq`/`invited_by_merchant_user_seq`(둘 다 nullable, 초대자 감사 정보)까지 resolve한다.

### IntelliJ HTTP Client(`.http` 파일)로 API 수동 테스트하기

각 `apps/*`에 `requests.http`가 있다(`apps/api-payment/requests.http`, `apps/api-admin/requests.http`, `apps/api-merchant/requests.http`) — IntelliJ가 인식하는 형식이다(에디터에서 열면 요청 옆에 ▶ 실행 아이콘이 뜬다). `backend/http-client.env.json`이 세 앱의 `baseUrl`과 로그인 아이디/비밀번호/API Key 같은 공용 변수를 "local" 환경으로 묶어 둔다 — 요청을 실행하기 전에 에디터 오른쪽 위에서 환경을 "local"로 고른다.

- **먼저 해당 앱을 띄운다**: `gradlew.bat :apps:api-payment:bootRun`처럼 `.http` 파일이 있는 앱을 실행해야 요청이 응답을 받는다.
- **자격증명은 `V4__seed_dev_identity_data.sql`이 심는다** — `db-core`가 처음부터 갖고 있던 `docker exec ... V1/V2/V3` 적용 순서에 이어서 이 파일도 같은 방식으로 적용해야 `.http` 파일의 요청들이 실제로 인증에 성공한다(아래 "Database / jOOQ code generation" 참고). 로그인 비밀번호는 `dev-admin`/`dev-owner` 둘 다 `DevPassword123!`이고, 결제 API Key는 `sk_test_devkey01_dev-secret-value`(scope: `PAYMENT_CREATE`+`PAYMENT_READ`)다 — 전부 로컬 개발 전용 값이고, 마이그레이션 파일 맨 위 주석에도 같은 내용이 있다.
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
docker exec -i backend-mysql-1 mysql --default-character-set=utf8mb4 -uroot -pverysecret stablecoin_payment < db-core/src/main/resources/db/migration/V1__init_schema.sql
docker exec -i backend-mysql-1 mysql --default-character-set=utf8mb4 -uroot -pverysecret stablecoin_payment < db-core/src/main/resources/db/migration/V2__seed_dev_data.sql
docker exec -i backend-mysql-1 mysql --default-character-set=utf8mb4 -uroot -pverysecret stablecoin_payment < db-core/src/main/resources/db/migration/V3__add_identity_access_and_merchant_api_key.sql
docker exec -i backend-mysql-1 mysql --default-character-set=utf8mb4 -uroot -pverysecret stablecoin_payment < db-core/src/main/resources/db/migration/V4__seed_dev_identity_data.sql
gradlew.bat :db-core:jooqCodegen                          # db-core/build/generated-src/jooq/main에 생성(gitignore 대상, 커밋하지 않음)
gradlew.bat :db-core:build                                 # jooqCodegen이 먼저 실행되고(compileKotlin.dependsOn으로 연결), 그다음 컴파일된다
```

- **마이그레이션은 지금 Flyway Gradle 플러그인이 아니라 수동으로 적용한다.** 공식 `org.flywaydb.flyway` 플러그인(최신 배포: 11.8.2)이 Gradle 9에서 제거된 Gradle API `JavaPluginConvention`을 여전히 호출해서, 이 프로젝트의 Gradle 9.5.1에서는 태스크가 그대로 실패한다(업스트림 미해결: https://github.com/flyway/flyway/issues/3798). `db-core/src/main/resources/db/migration/` 아래의 마이그레이션 파일들은 여전히 평범하고 번호가 올바르게 매겨진 Flyway 형식 SQL이다(`V1__init_schema.sql`, `V2__seed_dev_data.sql`, `V3__add_identity_access_and_merchant_api_key.sql`, `V4__seed_dev_identity_data.sql` — `V2`가 심는 `mrc_test_001` 가맹점에 실제로 로그인/API 호출을 해볼 수 있는 개발용 계정·API Key를 얹는다, 위 "IntelliJ HTTP Client" 참고) — 앱 모듈이 실제 `DataSource`를 갖게 되면 Spring Boot 자체의 Flyway 자동 구성(`spring-boot-starter-flyway`, 이 Gradle 플러그인과 무관함)이 자동으로 적용해줄 것이다. 여전히 깨져 있다고 가정하기 전에 더 최신 `flyway-gradle-plugin`이 고쳐졌는지 다시 확인한다.
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
