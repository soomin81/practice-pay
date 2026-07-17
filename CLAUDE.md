# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

This is the **root/common** guidance file. It covers only what applies across the whole repo. For directory-specific commands and implementation conventions, see:

- `backend/CLAUDE.md` — Kotlin/Spring Boot backend (build/test commands, hexagonal architecture, jOOQ/MySQL conventions).
- `frontend/CLAUDE.md` — frontend (not yet scaffolded).

## Repository layout

This is a monorepo root with three top-level directories:

- `backend/` — the only directory with actual code so far (Kotlin/Spring Boot Gradle project).
- `frontend/` — currently empty, no project scaffolded yet.
- `docs/` — the design source of truth for this project (see "Documentation" below). Written in Korean.

## Documentation — read before implementing domain logic

`docs/README.md` indexes everything. Treat `docs/` as authoritative over any code assumptions since the domain hasn't been implemented yet:

- `docs/domain/glossary.md` — canonical term definitions (Korean/English). Read this first; the ADRs and domain model assume these terms.
- `docs/domain/domain-model.md` — aggregates, value objects, domain services, and the design rule that domain code must not depend on frameworks or infra (aggregates reference each other by ID only; state changes go through domain methods, not direct field assignment).
- `docs/domain/state-transitions.md` — the state machine for every aggregate (see summary below).
- `docs/architecture/mvp-scope.md` — what's in/out of the MVP and the end-to-end happy-path flow.
- `docs/architecture/persistence-jooq.md` — module layering and jOOQ conventions (backend-specific; see `backend/CLAUDE.md`).
- `docs/database/database-design.md` and `docs/database/schema.sql` — full MySQL schema design (not yet applied via migration tooling).
- `docs/decisions/ADR-00{1..5}-*.md` — ADRs for MVP scope, MySQL+jOOQ, Hosted Checkout, Fake Exchange, and settlement boundary.

## Domain: what this system is

A stablecoin (USDC) payment gateway MVP. A merchant's customer pays a KRW-denominated order by sending USDC from an external EVM wallet on **Base Sepolia (testnet)** through a PG-hosted checkout page; the PG detects and confirms the on-chain transfer, sells the USDC via a **Fake Exchange** (mocked, per ADR-004), and produces a settlement receivable in KRW. Real payout, settlement batching, AML/KYT, and refunds are explicitly out of MVP scope (ADR-001, ADR-005).

End-to-end flow:
```
Payment 생성 → PaymentQuote 확정 → CheckoutSession 생성 → 고객 지갑 연결
→ USDC 전송 → BlockchainTransaction 감지 및 Confirm → Payment SUCCEEDED
→ 결제 완료 페이지와 Webhook → Fake Exchange 매도 → SettlementReceivable READY
```
MVP is complete when `Payment = SUCCEEDED`, `ExchangeOrder = COMPLETED`, `SettlementReceivable = READY`.

### Aggregates and their state machines

| Aggregate | States |
|---|---|
| `Payment` (root) | `CREATED → READY → PROCESSING → CONFIRMING → SUCCEEDED`, with `EXPIRED` from `CREATED`/`READY` and `FAILED` from `PROCESSING`/`CONFIRMING` |
| `CheckoutSession` | `CREATED → OPEN → WALLET_CONNECTED → PAYMENT_SUBMITTED → COMPLETED` (no customer cancel after `PAYMENT_SUBMITTED`) |
| `BlockchainTransaction` | `SUBMITTED → DETECTED → CONFIRMING → CONFIRMED`, exception `FAILED`, future `REORGED` |
| `ExchangeOrder` | Fake Exchange (MVP): `REQUESTED → COMPLETED`. Real exchange (future): `REQUESTED → SUBMITTED → PROCESSING → COMPLETED` |
| `SettlementReceivable` | MVP: `PENDING → READY`. Future: `READY → ASSIGNED → SETTLED` |
| `WebhookDelivery` | `PENDING → DELIVERING → SUCCEEDED`, failure path via `RETRY_WAITING` up to a max retry count, then `FAILED` |

`Payment → SUCCEEDED` additionally requires: network/chain ID match, allowed token contract, receiving wallet match, sufficient amount, successful receipt, required confirmations met, and no duplicate transaction hash. Verification never trusts a token **symbol** alone to decide "this is USDC" — always verify by (network, contract address) together.

Other aggregates: `Merchant`, and the immutable `PaymentQuote` snapshot (market rate, applied rate, spread, amounts, validity window) attached 1:1 to a `Payment`.

General rule across all aggregates: state is validated before every transition, transitions are never done by direct field assignment from controllers/repositories, and terminal states are never reused.

### Core domain distinctions

- `Order` (merchant's product/service order) and `Payment` (PG's payment unit/aggregate root for that order) are distinct concepts — don't conflate them.
- `Settlement` (future merchant-level aggregation of `SettlementReceivable`s) and `Payout` (future actual KRW bank transfer) are **not implemented in MVP** — `SettlementReceivable` reaching `READY` is the MVP finish line (ADR-005).
- Never add a settlement/payout status to the `payment` record — settlement state lives only on `SettlementReceivable` and its future successors.

## Working process

Before implementing domain/state/DB/API/architecture work, check the relevant doc in `docs/` (see "Documentation" above) — it is authoritative. When a change touches domain rules, state machines, the DB schema, an API, or the architecture, update implementation, docs, migrations, and tests together in the same change — don't let `docs/` drift from code.

This repo is also used by other coding agents (e.g. Codex) against the same `docs/` source of truth — keep any agent-facing conventions consistent with what's documented, rather than inventing conflicting local rules.
