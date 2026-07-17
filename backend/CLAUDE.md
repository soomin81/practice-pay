# CLAUDE.md (backend)

Guidance for working in `backend/`. For the shared payment domain (aggregates, state machines, MVP scope) see the root `../CLAUDE.md` — this file covers backend implementation conventions only.

## Current implementation state

`modules:domain`, `modules:application`, `modules:infra-persistence`, `db-core`, `architecture-tests`, and all four `apps:*` are real Gradle subprojects (see `settings.gradle.kts`); the rest are still empty placeholders not wired in. Don't assume code exists in the placeholder folders; check before referencing them, and re-verify this layout before relying on it since it has already been restructured more than once:

```
apps/
  api-payment/       real Gradle subproject, independently deployable Spring Boot app (own main class, own port) —
                     webmvc + jooq + depends on modules:application + modules:infra-persistence. The only app with
                     a real Use Case behind it so far (CreatePaymentUseCase); no controller wired to it yet.
  api-admin/         real Gradle subproject, independently deployable Spring Boot app — webmvc + security,
                     depends on modules:domain only (no InternalUser Use Case exists yet, so no jOOQ/DataSource either)
  api-merchant/      real Gradle subproject, independently deployable Spring Boot app — same shape as api-admin,
                     for MerchantUser/MerchantApiKey flows once those Use Cases exist
  batch/             real Gradle subproject, independently deployable Spring Boot app — spring-boot-starter-batch,
                     no web starter, no Job defined yet (future home for e.g. an OutboxEvent publisher worker)
modules/
  application/       real Gradle subproject, depends on domain; CreatePaymentUseCase (the payment-creation slice) + its outbound ports (see Architecture)
  common/            (placeholder)
  domain/            real Gradle subproject, no dependencies; all 8 payment aggregates + OutboxEvent + Identity/API key aggregates (see Domain code conventions)
  infra-blockchain/  (placeholder)
  infra-persistence/ real Gradle subproject — jOOQ Repository Adapters implementing modules:application's outbound ports (see Architecture)
db-core/             real Gradle subproject — Flyway migrations + jOOQ codegen (see below)
architecture-tests/  real Gradle subproject, test-only (no src/main) — ArchUnit rules over other modules' compiled classes
```

**The root project has no code of its own.** It was originally a Spring Initializr skeleton app (`PracticePayApplication.kt`, deleted) that became redundant once `apps/api-payment` took over as the real payment-API deployable — it was removed rather than kept as a duplicate. `backend/build.gradle.kts` now only holds the cross-cutting `allprojects {}` block (ktlint + `repositories {}` applied to every subproject); it doesn't apply the Kotlin or Spring Boot plugins to itself. Don't add source under `backend/src/` — add it to the relevant `apps:*` or `modules:*` subproject instead.

## Commands

Run from `backend/` (Windows: use `gradlew.bat`; the wrapper script `gradlew` is also present for POSIX shells). There's no single root app to build/run anymore — target the specific subproject:

```
gradlew.bat build                                        # full build, every subproject (compiles + runs tests)
gradlew.bat :apps:api-payment:bootRun                     # run a specific app locally (needs MySQL — see below)
gradlew.bat test                                          # run all tests, every subproject
gradlew.bat :apps:api-payment:test --tests "*PaymentApiApplicationTests.contextLoads"   # single test method, one subproject
gradlew.bat ktlintCheck                                   # lint every module (also runs as part of `check`/`build`)
gradlew.bat ktlintFormat                                  # auto-fix every module in place
```

- Local MySQL: `compose.yaml` defines a `mysql:latest` service for `docker compose up`, seeded with database `stablecoin_payment` (matches the schema — see "Database / jOOQ code generation" below). `apps:api-payment`'s tests instead use Testcontainers automatically (its own `TestcontainersConfiguration.kt` boots a MySQL container via `@ServiceConnection`); `apps:api-admin`/`apps:api-merchant`/`apps:batch` don't touch a database yet, so their tests need neither.
- Toolchain: Java 25, Kotlin 2.3.21, Spring Boot 4.1.0 (per `apps:*`/`db-core`/`modules:infra-persistence` subproject — the root project itself no longer applies either the Kotlin or Spring Boot plugin).
- Lint/format: **ktlint** via the `org.jlleitschuh.gradle.ktlint` plugin (14.2.0), applied to every module (including the phantom `:modules` parent Gradle creates for the hierarchical `modules:domain`/`modules:application` includes) via `allprojects {}` in the root `build.gradle.kts` — the one deliberate exception to this project's "duplicate small config per module" style, since ktlint config never varies per module. `backend/.editorconfig` pins `indent_style = tab` (this project's existing convention) so ktlint doesn't force a reformat to spaces. `ktlintCheck` already runs as part of `check`/`build`, so a green build implies lint-clean code. `db-core/build.gradle.kts` excludes `generated-src` (jOOQ-generated code, never hand-edited) from linting and adds an explicit `dependsOn("jooqCodegen")` on the ktlint tasks that read it, since Gradle's task-input validation requires a declared dependency on whatever produces a directory a task reads.

