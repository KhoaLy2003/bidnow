# Design Spec: Winner Payment UI

**Date:** 2026-10-15
**Epic:** Wallet & Financial Management (Phase 1: mock payment gateway), frontend wiring
**Depends on:** WALLET-305 (winner payment), WALLET-306 (forfeit scheduler), Buy It Now (`2026-10-14-buy-it-now-design.md`)
**Status:** Implemented (uncommitted); manual browser verification pending

---

## Overview

A bidder who wins an auction, by bid or by Buy It Now, already gets a 48-hour payment hold from wallet-service, and `POST /api/v1/wallets/payments/confirm` already settles it. No frontend calls those endpoints: the "Proceed to payment" button in `BidPanelEnded` is a stub and nothing lists what a winner owes. This spec wires the winner-payment UI on the **existing mock wallet**. It is frontend only.

### Understanding (agreed in discussion)

- "Deposit money" means topping up the wallet through the existing mock `POST /api/v1/wallets/deposit` (instant credit), not the per-auction deposit lock.
- The deposit lock is taken on the first bid or on Buy It Now, and counts toward the price. The winner pays only the remainder.
- Success: a winner can see what they owe, top up if short, and pay, from the auction page or from the wallet page, without leaving the platform.
- Real payment gateway (VNPay), withdrawals and fulfilment are Phase 2 and out of scope.

---

## What already exists

| Piece | State |
|---|---|
| Hold creation | On `auction-ended-topic`: deposit applied, remainder moved to locked when available covers it (`fundsHeld`), deadline `now + 48h` |
| `GET /api/v1/wallets/payments/pending` | `PendingPaymentResponse { auctionId, totalAmount, depositApplied, remaining, fundsHeld, deadline, hoursLeft, expired }`; returns `PENDING_PAYMENT` holds only |
| `POST /api/v1/wallets/payments/confirm` | `{ auctionId }` → `ConfirmPaymentResponse { auctionId, amountPaid, depositApplied, remainingPaid, availableBalance, lockedBalance }`; errors `PAYMENT_DEADLINE_EXPIRED`, `INSUFFICIENT_BALANCE`, `PAYMENT_NOT_PENDING`, `PAYMENT_HOLD_NOT_FOUND`, `SELLER_WALLET_NOT_FOUND`, `DEPOSIT_LOCK_CLOSED`, `INVALID_INPUT` |
| Forfeit | `ForfeitScheduler` forfeits expired holds |
| Notifications | `PAYMENT_REQUIRED`, `PAYMENT_REMINDER`, `PAYMENT_RECEIVED`, `PAYMENT_FAILED` already exist as notification types |
| Frontend | `/wallet` (`WalletPanel`, mock top-up via `useWallet().depositFunds`), `/my-bids`, stub buttons in `BidPanelEnded` |

---

## Decisions

| # | Question | Decision |
|---|---|---|
| 1 | Scope | Frontend only, on the mock wallet. No backend, schema or gateway change. |
| 2 | Entry points | (a) Pay dialog opened from the auction page's "Proceed to payment"; (b) "Payments due" list on `/wallet`. |
| 3 | Auction title/image in the list | The frontend fetches each auction (title and image). No `auction_title` column in wallet-service. |
| 4 | State | One shared `paymentStore` + `usePendingPayments` hook read by both surfaces, so paying on one updates the other. |
| 5 | Top-up when short | Inline in the pay dialog, prefilled with the shortfall, using the existing mock deposit. |
| 6 | Time display | Show the server's `hoursLeft` and `expired`. Do not parse `deadline` against the browser clock: it is a timezone-less `LocalDateTime`. |

---

## Scope

### In scope

- Types, service methods, mapper, store and hook for pending payments and confirm
- `PayNowDialog` (price, deposit applied, remainder, time left, inline top-up, Pay)
- `PaymentsDueList` on `/wallet`
- Payment status block in `BidPanelEnded` (Won) replacing the stub button
- `lib/payment-errors.ts`
- Unit tests for pure modules; manual verification for components
- Doc updates listed below

### Out of scope

- Any backend, migration or gateway change (including a payment status on the auction detail)
- Real gateway (VNPay), withdrawals, platform fees
- "Contact seller" stub, shipping and delivery steps in `BidPanelEnded`
- Showing a paid auction as "Paid" after a page reload (see Known Limitations)

