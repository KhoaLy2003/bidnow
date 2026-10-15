# Design Spec: Auction Payment Event Consumer

**Date:** 2026-10-15
**Epic:** Auction Service / Wallet & Financial Management, the follow-up listed in `docs/epics/wallet/epic.md` (WALLET-305: "auction-service consumes `PaymentEvent`")
**Status:** Implemented (uncommitted); reviewed

---

## Overview

Wallet-service publishes a `PaymentEvent` on `payment-event-topic` for each step of a winner's payment. Only media-service consumes it (for notifications). Auction-service never learns whether the winner paid, so `auction_items.winner_paid_at` and `payment_deadline` stay `NULL` for ever, and an auction whose winner forfeits stays `COMPLETED`.

This spec adds the **first Kafka consumer in auction-service**. It projects the payment outcome onto the auction:

- `payment_deadline` is set when the payment is required.
- `winner_paid_at` is set when the winner pays.
- A forfeited sale moves the auction `COMPLETED → FAILED`.

No schema change, no API change, no new status. Wallet-service stays the system of record for payment state; the auction columns are a projection of it.

### Understanding (agreed in discussion)

- Auction-service should hold the payment outcome itself, so it no longer reads "sold" for a sale that was never paid.
- The status model is the **current** one (`DRAFT`, `SCHEDULED`, `ACTIVE`, `COMPLETED`, `FAILED`, `CANCELLED`, `REJECTED`). Nothing is renamed or removed.
- A forfeited sale becomes `FAILED`, as `docs/epics/wallet/epic.md` and `auction-service-schema.md` already describe. `FAILED` therefore covers two cases: no bids (`winnerId` is null) and an unpaid sale (`winnerId` is set).
- The existing `GET /api/v1/wallets/payments/{auctionId}` endpoint and the winner payment UI are unchanged.

---

## Approaches considered

| | **A. `FAILED` on forfeit, columns only (chosen)** | B. New `payment_status` column |
|---|---|---|
| Schema | None; uses `winner_paid_at`, `payment_deadline`, `FAILED` | New column and migration |
| API / UI | Already handle `FAILED` | New DTO field, mapper and UI work |
| Semantics | `FAILED` means two things, told apart by `winnerId` | One meaning per field |
| Fit | Matches the epic and schema doc | More than was asked for |

**Chosen: A.** B is the cleaner model if the two meanings of `FAILED` start to hurt (see Known Limitations).

---

## Decisions

| # | Question | Decision |
|---|---|---|
| 1 | Source | `payment-event-topic`, class `PaymentEvent` (from `common`), unchanged |
| 2 | `REQUIRED` | Set `paymentDeadline` from `event.deadline` if it is null |
| 3 | `COMPLETED` | Set `winnerPaidAt` to the consume time if it is null |
| 4 | `FAILED` | If `COMPLETED` and `winnerPaidAt` is null: status → `FAILED`, history row |
| 5 | Other types (`REMINDER_24H`, …) | Ignored |
| 6 | Paid timestamp | Consume time, from the injected `Clock`. `PaymentEvent` has no timestamp and none is added (YAGNI) |
| 7 | New event published | None |
| 8 | Wrong winner | The event's `userId` must equal the auction's `winnerId`, otherwise it is skipped |

---

## Design

### Consumer

`PaymentEventConsumer` in `auction/kafka/`:

```java
@KafkaListener(topics = "payment-event-topic", groupId = "${spring.kafka.consumer.group-id}")
public void onPaymentEvent(PaymentEvent event)
```

It only delegates to `AuctionPaymentService.applyPayment(event)`. Keeping the logic out of the listener makes it testable without Kafka and is how the closure and activation services are structured.

The wallet publishes with the winner's `userId` as the Kafka key, so all events for one sale share a partition and arrive in order.

### Service: `AuctionPaymentService.applyPayment`

`@Transactional`. It loads the auction with `findByIdForUpdate` (the row lock used by closure, bidding and admin cancel), so a payment event cannot race an admin action.

Guards, in order. A failed guard logs and returns without rethrowing (nothing to retry), except the `ACTIVE` one:

0. A null event, a null `auctionId`, or a `paymentType` other than `REQUIRED`, `COMPLETED` or `FAILED` → return before touching the database.
1. Auction not found → warn and return.
2. Status `ACTIVE` → **throw** `IllegalStateException`. This is a defensive guard: it should not happen, because every `AuctionEndedEvent` is published after its transaction commits (all publishers use `AfterCommit.run(...)` or an `afterCommit` hook). If it ever does, throw so the error handler retries (1s / 2s / 4s) before dead-lettering. This check comes before the winner check because an `ACTIVE` auction has no `winnerId` yet.
3. Status other than `COMPLETED` (`FAILED`, `CANCELLED`, …) → debug and return. So a replayed `FAILED`, a `COMPLETED` payment arriving after a forfeit, and anything on a cancelled auction are all no-ops.
4. `event.userId` is not `auction.winnerId` → warn and return.

Then, by `paymentType`:

| `paymentType` | Condition | Effect |
|---|---|---|
| `REQUIRED` | `paymentDeadline == null` | `paymentDeadline = event.deadline` (as `OffsetDateTime`) |
| `COMPLETED` | `winnerPaidAt == null` | `winnerPaidAt = now` |
| `FAILED` | status `COMPLETED` and `winnerPaidAt == null` | status → `FAILED`, history `COMPLETED → FAILED`, `triggeredBy` null, reason "Winner did not pay by the payment deadline" |
| anything else | n/a | ignored |