## Testing

- Test framework is **Kotest** (`FunSpec` style) — not JUnit5's `@Test`/`kotlin-test`. `gradlew.bat test` picks up Kotest specs automatically through `kotest-runner-junit5` on the JUnit Platform (`useJUnitPlatform()` is already set), no extra config needed.
- Spring context tests register `io.kotest.extensions.spring.SpringExtension` via `extensions(SpringExtension)` inside the spec body, alongside the usual `@SpringBootTest`/`@Import` annotations (see `apps/api-payment/src/test/kotlin/paytech/practice/pay/api/payment/PaymentApiApplicationTests.kt`).
- Mocking uses **MockK** (`io.mockk`), not Mockito.
- Assertions use `kotest-assertions-core` (`shouldBe`, etc.).
- Architecture rules (e.g. domain must not depend on Spring/jOOQ, the hexagonal layering below) are enforced with **ArchUnit** (`com.tngtech.archunit:archunit`), called from inside ordinary Kotest `test { }` blocks via `ClassFileImporter().importPackages(...)` + `.check(classes)` — not the separate `archunit-junit5` engine/`@AnalyzeClasses` style, to keep one test-writing convention (Kotest) for the whole project. Cross-module rules (checking one module's compiled classes from outside it) live in `architecture-tests` (test-only Gradle subproject; the module(s) under test are added as `testImplementation` deps — see `DomainPurityTest`).

## Architecture (hexagonal)

```
inbound adapter → application → domain ← outbound port ← outbound adapter
```

Planned module layering (see `docs/architecture/persistence-jooq.md`): `domain` → `application` → `adapter/outbound/persistence/jooq` → `generated-src/jooq`.

- Domain code must not depend on Spring, jOOQ, an HTTP client, or any blockchain SDK — it depends on nothing but plain Kotlin.
- Aggregates reference other aggregates by ID, never by object reference.
- **CQS (Command Query Separation)** at the method level: a method either changes state and returns nothing (a Command — e.g. `Payment.ready()`, `submit()`, `succeed()`) or returns data without side effects (a Query — e.g. reading `payment.status`), never both. Don't add a method that mutates state and also hands back a computed result.
- **CQRS at the persistence level**: Command repositories store/restore whole aggregates; complex reads go through dedicated jOOQ projections instead of the aggregate repository — see Persistence conventions.
- Every external system (blockchain RPC, exchange, webhook delivery) sits behind an outbound Port; adapters implement the port, never the reverse.
- State-transition rules live on the domain aggregate itself, never in a Controller or Repository.

### Application layer conventions (`modules:application`)

Established by the first use case, `CreatePaymentUseCase` (`application.payment`) — follow this shape for future use cases:

- **Outbound ports** live in `application.port.outbound` as plain Kotlin interfaces (or `fun interface` when the port has exactly one non-generic method, e.g. `IdGenerator`) — no Spring/jOOQ dependency, matching the domain-purity rule one layer up. One Command Repository port per aggregate (`save`/`findBy...`, matching "Command Repository는 Aggregate를 저장하고 복원한다"), plus supporting ports for cross-cutting concerns the use case needs but that aren't persistence (`ExchangeRateProvider`, `IdGenerator`, `TransactionManager`).
- **`TransactionManager`** (`fun <T> runInTransaction(block: () -> T): T`) is how a use case satisfies a documented multi-aggregate transaction boundary (the "트랜잭션 경계" section of `docs/architecture/persistence-jooq.md`) without the application layer depending on Spring's `@Transactional` or knowing which persistence framework is behind it. Reuse this port for the other two documented boundaries (payment completion, exchange completion) when those use cases are built — don't invent a bespoke bundled-repository port per use case instead.
- **A use case is a plain class with one `execute(command): result` method** — no separate inbound port interface, since nothing yet needs more than one implementation. `Command`/`Result` are small data classes named `<UseCaseName>Command`/`<UseCaseName>Result` in the same package. Returning an identifier (or other minimal data) from a creation command's `execute` is an accepted CQS exception at the use-case layer — the CQS rule above is about domain aggregate methods, not use-case entry points.
- **Idempotency checks** (see "Idempotency keys" below) happen at the start of `execute`, before any port write — look up by the documented key and short-circuit with the existing result if found. This is a best-effort fast path, not the final guarantee; the DB's own `UNIQUE` constraint is still the last line of defense against a race between two concurrent requests.
- Gaps `docs/` doesn't resolve yet (e.g. where a merchant's receiving wallet/network comes from) are taken as `Command` inputs for now rather than invented as new ports/tables — flagged in that `Command`'s KDoc so the simplification is easy to find and replace later.