---

## Frontend Design

All amounts are dollars end to end; arithmetic and comparison go through `lib/money.ts`; display with `formatCurrency` in `font-mono`.

### Types, service, mapper

- `types/api/wallet.api.ts`: add `PendingPaymentResponse`, `ConfirmPaymentRequest`, `ConfirmPaymentResponse`, mirroring the backend DTOs.
- `services/wallet.service.ts`: add `getPendingPayments()` → `GET /api/v1/wallets/payments/pending` and `confirmPayment(auctionId)` → `POST /api/v1/wallets/payments/confirm`, using `apiFetch`. A non-2xx confirm response throws `PaymentRequestError(status, body)`, modelled on `BidRequestError`.
- `types/mappers/wallet.mapper.ts`: map `PendingPaymentResponse` to a UI `PendingPayment`.
- The UI `PendingPayment` omits `deadline`; the UI uses `hoursLeft` and `expired` only.

### State

- `store/paymentStore.ts`: fields `holds`, `paidIds` (session-local set of auction ids confirmed in this session), `isLoading`, `hasFetched`, `loadFailed`; actions `load`, `refresh` (reloads only if loaded before), `markPaid` (removes the hold, adds to `paidIds`), `reset`.
- `hooks/usePendingPayments.ts`: on mount it loads the store, or revalidates it in the background if it already holds data; `useUserNotifications.resync()` (run on mount, window focus and every STOMP reconnect) also calls `paymentStore.refresh()`, which does nothing if the store was never loaded; exposes `holds`, `paidIds`, `isLoading`, `hasFetched`, `loadFailed`, `refetch`. Live pushes are applied in `hooks/useUserNotifications.ts` (which already owns the push handler): a `PAYMENT_REQUIRED`, `PAYMENT_RECEIVED` or `PAYMENT_FAILED` push calls `paymentStore.refresh()`, which reloads only if the store was loaded before. The store is reset on logout/user change. After a successful pay, `PayNowDialog` calls `markPaid`, then refetches the wallet and refreshes the store.
- `lib/payment-utils.ts` (pure):
  - `getShortfall(hold, availableBalance)`: `null` when `hold.fundsHeld` or `available >= remaining`, otherwise `remaining − available` (cents arithmetic).
  - `derivePaymentPanelState({ holds, auctionId, paidIds, hasFetched, loadFailed, pollWindowOpen })` → `{ state: loading | error | preparing | due | expired | paid | none, hold }`.
  - `isRecentWin(completedAt)`: whether the auction completed within 2 minutes (or has no `completedAt` yet). `BidPanelEnded` opens the "preparing" window only for a recent win, so revisiting an old paid win never flashes "Preparing your payment…".

### Components

- `components/payment/PayNowDialog.tsx` (shadcn `Dialog`, as `BuyNowButton`):
  - Shows item title, total price, deposit applied, remainder to pay, `hoursLeft`.
  - When `getShortfall` is non-null: shows the shortfall, an amount field (the existing `CurrencyInput`, as in `WalletPanel`) prefilled with it, and an "Add funds" button that calls `depositFunds` then refetches the wallet. Pay is disabled until the shortfall clears.
  - Pay calls `confirmPayment`. While the request runs, Pay shows a spinner and the dialog cannot be dismissed.
  - Success: `markPaid`, wallet refetch, toast "Payment complete", close.
  - Failure: inline message via `describePaymentFailure`.
- `components/wallet/PaymentsDueList.tsx`: on `/wallet`, one row per hold: auction image and title (fetched per `auctionId`; a failed fetch falls back to "Auction" with a link), remainder, time left, Pay button that opens `PayNowDialog`. Hidden when there are no holds.
- `components/auction/BidPanelEnded.tsx` (Won): replaces the stub with a payment status block driven by `derivePaymentPanelState`:

| State | UI |
|---|---|
| `loading` | Skeleton |
| `error` | "We couldn't load your payment details." with Retry (only before the first successful load) |
| `preparing` | "Preparing your payment…". Refetch every 3 s for up to 30 s, then fall through to `none`. |
| `due` | Shows "Remaining to pay", the server's time left, and a "Proceed to payment →" button that opens `PayNowDialog` |
| `expired` | "Payment deadline passed" message |
| `paid` | "Payment complete" |
| `none` | "No payment due. Check your wallet history." with a link to `/wallet` |

