# Design Spec: Winner Payment Status

**Date:** 2026-10-15
**Epic:** Wallet & Financial Management (Phase 1), follow-up to WALLET-305 / WALLET-306 and `2026-10-15-winner-payment-ui-design.md`
**Status:** Implemented (uncommitted); manual browser verification pending

---

## Overview

After a winner pays, the frontend only knows it for the current browser session: the pending-payments endpoint returns unpaid holds only, so a reload (or a payment made in another tab, or a confirm that timed out but succeeded) leaves the Won panel on "No payment due". A missed deadline is worse: the panel stays "expired" and never says the deposit was forfeited.

This spec adds a small **read-only, authenticated payment-status endpoint to wallet-service** and uses it in the Won panel, so a paid, forfeited or cancelled sale shows correctly at any time. No auction-service change.

### Understanding (agreed in discussion)

You asked for auction-service to learn about payments (consume `PaymentEvent`, set `winner_paid_at` / `payment_deadline`, expose them in the detail API) so the winner payment UI can show the paid state. During brainstorming we agreed:

- Payment status is visible to the **winner and the seller only**, never through the anonymous public detail endpoint (`GET /api/v1/auctions/public/{id}` strips identity, so it cannot be per-viewer).
- The status is read from the **wallet-service**, which already records it authoritatively (`payment_holds.status`: `PENDING_PAYMENT`, `COMPLETED`, `CANCELLED`, `FORFEITED`), instead of copying it into auction-service. The UI therefore also shows **forfeited** and **cancelled**.
- **Winner panel only** in the UI. The endpoint authorizes the seller too, but no seller screen is built now.
- Success: after a reload, or after a payment made elsewhere, a winner sees the true state of the sale: "Payment complete", "deposit forfeited", or "cancelled".

---

## Approaches considered

| | A. Auction-service projection (the original ask) | **B. Read endpoint in wallet-service (chosen)** | C. Both |
|---|---|---|---|
| What | First Kafka consumer in auction-service sets `winner_paid_at` / `payment_deadline`; authenticated auction endpoint | `GET /api/v1/wallets/payments/{auctionId}` over `payment_holds` | B now, A later |
| Work | Consumer + config, `occurredAt` on shared `PaymentEvent`, endpoint | One repository method, one service method, one endpoint | As B |
| States | Pending and paid only (no forfeit column) | All four hold states | As B |
| Consistency | Eventual (Kafka lag) | Immediate, system of record | As B |
| Cost | Duplicates payment facts in a second database | Auction columns `winner_paid_at` / `payment_deadline` stay unused (superseded: see `2026-10-15-auction-payment-event-consumer-design.md`) | As B |

**Chosen: B.** Smaller, keeps payment facts in the service that owns them (`AGENTS.md`: respect microservice boundaries), and adds the forfeit and cancel states. A is worth revisiting only if auction-service needs the paid fact for its own features (admin lists, a seller dashboard); nothing does today.

---

## Decisions

| # | Question | Decision |
|---|---|---|
| 1 | Who can see payment status | Winner and seller of that auction. Anyone else gets the same 404 as "no hold". |
| 2 | Which service hosts it | wallet-service, `GET /api/v1/wallets/payments/{auctionId}` (route already authenticated by the gateway). |
| 3 | Response | `{ auctionId, status, totalAmount }` only. No timestamps (the hold's `completed_at` / `deadline` are timezone-less `LocalDateTime`), no deposit / remaining / `fundsHeld` (the pending endpoint serves the winner's due state; the seller must not see them). |
| 4 | Locking | None. A plain read; the existing pessimistic lookup must not be used. |
| 5 | UI scope | Winner panel only; no seller screen. |
| 6 | Where the UI keeps it | `paymentStore` (per-auction `statuses`), keeping `paidIds` for its separate job. |
| 7 | Auction-service | Unchanged. |

---

## Scope

### In scope

- wallet-service: `PaymentStatusResponse`, `PaymentHoldRepository.findByAuctionId`, `PaymentService.getPaymentStatus`, `GET /api/v1/wallets/payments/{auctionId}`, tests
- Frontend: API/UI types, `walletService.getPaymentStatus`, `paymentStore` status lookup, `derivePaymentPanelState` extension, `PaymentStatusBlock` states, tests
- Docs listed below

### Out of scope

- Any auction-service change, consumer, or event change (`PaymentEvent` is untouched)
- Seller-facing screens
- Timestamps or dates in the UI
- Any database migration
- Changing the pending / confirm endpoints