### Persistence adapter conventions (`modules:infra-persistence`)

Established implementing the payment-creation slice's ports (`infra.persistence.jooq`, one subpackage per aggregate, e.g. `infra.persistence.jooq.payment`) — follow this shape for future adapters:

- Adapters are `@Repository`/`@Component` classes constructor-injected with a single `DSLContext`, so no manual bean wiring is needed inside the module itself — an app depending on `modules:infra-persistence` just needs its `@SpringBootApplication` component scan to actually reach `infra.persistence.jooq` (see the `apps/*` subsection below; `api-payment` depends on this module but doesn't widen its scan yet, so these beans aren't picked up there today).
- **jOOQ's generated table classes collide by name with several domain aggregates** (`Payment`, `Merchant`, `CheckoutSession`, `PaymentQuote`, `OutboxEvent` all exist as both a `paytech.practice.pay.dbcore.jooq.tables.*` class and a `paytech.practice.pay.domain.*` class). Every adapter resolves this the same way: import only the table's singleton constant via its companion (`import ...tables.Payment.Companion.PAYMENT`), never the table class itself — the class is never referenced by name, so there's nothing to collide with the domain import.
- `Instant` ↔ `LocalDateTime` conversion for `DATETIME(6)` UTC columns goes through the shared `toUtcLocalDateTime()`/`toUtcInstant()` extensions in `infra.persistence.jooq.InstantMapping.kt` — don't hand-roll `ZoneOffset.UTC` conversions per adapter.
- Columns with no domain equivalent (`payment.order_currency`, `payment_quote.quote_currency`) are filled with the hardcoded literal `"KRW"` at the adapter boundary — consistent with `Money` implicitly always meaning KRW everywhere else in this codebase (MVP only supports the KRW→USDC pair).
- **Known gap: `save()` on `Payment`/`CheckoutSession` (the two aggregates with a `version` optimistic-lock column) does not provide real optimistic-lock protection today.** The domain aggregates don't carry a `version` field (kept out deliberately to avoid leaking a persistence concern into the domain layer), so the adapter re-reads the current DB `version` immediately before updating and uses `current + 1` — this only guards against literal concurrent writes to the exact same adapter call, not "the aggregate was loaded from a stale version." Revisit this (most likely by threading an expected-version through the port, or fully embracing DB-side `SELECT ... FOR UPDATE`) when the first state-transition use case that re-saves an existing aggregate is built — today only `CreatePaymentUseCase` calls `save()`, and it always saves brand-new aggregates, so the gap has no live impact yet.
- **Testing**: `infra-persistence` has real MySQL integration tests (not mocks) — a Testcontainers MySQL instance shared across the test JVM run (`PersistenceTestSupport`), migrated with the `flyway-core` Java API directly (`Flyway.configure()...migrate()`), not the `org.flywaydb.flyway` Gradle plugin (that plugin is what's broken on Gradle 9.5.1 — see "Database / jOOQ code generation" below — the plain Java library has nothing to do with that breakage). The test `DSLContext` is wired exactly like Spring Boot's own `JooqAutoConfiguration` would (`DataSourceConnectionProvider` + `TransactionAwareDataSourceProxy` + `org.springframework.boot.jooq.autoconfigure.SpringTransactionProvider`, from the `spring-boot-jooq` module — Spring Boot 4.x moved jOOQ autoconfiguration out of `spring-boot-autoconfigure` into this dedicated module), so `TransactionManagerAdapterTest` can prove multi-repository writes actually roll back together.

### Apps (`apps:api-payment`, `apps:api-admin`, `apps:api-merchant`, `apps:batch`)

Each is an **independently deployable Spring Boot application** — its own `build.gradle.kts` applying the `org.springframework.boot` plugin, its own `@SpringBootApplication` main class, its own `application.yaml`, its own port — not just a package inside one shared app. This was a deliberate choice (confirmed with the user over the alternative of a modular monolith) precisely because the four apps serve different audiences (payment API for merchants' servers, admin console for internal staff, merchant console, and offline batch jobs) that may need to scale, deploy, and fail independently later — following through on that choice is also why the original Spring-Initializr root app (which duplicated `api-payment`'s role) was deleted rather than kept as a redundant fifth deployable.

- **Dependencies are scoped to what each app actually does today, not what it will eventually do.** `api-payment` is the only one with a real Use Case behind it (`CreatePaymentUseCase`) so it's the only one with `modules:application`/`modules:infra-persistence`/`spring-boot-starter-jooq`/a `DataSource` wired; `api-admin`/`api-merchant` only depend on `modules:domain` (their Identity/API-key aggregates exist, but no Use Case does yet) plus `webmvc`+`security` for their eventual login endpoints; `batch` only has `spring-boot-starter-batch`, no web starter. Widen a given app's dependencies only when a real Use Case needs them, not preemptively.
- **Ports**: `api-payment` 8081, `api-admin` 8082, `api-merchant` 8083; `batch` has no `server.port` (not a web app — `spring-boot-starter-batch` without a web starter backs off its web server autoconfiguration, and its `BatchAutoConfiguration` itself backs off with no `DataSource` bean present, so it currently boots as a plain non-web, job-less app).
- **Component scanning**: `@SpringBootApplication`'s default scan base package is the main class's own package and its sub-packages. `api-payment`'s main class lives in `paytech.practice.pay.api.payment`, which is a *sibling* of `modules:infra-persistence`'s adapters (`paytech.practice.pay.infra.persistence.jooq`), not an ancestor — so those `@Repository` beans are **not** picked up yet. Once a real controller needs them, either move to a shared root package prefix or set `@SpringBootApplication(scanBasePackages = [...])` explicitly; don't assume adding the Gradle dependency alone wires the beans in.
- `api-payment`'s `application.yaml` points `spring.datasource.*` directly at the same local dev MySQL `db-core`/`compose.yaml` already use (`localhost:3306/stablecoin_payment`, `root`/`verysecret`) rather than relying on `spring-boot-docker-compose` auto-detection — that mechanism looks for a `compose.yaml` in the running app's own working directory (`apps/api-payment/`), not `backend/`, so it wouldn't find the shared one without extra path configuration.
- Tests all follow the same shape (`@SpringBootTest` + Kotest `SpringExtension`, one `contextLoads` test per app — see "Testing" above); `api-payment` additionally imports a `TestcontainersConfiguration` since it has a `DataSource` to satisfy, the other three don't need one yet.

## Domain code conventions

All aggregates from `docs/domain/domain-model.md` are built: `Merchant`, `Payment`, `CheckoutSession`, `BlockchainTransaction`, `ExchangeOrder`, `SettlementReceivable`, `WebhookDelivery`, plus `PaymentQuote`, `OutboxEvent` (`domain.outbox`), and the identity/API key aggregates (`InternalUser`, `MerchantUser`, `AccountInvitation` in `domain.identity`; `MerchantApiKey` in `domain.apikey`). Follow the same shape for any future aggregate.

`Merchant` and `OutboxEvent` aren't covered by `docs/domain/state-transitions.md` — both have their transitions inferred directly from the DB schema (CHECK constraints + column shape), with that reasoning recorded in the respective status enum's KDoc rather than added to `docs/domain/state-transitions.md` itself (that file reflects reviewed business rules, not implementation-inferred ones).

- **Value Objects** wrap a single primitive as a Kotlin `@JvmInline value class`, validate in an `init { require(...) }` block, and carry KDoc explaining which DB column they map to and why the type exists (see `PaymentId`, `MerchantId`, `Money`, `TokenAmount`, `WalletAddress`, `Asset`, `BlockchainNetwork`, `MerchantOrderId`, `LoginId`, `Email`, `ApiKeyPrefix`, etc.). Reuse a VO across aggregates once a second one needs the identical concept (e.g. `WalletAddress`/`BlockchainNetwork`/`HttpUrl` live in `domain.shared`; `AccountStatus`/`LoginId`/`Email` live in `domain.identity` and are shared by `InternalUser` and `MerchantUser`) rather than duplicating it per-aggregate.
- **Aggregates** expose a `private` constructor plus two companion factories: `create(...)` (or a more specifically named creation factory, e.g. `Merchant.create`, `MerchantUser.inviteInitialOwner`/`inviteSubAccount`, `InternalUser.bootstrap`/`invite`) for a brand-new instance (fixes the initial state and defaults nullable fields to `null`), and `reconstitute(...)` for rebuilding from persisted values (every field explicit). Never expose a public constructor that lets a caller assemble an aggregate in an inconsistent state.
- **State-transition methods** are Commands (CQS): they validate the current state via a small private `checkTransition(allowed, target)` helper and throw `IllegalStateException` on an invalid transition, matching the signatures in `docs/domain/domain-model.md` exactly where documented (don't add parameters the docs don't have, e.g. `fail()` takes only `reason` and `failedAt`, not a message). Where a doc doesn't cover an aggregate's transitions at all (`Merchant`, and everything under "Identity & access" beyond the shared `AccountStatus` flow), the transitions are inferred from the schema's status CHECK constraint and column shape, with that reasoning recorded in the enum's KDoc.
- **Two deliberate exceptions to the create()/reconstitute() pattern**, both because the data shape doesn't support "new vs restored" as a meaningful distinction:
  - `PaymentQuote` is a plain `data class` with a public constructor — it's an immutable snapshot (no `status`, no transition methods; the `payment_quote` table has no `updated_at`/`version` either).
  - `AccountInvitation`'s transition methods (`expire()`, `revoke()`) take no timestamp parameter, unlike every other aggregate — the `account_invitation` table has no `updated_at`/`version` column to write one into.
- **This is a learning project (학습용 프로젝트)**: KDoc and `require`/`check` validation messages in domain code are written in **Korean**, even though identifiers stay in English. Explain the *why* (DB mapping, business rule, scope limits like "EIP-55 checksum 검증은 하지 않는다"), not just what the code does.

## Idempotency keys

Each of these enforces uniqueness/idempotency on the given key(s) — use them, don't invent parallel dedup logic:

| Entity | Key |
|---|---|
| Payment creation | `merchant_seq + merchant_order_id` |
| BlockchainTransaction | `network_code + transaction_hash` |
| ExchangeOrder | `client_order_id` |
| SettlementReceivable | `payment_seq` |
| WebhookDelivery | `event_id + merchant_seq` |
| OutboxEvent | `event_id` |
| InternalUser | `login_id` (also separately unique: `email`) |
| MerchantUser | `merchant_seq + login_id` (also separately unique: `merchant_seq + email`) |
| AccountInvitation | `token_hash` |
| MerchantApiKey | `key_prefix` |

## Database / jOOQ code generation

`db-core` owns the DB schema and generates jOOQ Kotlin code from a real MySQL instance. It is a real Gradle subproject using the **official** `org.jooq.jooq-codegen-gradle` plugin (jOOQ core is 3.21.5 via the Spring Boot 4.1.0 BOM; the codegen plugin's latest published release is 3.20.3 — a known one-minor-version lag between the plugin and jOOQ core, not a mismatch you introduced).

Workflow (from `backend/`):

```
docker compose up -d                                    # starts MySQL (db: stablecoin_payment, root pw: verysecret)
docker exec -i backend-mysql-1 mysql --default-character-set=utf8mb4 -uroot -pverysecret stablecoin_payment < db-core/src/main/resources/db/migration/V1__init_schema.sql
docker exec -i backend-mysql-1 mysql --default-character-set=utf8mb4 -uroot -pverysecret stablecoin_payment < db-core/src/main/resources/db/migration/V2__seed_dev_data.sql
docker exec -i backend-mysql-1 mysql --default-character-set=utf8mb4 -uroot -pverysecret stablecoin_payment < db-core/src/main/resources/db/migration/V3__add_identity_access_and_merchant_api_key.sql
gradlew.bat :db-core:jooqCodegen                          # generates into db-core/build/generated-src/jooq/main (gitignored, not committed)
gradlew.bat :db-core:build                                 # jooqCodegen runs first (wired via compileKotlin.dependsOn), then compiles
```

- **Migrations are applied manually for now, not via the Flyway Gradle plugin.** The official `org.flywaydb.flyway` plugin (latest published: 11.8.2) still calls the Gradle API `JavaPluginConvention`, which was removed in Gradle 9 — its tasks fail outright on this project's Gradle 9.5.1 (unresolved upstream: https://github.com/flyway/flyway/issues/3798). The migration files under `db-core/src/main/resources/db/migration/` are still plain, correctly-numbered Flyway-format SQL (`V1__init_schema.sql`, `V2__seed_dev_data.sql`, `V3__add_identity_access_and_merchant_api_key.sql`) — once the app module gets a real `DataSource`, Spring Boot's own Flyway autoconfiguration (`spring-boot-starter-flyway`, independent of this Gradle plugin) will apply them automatically. Re-check whether a newer `flyway-gradle-plugin` fixes this before assuming it's still broken.
- **Always pass `--default-character-set=utf8mb4` when applying a migration via the `mysql` CLI.** Without it, the CLI's default `latin1` client charset silently mangles the Korean `COMMENT`/seed text on the way into MySQL (the corruption happens on write, not on jOOQ's read side — this bit us once already; the DB had to be dropped and re-seeded to fix it).
- The `jooq { configuration { jdbc { url = ... } } }` URL also carries `useUnicode=true&characterEncoding=UTF-8` as cheap extra insurance for the codegen connection itself.
- `compileKotlin` does not automatically depend on `jooqCodegen`, and the official plugin does not automatically add its output directory to the Kotlin source set — both are wired explicitly in `db-core/build.gradle.kts` (`tasks.named("compileKotlin") { dependsOn("jooqCodegen") }` + `sourceSets { main { kotlin { srcDir(...) } } }`). Don't assume a fresh official-plugin setup wires these for you.
- Generated code lives under package `paytech.practice.pay.dbcore.jooq` and must only be consumed inside future persistence adapters (`modules/infra-persistence`), per the Persistence conventions below — never from `domain`/`application`.
- `jooqCodegen` prints (harmless) `Ambiguous key name` warnings for `internal_user` and `merchant_user` — both tables have more than one FK back to themselves/each other (`created_by_internal_user_seq`, `invited_by_internal_user_seq`, `invited_by_merchant_user_seq`), so jOOQ can't auto-name every implicit-join convenience accessor uniquely. The build still succeeds and the generated `Table`/`Record` classes are unaffected; only some optional path-navigation sugar methods are skipped.

## Persistence conventions (once implemented)

- MySQL 8.x, jOOQ only — **no JPA/Hibernate**. Generated jOOQ records are used only inside persistence adapters, never leaked to domain/application layers, and are converted via an explicit Mapper between Record and domain object (no implicit/reflective mapping).
- Command repositories store/restore whole aggregates; complex reads go through dedicated jOOQ projection queries instead of the aggregate repository.
- Optimistic locking: mutable-aggregate tables carry `version BIGINT`; UPDATEs condition on id + expected current status + expected version.
- Type mapping: KRW → `BIGINT`/`Money`, USDC → minor-unit `BIGINT`/`TokenAmount` (e.g. `72.992701 USDC = 72,992,701`), rates → `DECIMAL(24,12)`/`BigDecimal`, timestamps → `DATETIME(6)` UTC, status columns → `VARCHAR` backed by Kotlin enums (never MySQL `ENUM`, never `FLOAT`/`DOUBLE` for money or rates).
- Key transaction boundaries: payment creation groups `Payment + PaymentQuote + CheckoutSession + OutboxEvent`; payment completion groups `BlockchainTransaction + Payment(SUCCEEDED) + OutboxEvent`; exchange completion groups `ExchangeOrder(COMPLETED) + SettlementReceivable(READY) + OutboxEvent`.
- Async side effects (webhooks, events) go through the transactional outbox pattern (`outbox_event` table) rather than direct publish-in-transaction.
