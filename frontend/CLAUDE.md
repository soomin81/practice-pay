# CLAUDE.md (frontend)

Guidance for working in `frontend/`. For the shared payment domain (aggregates, state machines, MVP scope) see the root `../CLAUDE.md` — this file covers frontend implementation conventions only.

## Current implementation state

No frontend project has been scaffolded here yet — `frontend/` is empty. There is no framework, build tool, or command to run yet.

Likely relevant when scaffolding starts, since this frontend will drive the customer-facing Hosted Checkout flow (per `docs/architecture/mvp-scope.md` and `docs/decisions/ADR-003-hosted-checkout.md`): `CheckoutSession` states (`CREATED → OPEN → WALLET_CONNECTED → PAYMENT_SUBMITTED → COMPLETED`, no cancel after `PAYMENT_SUBMITTED`) and `Payment` states drive the UI's screen progression — see the root `../CLAUDE.md` for the full state tables. Amounts must be handled as integers (KRW whole units, USDC minor units) to match the backend's `BIGINT` types — never use floating point for money.

## Commands

None yet. Once a project is scaffolded here, record build/dev/test/lint commands in this section.