---

## Backend Design (wallet-service)

### Endpoint

`GET /api/v1/wallets/payments/{auctionId}`, caller identified with `@AuthenticatedUserId`. Annotated like its siblings (`@Operation`, tag "Winner Payments").

Response: `BaseResponse<PaymentStatusResponse>`

```
PaymentStatusResponse { UUID auctionId; PaymentHoldStatus status; BigDecimal totalAmount }
```

`status` is the existing enum serialized by name: `PENDING_PAYMENT | COMPLETED | CANCELLED | FORFEITED`.

### Authorization and errors

| Case | Result |
|---|---|
| Caller is the hold's winner or seller | 200 |
| Caller is anyone else | 404 `PAYMENT_HOLD_NOT_FOUND` (same as `confirmPayment` for someone else's hold, so existence is not leaked) |
| No hold for the auction (fresh win whose hold is not created yet, or no winner) | 404 `PAYMENT_HOLD_NOT_FOUND` |
| Malformed `auctionId` | 400, not 500 (see Risks) |
| No / invalid JWT | 401 from the gateway |

### Service

`PaymentService.getPaymentStatus(UUID callerUserId, UUID auctionId)`, `@Transactional(readOnly = true)`:

```
hold = paymentHoldRepository.findByAuctionId(auctionId)               -- new, no lock
         .filter(h -> caller == h.winnerUserId || caller == h.sellerUserId)
         .orElseThrow(NotFoundException(PAYMENT_HOLD_NOT_FOUND))
return PaymentStatusResponse { hold.auctionId, hold.status, hold.totalAmount }
```

`PaymentHoldRepository` gains `Optional<PaymentHold> findByAuctionId(UUID auctionId)` (derived query; `auction_id` is unique). It deliberately is not `findByAuctionIdForUpdate`: a read must not take a pessimistic write lock that would contend with confirm / cancel / forfeit.

### Risks

- **Route precedence:** `GET /payments/pending` is a literal path and `/payments/{auctionId}` a template; Spring picks the literal. A controller test must prove `/pending` still reaches the pending-payments handler.
- **Malformed id:** a non-UUID path value must yield 400. The plan must check whether `WalletExceptionHandler` (or the shared handler) maps `MethodArgumentTypeMismatchException`, and add the mapping if not.

### Tests

- `PaymentServiceImplTest`: winner gets each of the four statuses; seller gets the status; a stranger gets `PAYMENT_HOLD_NOT_FOUND`; a missing hold gets `PAYMENT_HOLD_NOT_FOUND`; the plain `findByAuctionId` is used and `findByAuctionIdForUpdate` never is.
- `PaymentControllerTest`: 200 body shape (`auctionId`, `status`, `totalAmount`); 404 error mapping; malformed id returns 400; `/pending` still routes to the pending handler.

---

## Frontend Design

All amounts stay dollars (`lib/money.ts`, `formatCurrency`). Builds on the code from `2026-10-15-winner-payment-ui-design.md`; the pending-payments flow is unchanged.

### Types and service

- `types/api/wallet.api.ts`: `PaymentHoldStatus = 'PENDING_PAYMENT' | 'COMPLETED' | 'CANCELLED' | 'FORFEITED'`, `PaymentStatusResponse { auctionId: string; status: PaymentHoldStatus; totalAmount: number }`.
- `types/ui/wallet.ui.ts`:

```ts
export type PaymentStatusLookup =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'found'; status: PaymentHoldStatus }
  | { kind: 'not-found' }
  | { kind: 'failed' }
```

- `walletService.getPaymentStatus(auctionId): Promise<PaymentStatusResponse | null>`: `GET /api/v1/wallets/payments/{auctionId}`; returns `null` on a 404 ("no hold for you"); any other non-2xx throws `PaymentRequestError`. Any 404 (no hold, not a participant, or for example a gateway 404) is treated as "no hold". An older wallet-service without the route does not answer 404: its shared catch-all handler returns 500, so wallet-service must be deployed first.

### Store

`store/paymentStore.ts` gains:

- `statuses: Record<string, PaymentStatusLookup>` (auction id to lookup; missing means `idle`).
- `loadStatus(auctionId)`: sets `loading`, calls the service, then writes `found` / `not-found` / `failed`. Latest-wins per auction (a superseded request must not overwrite a newer one); `reset()` invalidates in-flight requests and clears `statuses`.
- `markPaid(auctionId)` also records `found COMPLETED` for that auction.
- `paidIds` stays unchanged: it also stops a stale pending fetch from resurrecting a paid hold, a different job from the status lookup.

### When the lookup runs

`PaymentStatusBlock` calls `loadStatus(auctionId)` from an effect once the pending list has loaded (`hasFetched`) and this auction has **no** pending hold, and again whenever the hold disappears (deps: `auctionId`, `hasFetched`, whether a hold is present). It does not run on each poll tick. The effect calls a store action, not component `setState`, so it complies with the `react-hooks/set-state-in-effect` lint rule.

A hold disappearing covers: paying in another tab; a confirm that timed out but succeeded (the resync after `unknown` / `unavailable`); the forfeit scheduler running (pending drops the hold, the existing `PAYMENT_FAILED` push triggers `refresh()`, the lookup then reads `FORFEITED`).

### Panel state machine

`derivePaymentPanelState` gains the input `statusLookup: PaymentStatusLookup` and the output states `forfeited` and `cancelled`. Precedence:

1. `paidIds` has the auction → `paid`
2. a pending hold exists → `due` (or `expired` when the server flags it)
3. pending list not loaded → `loading`, or `error` if its first load failed
4. no hold, lookup `idle` or `loading` → `loading`
5. lookup `found`: `COMPLETED` → `paid`; `FORFEITED` → `forfeited`; `CANCELLED` → `cancelled`; `PENDING_PAYMENT` → `preparing` (race with the pending list; its poll picks the hold up)
6. lookup `not-found` → `preparing` while the recent-win window is open, otherwise `none`
7. lookup `failed` → `error`

| State | UI |
|---|---|
| `paid` | "Payment complete" (unchanged) |
| `forfeited` | "The payment deadline passed and your deposit was forfeited." with a link to `/wallet` |
| `cancelled` | "This sale was cancelled. Your deposit and any held funds were returned." with a link to `/wallet` |
| `error` | existing message; **Retry** now reloads both the pending list and the status |
| others | unchanged |

The Won panel only renders for an auction that completed with a winner, so `cancelled` will rarely appear there; it is handled because it is cheap and the status endpoint can return it.

### Tests

**Vitest (pure modules, existing style):**
- `walletService.getPaymentStatus`: 200 returns data; 404 returns `null`; 502 / non-JSON throws `PaymentRequestError`.
- `paymentStore`: `loadStatus` found / not-found / failed; latest-wins per auction; `reset` clears statuses and ignores an in-flight request; `markPaid` records `COMPLETED`; `paidIds` behaviour unchanged.
- `derivePaymentPanelState`: every new transition above, including precedence (paid marker beats lookup; a hold beats lookup) and `PENDING_PAYMENT` falling to `preparing`.

**Manual (components, as before):** reload after paying shows "Payment complete"; a forfeited hold shows the forfeit message; paying in a second tab flips the first tab to "Payment complete"; a fresh win goes preparing then due; a failed status load shows the error with a working Retry.

---

## Documentation Updates

- `docs/architecture.md`: the new wallet endpoint in the public API list.
- `docs/diagrams/05-winner-payment-flow.md`: the status read for a returning winner; drop the line saying auction-service does not learn about payment, replacing it with "payment status is read from wallet-service".
- `docs/epics/wallet/epic.md`: the frontend section's state list gains `forfeited` / `cancelled`; the known limitation is removed.
- `docs/superpowers/specs/2026-10-15-winner-payment-ui-design.md`: the first Known Limitation (paid reads "No payment due" after reload) is resolved by this spec; the `none` state now means only "no hold exists for this auction".
- No database or schema doc change (no migration).

---

## Rollout

Deploy wallet-service first, then the frontend. This order is mandatory: an older wallet-service answers the unmapped route with a 500 (the shared catch-all handler), which the new Won panel shows as its error state, for paid and fresh wins as well, until wallet-service is deployed. Rolling wallet-service back on its own has the same effect.

---

## Known Limitations (deliberate)

- A fresh win gets 404 until the Kafka-driven hold exists; the existing 3 s / 30 s "preparing" poll covers that window.
- `none` now means no hold exists for the auction (for example it predates wallet payments, or the hold-creation event was lost); it no longer hides paid auctions.
- The status endpoint returns no timestamps, so the UI shows states, not dates ("paid on ...").
- Auction-service's `winner_paid_at` and `payment_deadline` columns were unused when this shipped; they are now written by the payment event consumer (`2026-10-15-auction-payment-event-consumer-design.md`).
- No seller screen; the endpoint already authorizes the seller, so one is a frontend-only addition later.
