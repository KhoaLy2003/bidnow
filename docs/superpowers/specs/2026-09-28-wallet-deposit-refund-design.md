# WALLET-304 — Deposit Refund on Auction End / Cancel

**Issue:** [KhoaLy2003/bidnow#102](https://github.com/KhoaLy2003/bidnow/issues/102)
**Epic:** #49 — Wallet & Financial Management
**Depends on:** WALLET-303 (`deposit_locks`, `DepositLockService`) — `docs/superpowers/specs/2026-09-28-wallet-deposit-lock-design.md`
**Date:** 2026-09-28

---

## Scope

**In scope (wallet-service + one shared event DTO in `common`):**
- Kafka consumer for `auction-ended-topic` and `auction-cancelled-topic`
- `DepositLockService.releaseDeposit(...)` — per-lock, independent-transaction refund
- `DepositRefundedEvent` (common) published to `deposit-refunded-topic` after commit
- Wallet-service Kafka error handling: exponential backoff + dead-letter topic, and `ErrorHandlingDeserializer`
- Unit tests and doc updates

**Out of scope:**
- Any auction-service change (event payloads stay as they are)
- Media-service consumption of `DepositRefundedEvent` (notifications — later story)
- Winner's deposit: payment / forfeit (WALLET-305)
- Broker-level integration tests (no Testcontainers in wallet-service)

---

## Corrections to the Issue

| Issue says | Reality | Decision |
|---|---|---|
| Topic `auction-ended` | Auction-service publishes `auction-ended-topic` | Consume `auction-ended-topic` |
| Payload `{ auctionId, winnerId, loserIds[], depositAmount }` | `AuctionEndedEvent` has no `depositAmount`; `loserIds` is always `List.of()` (scheduler, TODO pending bidding-service) or `null` (admin force-close) | Ignore `loserIds` and `depositAmount`. Losers = all `LOCKED` `deposit_locks` rows for the auction except the winner's; amount = each lock's own `amount` |
| "retried via Kafka retry / DLQ" | No retry/DLQ config exists; Spring Kafka default = 9 immediate retries then log-and-drop | Add backoff + DLT in this story |
| Only auction end | Cancelled auctions also leave deposits locked | Also consume `auction-cancelled-topic`, release all locks |

---

## Decisions

| Topic | Decision |
|---|---|
| Who is refunded | Derived from `deposit_locks` (`status = LOCKED`, `auction_id`), excluding the winner's wallet on end; everyone on cancel |
| Refund amount | The lock row's `amount` |
| Transaction granularity | One DB transaction per lock (failure isolation) |
| Partial failure | Continue other locks, rethrow at end → record redelivered; already-released locks skip |
| Retry | `DefaultErrorHandler`, exponential backoff 1s → 2s → 4s (3 retries), then `DeadLetterPublishingRecoverer` → `<topic>.DLT` |
| Deserialization failures | `ErrorHandlingDeserializer` delegating to `JsonDeserializer` → DLT |
| Zero-amount lock | Status → RELEASED; no balance change, no REFUND transaction, no event |
| Suspended wallet | Refunded anyway |
| Outbound event | `DepositRefundedEvent { userId, walletId, auctionId, amount, reason, refundedAt }`, reason `AUCTION_LOST` / `AUCTION_CANCELLED`, topic `deposit-refunded-topic`, key `userId`, after commit |
| Winner's lock | Untouched (WALLET-305) |

---

## Components

### `common`

`com.bidnow.common.dto.event.DepositRefundedEvent` (Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`, same style as `DepositReceivedEvent`):

```java
UUID userId;
UUID walletId;
UUID auctionId;
BigDecimal amount;
String reason;        // AUCTION_LOST | AUCTION_CANCELLED
Instant refundedAt;
```

### wallet-service

| Unit | Responsibility |
|---|---|
| `domain/enums/RefundReason` | `AUCTION_LOST, AUCTION_CANCELLED` |
| `repository/DepositLockRepository` | + `List<DepositLock> findByAuctionIdAndStatus(UUID auctionId, DepositLockStatus status)` |
| `repository/WalletRepository` | + `@Lock(PESSIMISTIC_WRITE) findByIdForUpdate(UUID id)` |
| `service/DepositLockService` | + `void releaseDeposit(UUID lockId, UUID walletId, RefundReason reason)` |
| `kafka/DepositRefundedApplicationEvent` | In-process Spring event carrying refund data |
| `kafka/WalletEventPublisher` | + `onDepositRefunded` (`@TransactionalEventListener(AFTER_COMMIT)`) → Kafka |
| `kafka/AuctionLifecycleEventConsumer` | Two `@KafkaListener`s; sweeps locks and calls `releaseDeposit` per lock |
| `config/KafkaConsumerConfig` | `DefaultErrorHandler` bean (backoff + DLT recoverer) |
| `application.yml` | `ErrorHandlingDeserializer` wrapping `JsonDeserializer` |

---

## Flows

### Consumer (not transactional)

```
onAuctionEnded(AuctionEndedEvent e):
  releaseAll(e.auctionId, excludeUserId = e.winnerId, AUCTION_LOST)

onAuctionCancelled(AuctionCancelledEvent e):
  releaseAll(e.auctionId, excludeUserId = null, AUCTION_CANCELLED)

releaseAll(auctionId, excludeUserId, reason):
  locks = depositLockRepository.findByAuctionIdAndStatus(auctionId, LOCKED)
  if locks empty → log debug, return
  excludedWalletId = excludeUserId != null
      ? walletRepository.findByUserId(excludeUserId).map(Wallet::getId).orElse(null) : null
  failures = []
  for lock in locks where lock.walletId != excludedWalletId:
      try   depositLockService.releaseDeposit(lock.id, lock.walletId, reason)
      catch (RuntimeException ex) → log error (lockId, walletId, auctionId), failures += lock.id
  if failures non-empty → throw DepositReleaseException(auctionId, failures)
```

The consumer holds no transaction and Kafka listener threads have no open-in-view `EntityManager`, so the `DepositLock` rows it reads are detached; each `releaseDeposit` call starts a fresh persistence context.

### `releaseDeposit(lockId, walletId, reason)` — `@Transactional`

```
1. wallet = walletRepository.findByIdForUpdate(walletId)       -- row lock first (same order as lockDeposit)
     absent → log warn, return
2. lock = depositLockRepository.findById(lockId)                -- first load in this tx → fresh
     absent or status != LOCKED → return                        -- idempotent skip
3. if lock.amount > 0:
     before = wallet.available
     wallet.available += amount
     wallet.locked    -= amount                                 -- total unchanged
     save Transaction { type=REFUND, status=COMPLETED, amount,
                        availableBalanceBefore=before, availableBalanceAfter=wallet.available,
                        referenceId=auctionId,
                        description="Deposit refund for auction <id> (<reason>)" }
4. lock.status = RELEASED; lock.releasedAt = now
5. if lock.amount > 0: publish DepositRefundedApplicationEvent(userId, walletId, auctionId, amount, reason, now)
```

- **Lock ordering:** wallet row → deposit lock row, matching `lockDeposit`. No cycle, no deadlock.
- **Concurrent duplicate deliveries** (e.g. DLT replay while original retries) serialize on the wallet row; the second sees `RELEASED` and skips.
- **DB backstop:** `chk_balances_non_negative` and `chk_balance_invariant` on `wallets` reject any corrupting update → exception → retry → DLT.

### Error handling

`KafkaConsumerConfig`:

```java
@Bean
public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
    ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
    backOff.setInitialInterval(1_000L);
    backOff.setMultiplier(2.0);
    return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafkaTemplate), backOff);
}
```

Spring Boot injects a `CommonErrorHandler` bean into the auto-configured listener container factory, so every wallet listener (including `UserRegisteredEventConsumer`) gains it.

`application.yml` (`spring.kafka.consumer`):

```yaml
value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
properties:
  spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
  spring.json.trusted.packages: "*"
