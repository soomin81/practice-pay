# CLAUDE.md (backend)

Guidance for working in `backend/`. For the shared payment domain (aggregates, state machines, MVP scope) see the root `../CLAUDE.md` — this file covers backend implementation conventions only.

## Current implementation state

The backend is still at the Spring Initializr skeleton stage. `src/main/kotlin/paytech/practice/pay/PracticePayApplication.kt` is the only application code. The following are empty placeholders for the planned multi-module architecture described below — none of them are wired into `settings.gradle.kts` yet, and `settings.gradle.kts` currently declares only the root project. Don't assume code exists in these folders; check before referencing them, and re-verify this layout before relying on it since it has already been restructured once:

```
apps/               api-admin, api-merchant, api-payment, batch
modules/
  application/
  common/
  domain/
  infra-blockchain/
  infra-persistence/
db-core/
architecture-tests/
```

## Commands

Run from `backend/` (Windows: use `gradlew.bat`; the wrapper script `gradlew` is also present for POSIX shells).

```
gradlew.bat build                              # full build (compiles + runs tests)
gradlew.bat bootRun                            # run the app locally (needs MySQL — see below)
gradlew.bat test                               # run all tests
gradlew.bat test --tests "paytech.practice.pay.PracticePayApplicationTests"   # single test class
gradlew.bat test --tests "*PracticePayApplicationTests.contextLoads"          # single test method
```

- Local MySQL: `compose.yaml` defines a `mysql:latest` service for `docker compose up`. Tests instead use Testcontainers automatically (`TestcontainersConfiguration.kt` boots a MySQL container via `@ServiceConnection`); `TestPracticePayApplication.kt` is a dev-time main that wires the same Testcontainers config for local `bootRun`-style use without a manual DB.
- Toolchain: Java 25, Kotlin 2.3.21, Spring Boot 4.1.0.
- No jOOQ code-generation Gradle plugin, Flyway, or Liquibase is configured yet, even though `docs/database/schema.sql` documents the target schema and the `jooq` starter is a dependency. Schema/codegen wiring is still to be done.
- No linter/formatter (ktlint/detekt) is configured yet.

## Testing

- Test framework is **Kotest** (`FunSpec` style) — not JUnit5's `@Test`/`kotlin-test`. `gradlew.bat test` picks up Kotest specs automatically through `kotest-runner-junit5` on the JUnit Platform (`useJUnitPlatform()` is already set), no extra config needed.
- Spring context tests register `io.kotest.extensions.spring.SpringExtension` via `extensions(SpringExtension)` inside the spec body, alongside the usual `@SpringBootTest`/`@Import` annotations (see `PracticePayApplicationTests.kt`).
- Mocking uses **MockK** (`io.mockk`), not Mockito.
- Assertions use `kotest-assertions-core` (`shouldBe`, etc.).
- Architecture rules (e.g. domain must not depend on Spring/jOOQ, the hexagonal layering below) are enforced with **ArchUnit** (`com.tngtech.archunit:archunit`), called from inside ordinary Kotest `test { }` blocks via `ClassFileImporter().importPackages(...)` + `.check(classes)` — not the separate `archunit-junit5` engine/`@AnalyzeClasses` style, to keep one test-writing convention (Kotest) for the whole project.

## Architecture (hexagonal)

```
inbound adapter → application → domain ← outbound port ← outbound adapter
```

Planned module layering (see `docs/architecture/persistence-jooq.md`): `domain` → `application` → `adapter/outbound/persistence/jooq` → `generated-src/jooq`.

- Domain code must not depend on Spring, jOOQ, an HTTP client, or any blockchain SDK — it depends on nothing but plain Kotlin.
- Aggregates reference other aggregates by ID, never by object reference.
- Command and Query responsibilities are separated (command repositories mutate/restore aggregates; reads go through dedicated jOOQ projections — see Persistence conventions).
- Every external system (blockchain RPC, exchange, webhook delivery) sits behind an outbound Port; adapters implement the port, never the reverse.
- State-transition rules live on the domain aggregate itself, never in a Controller or Repository.

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

## Persistence conventions (once implemented)

- MySQL 8.x, jOOQ only — **no JPA/Hibernate**. Generated jOOQ records are used only inside persistence adapters, never leaked to domain/application layers, and are converted via an explicit Mapper between Record and domain object (no implicit/reflective mapping).
- Command repositories store/restore whole aggregates; complex reads go through dedicated jOOQ projection queries instead of the aggregate repository.
- Optimistic locking: mutable-aggregate tables carry `version BIGINT`; UPDATEs condition on id + expected current status + expected version.
- Type mapping: KRW → `BIGINT`/`Money`, USDC → minor-unit `BIGINT`/`TokenAmount` (e.g. `72.992701 USDC = 72,992,701`), rates → `DECIMAL(24,12)`/`BigDecimal`, timestamps → `DATETIME(6)` UTC, status columns → `VARCHAR` backed by Kotlin enums (never MySQL `ENUM`, never `FLOAT`/`DOUBLE` for money or rates).
- Key transaction boundaries: payment creation groups `Payment + PaymentQuote + CheckoutSession + OutboxEvent`; payment completion groups `BlockchainTransaction + Payment(SUCCEEDED) + OutboxEvent`; exchange completion groups `ExchangeOrder(COMPLETED) + SettlementReceivable(READY) + OutboxEvent`.
- Async side effects (webhooks, events) go through the transactional outbox pattern (`outbox_event` table) rather than direct publish-in-transaction.