Replays are harmless: each effect is guarded by a null or status check. A `FAILED` event after a `COMPLETED` payment, or the reverse, is ignored because of the `winnerPaidAt` and status checks. `completedAt` is untouched on `FAILED`; it was set at closure.

No Kafka event is published for the `FAILED` transition (the wallet already refunds and notifies; nothing else listens). Revisit if media-service needs an "auction failed" notification: the wallet epic's forfeit story lists "seller: auction failed" as a later media-service story.

### Consumer configuration

Auction-service has only producer config today. Add, mirroring wallet-service:

- `application.yml`: `spring.kafka.consumer` with `group-id: auction-service-group`, `auto-offset-reset: earliest`, `StringDeserializer` keys, `ErrorHandlingDeserializer` wrapping `JsonDeserializer` for values, `spring.json.trusted.packages: "*"`, and the same reconnect backoff.
- `KafkaConsumerConfig` (`auction/config/`): a `DefaultErrorHandler` with `DeadLetterPublishingRecoverer` and exponential backoff 1s / 2s / 4s (three retries), then `<topic>.DLT`. It reuses auction-service's existing `KafkaTemplate<String, Object>`.

The error handler is applied to every listener in the service, which is fine because this is the only one.

### Failure behaviour

| Case | Result |
|---|---|
| Replay / duplicate event | No change |
| Event for an unknown auction or wrong user | Logged, skipped |
| Auction still `ACTIVE` (defensive; should not happen) | Retried 1s / 2s / 4s, then DLT |
| Undeserializable record | `ErrorHandlingDeserializer` routes it to the DLT |
| DB failure | Retried, then DLT |

---

## Scope

### In scope

- auction-service: `PaymentEventConsumer`, `AuctionPaymentService`, `KafkaConsumerConfig`, `application.yml` consumer block
- Tests (below)
- Docs (below)

### Out of scope

- Any wallet-service change
- New columns, statuses, DTO fields or UI (the frontend already handles `FAILED`)
- Publishing a new event on `FAILED`; changing media-service notifications
- Backfilling existing auctions (see Rollout)
- Telling the two kinds of `FAILED` apart in the seller UI

---

## Tests

JUnit and Mockito, as elsewhere in the service.

- `AuctionPaymentServiceTest`:
  - `REQUIRED` sets the deadline; a second `REQUIRED` does not overwrite it
  - `COMPLETED` sets `winnerPaidAt`; a replay keeps the first value
  - `FAILED` on `COMPLETED` moves to `FAILED` and saves a history row
  - `FAILED` after payment is ignored; `COMPLETED` after `FAILED` is ignored
  - wrong `userId`, unknown auction and `CANCELLED` auction are skipped without throwing
  - `ACTIVE` auction throws
  - an unknown `paymentType` changes nothing
- `PaymentEventConsumerTest`: delegates to the service.
- `KafkaConsumerConfigTest`: backoff values, as for wallet.
- BDD: skipped. The consumer's behaviour is covered at the unit level, and an end-to-end step would need an embedded Kafka that the auction-service features do not use today.

---

## Documentation Updates

- `docs/epics/wallet/epic.md`: mark the "auction-service consumes `PaymentEvent`" follow-up as done, with the mapping above.
- `docs/epics/auction/auction-service-schema.md`: describe how `winner_paid_at`, `payment_deadline` and `FAILED` are now written.
- `docs/diagrams/05-winner-payment-flow.md`: the auction-service step (replacing the "columns stay unused" note).
- `docs/superpowers/specs/2026-10-15-winner-payment-status-design.md`: its "columns stay unused" line now points here.

---

## Rollout

Deploy auction-service. With `auto-offset-reset: earliest` and a new group, the consumer replays the topic's retained history on first start. That is safe because every effect is idempotent and guarded by the winner and status checks, but it can move old forfeited sales to `FAILED`. Auctions whose events have already expired from the topic are not backfilled. The replay also re-reads retained `COMPLETED` events, so already-paid historical auctions get `winner_paid_at` = deploy time, not the real payment time (nothing reads `winner_paid_at` today).

---

## Known Limitations (deliberate)

- **`FAILED` has two meanings.** No bids, or an unpaid sale. They differ by `winnerId`, and the current seller UI shows the same "Failed" badge for both. Option B (a `payment_status` column) is the way out if this matters.
- **`winner_paid_at` is the consume time**, not the instant of payment. Normally the difference is milliseconds; under consumer lag it can be longer. The authoritative time is `payment_holds.completed_at` in wallet-service. On first start the new group also replays retained `COMPLETED` events, so already-paid historical auctions get `winner_paid_at` = deploy time (see Rollout).
- **The DLT is a dead end.** After the ~7s of retries a failed record is re-published to `payment-event-topic.DLT`, which nothing consumes and nothing reconciles. A database outage longer than the retry window therefore drops projection updates (a forfeited sale stays `COMPLETED`, `winner_paid_at` / `payment_deadline` stay null) until the DLT records are replayed manually. Wallet-service stays correct (it is the system of record).
- **Eventually consistent.** The auction reflects a payment after Kafka delivery; the wallet status endpoint stays the immediate source for the winner panel.