```

Dead-lettered records of deserialization failures carry the raw bytes; `JsonSerializer` writes them as a base64 JSON string. Acceptable for manual inspection/replay.

---

## Testing

JUnit 5 + Mockito.

**`DepositLockServiceImplTest` (release)**
- Happy path: available +50, locked −50, total unchanged; REFUND tx with before/after, `referenceId = auctionId`, COMPLETED; lock RELEASED with `releasedAt`; application event published with reason
- Lock RELEASED / FORFEITED → no saves, no event
- Lock missing → no saves, no event
- Wallet missing → no saves, no event
- Zero amount → lock RELEASED, no tx, no event, balances unchanged
- SUSPENDED wallet → still refunded
- Wallet read only via `findByIdForUpdate` (never `findByUserId`/`findById`)

**`AuctionLifecycleEventConsumerTest`**
- Ended with winner: winner's lock skipped, each loser's lock released with `AUCTION_LOST`
- Ended with `winnerId = null`: all locks released
- Cancelled: all locks released with `AUCTION_CANCELLED`
- No locks: `releaseDeposit` never called
- One `releaseDeposit` throws: remaining locks still released; listener throws `DepositReleaseException` after the loop
- `loserIds` content ignored (event with non-empty `loserIds` that don't match locks → locks drive behavior)

**`WalletEventPublisherTest`**
- `onDepositRefunded` sends to `deposit-refunded-topic`, key `userId`, payload fields mapped

**`KafkaConsumerConfigTest`**
- Error handler built with a `DeadLetterPublishingRecoverer` and backoff of 3 retries starting at 1s ×2

**Known gaps:** no broker-level test of redelivery/DLT routing or the `ErrorHandlingDeserializer` wiring; verified by manual smoke (publish a malformed record to `auction-ended-topic`, observe `auction-ended-topic.DLT`) when Docker is available.

---

## Documentation Updates

- `docs/architecture.md` — wallet consumes `auction-ended-topic`, `auction-cancelled-topic`; emits `deposit-refunded-topic`; DLT convention `<topic>.DLT`
- `docs/epics/wallet/epic.md` — refund flow: derived from `deposit_locks`, cancel also releases, event name/topic
- `AuctionClosureService` TODO about `loserIds` is left for bidding-service; wallet no longer depends on it (note in epic)

---

## Follow-ups (not this story)

- Media-service: consume `deposit-refunded-topic` for refund notifications
- WALLET-305: winner payment / forfeit
- Testcontainers Kafka + Postgres integration tests for retry/DLT and pessimistic locking