The "Contact seller" stub and the shipping steps are left as they are.

### Data flow

1. The auction closes and wallet-service creates the hold. `PAYMENT_REQUIRED` is pushed to the winner and triggers a `paymentStore` refetch. The bounded poll in `BidPanelEnded` covers a missed push.
2. The winner opens the auction page or `/wallet` and sees the due payment from the shared store.
3. If `fundsHeld` is false and the balance is short, the dialog offers the inline mock top-up. If `fundsHeld` is true the money is already locked and there is no top-up step.
4. Pay → confirm. On success the hold leaves the store, the auction id enters `paidIds`, and both surfaces update.

### Errors (`lib/payment-errors.ts`)

`classifyPaymentError(err)` and `describePaymentFailure(failure)`, same split as `lib/bid-errors.ts`. `INSUFFICIENT_BALANCE` carries `required` and `availableBalance` as strings in `errors`.

| Backend code / status | Kind | UI |
|---|---|---|
| 401 | `unauthenticated` | Route to `/login` |
| `INSUFFICIENT_BALANCE` | `insufficient-balance` | Re-sync the balance and show the top-up; form stays enabled |
| `PAYMENT_DEADLINE_EXPIRED` | `expired` | Refetch, show the expired state; form disabled |
| `PAYMENT_NOT_PENDING`, `PAYMENT_HOLD_NOT_FOUND` | `no-longer-due` | Refetch, "this payment is no longer due"; form disabled |
| `SELLER_WALLET_NOT_FOUND`, `DEPOSIT_LOCK_CLOSED` | `account-problem` | "Contact support" message; form disabled |
| `INVALID_INPUT` | `invalid` | First error message |
| 503, `SERVICE_UNAVAILABLE`, network failure | `unavailable` | Show a retry message, and resync the wallet and pending payments (the request may have succeeded server-side) |
| anything else | `unknown` | Generic message, and resync the wallet and pending payments (the request may have succeeded server-side) |

---

## Testing

**Vitest (pure modules, existing style):**
- `lib/payment-errors.test.ts`: every code above, including `INSUFFICIENT_BALANCE` amount parsing.
- `services/wallet.service` payment methods: request shape and error mapping (as `bid.service.test.ts`).
- `store/paymentStore.test.ts`: set holds, `markPaid` removes the hold and records `paidIds`, reset.
- `types/mappers/wallet.mapper` pending-payment mapping.
- `lib/payment-utils.test.ts`: `getShortfall` (fundsHeld, exact balance, one cent short, cents rounding) and every `derivePaymentPanelState` transition.

**Manual (components, as `BuyNowButton`):**
- Pay with enough funds
- Pay when short: top up, then pay
- An expired hold
- A Buy It Now win leading to payment
- Paying on the auction page updates the `/wallet` list and vice versa
- Failure messages for expired and already-paid holds

---

## Documentation Updates

- `docs/diagrams/05-winner-payment-flow.md`: rewrite to the real flow (hold at close, `/api/v1/wallets/payments/*`, no platform fee, event-only auction sync, forfeit scheduler). It currently shows a 5% fee and a Feign status call that do not exist.
- `docs/epics/wallet/epic.md`: add a short section describing the frontend payment flow as built, and the known limitation below.
- No API surface, schema or architecture change, so `docs/architecture.md` and the database docs are untouched.

---

## Known Limitations (deliberate)

- After a page reload, a paid auction reads "No payment due" instead of "Paid": the pending endpoint returns only unpaid holds and wallet transactions expose no auction reference. Fixing it needs a backend change (for example a payment status on the auction or hold lookup), which is out of scope.
- "Preparing" and "paid or forfeited" are indistinguishable from the pending endpoint once the 30 s poll window ends; both fall through to `none`.
- The Payments due list issues one auction fetch per hold. The number of unpaid holds per user is expected to be small.
- Top-up uses the mock deposit, which credits instantly with no limits. Real gateway behaviour (redirect, IPN callback, pending deposits) is Phase 2.
- Auction-service still does not learn that the winner paid (`winner_paid_at`); this is an existing follow-up from WALLET-305.
