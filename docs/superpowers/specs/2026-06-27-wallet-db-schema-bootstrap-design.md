# Wallet Service — DB Schema, Bootstrap & Wallet Initialization

**Issue:** WALLET-301  
**Date:** 2026-06-27  
**Milestone:** v0.2-auction-wallet

---

## Overview

Bootstrap the `wallet-service` with its database schema, a Kafka consumer that auto-creates a wallet on user registration, a REST endpoint to retrieve the authenticated user's wallet, and a startup initializer that seeds the platform wallet.

---

## Scope

This issue covers one migration, one Kafka consumer, one REST endpoint, and one startup initializer. The `transactions`, `deposit_locks`, and `withdrawal_requests` tables are deferred to future issues.

---

## Database Schema

### Migration 01 — `wallets` table

File: `wallet-service/src/main/resources/db/changelog/migrations/01-init-wallets.sql`

```sql
-- liquibase formatted sql

-- changeset bidnow:wallet_001
CREATE TABLE wallets (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID UNIQUE NOT NULL,
    total_balance     NUMERIC(19,4) NOT NULL DEFAULT 0,
    available_balance NUMERIC(19,4) NOT NULL DEFAULT 0,
    locked_balance    NUMERIC(19,4) NOT NULL DEFAULT 0,
    currency          VARCHAR(3)    NOT NULL DEFAULT 'USD',
    status            VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_balances_non_negative
        CHECK (total_balance >= 0 AND available_balance >= 0 AND locked_balance >= 0),
    CONSTRAINT chk_balance_invariant
        CHECK (total_balance = available_balance + locked_balance)
);

CREATE INDEX idx_wallets_user_id ON wallets (user_id);
```

The `UNIQUE` constraint on `user_id` is the hard safety net for idempotent wallet creation. The `chk_balance_invariant` CHECK constraint enforces `total = available + locked` at the database level.

Master changelog (`db.changelog-master.xml`) includes this file:
```xml
<include file="db/changelog/migrations/01-init-wallets.sql"/>
```

---

## Domain & Repository

### `WalletStatus` enum

```
ACTIVE, SUSPENDED
```

`SUSPENDED` is a stub for future use; all wallets are created `ACTIVE`.

### `Wallet` entity

Extends `BaseEntity` (inherits `createdAt`/`updatedAt`). Key fields:

| Field              | Type          | Notes                        |
|--------------------|---------------|------------------------------|
| `id`               | `UUID`        | PK, generated                |
| `userId`           | `UUID`        | `UNIQUE NOT NULL`            |
| `totalBalance`     | `BigDecimal`  | Default 0                    |
| `availableBalance` | `BigDecimal`  | Default 0                    |
| `lockedBalance`    | `BigDecimal`  | Default 0                    |
| `currency`         | `String`      | Default `"USD"`              |
| `status`           | `WalletStatus`| `@Enumerated(STRING)`, ACTIVE |

### `WalletRepository`

`JpaRepository<Wallet, UUID>` with one custom finder:

```java
Optional<Wallet> findByUserId(UUID userId);
```

---

## Service Layer

### `WalletService` interface

```java
void createWalletIfAbsent(UUID userId);
WalletResponse getMyWallet(UUID userId);
```

### `WalletServiceImpl`

**`createWalletIfAbsent(UUID userId)`**
1. `walletRepository.findByUserId(userId)` — if present, return (idempotent).
2. Build `Wallet` with all balances `0`, currency `"USD"`, status `ACTIVE`.
3. `walletRepository.save(wallet)`.

The `UNIQUE` constraint on `user_id` is a hard backstop for the rare concurrent duplicate.

**`getMyWallet(UUID userId)`**
1. `walletRepository.findByUserId(userId)` — throws `ResourceNotFoundException` if absent.
2. Map `Wallet` → `WalletResponse` and return.

---

## Kafka Consumer

**`UserRegisteredEventConsumer`**

- Topic: `user-registered-topic` (matches `IdentityKafkaProducer.USER_REGISTERED_TOPIC`)
- Group ID: `${spring.kafka.consumer.group-id}`
- Event: `UserRegisteredEvent` (from `com.bidnow.common.dto.event`)
- Delegates to `walletService.createWalletIfAbsent(event.getUserId())`
- Annotated `@Transactional`

Pattern mirrors `UserKafkaConsumer` in `user-service`.

---

## Startup Initializer

**`WalletInitializer`**

Runs once after the application context is fully ready (`@EventListener(ApplicationReadyEvent.class)`), mirroring `AuctionStartupRecoveryService`.

Reads `wallet.platform-user-id` from `application.yml` (a known UUID representing the platform's system account). Calls `walletService.createWalletIfAbsent(platformUserId)`. Idempotent across restarts.

`application.yml` addition:
```yaml
wallet:
  platform-user-id: ${PLATFORM_USER_ID}
```

---

## REST API

### `GET /api/v1/wallet`

| Item         | Detail                                          |
|--------------|-------------------------------------------------|
| Auth         | Authenticated user (`@AuthenticatedUserId`)     |
| Controller   | `WalletController`                              |
| Success      | `200 BaseResponse<WalletResponse>`              |
| Not found    | `404` via `ResourceNotFoundException`           |

**`WalletResponse` fields:** `totalBalance`, `availableBalance`, `lockedBalance`, `currency`, `status`

### `SecurityConfig`

Permits `/api/v1/wallet/**` to authenticated users only. No public or internal paths for this issue.

---

## Idempotency

Both `UserRegisteredEventConsumer` and `WalletInitializer` call the same `createWalletIfAbsent`. The `findByUserId` guard handles the normal case; the DB `UNIQUE` constraint on `user_id` handles concurrent duplicates.

---

## Out of Scope

- `transactions`, `deposit_locks`, `withdrawal_requests` tables and their business logic (future issues)
- Deposit, withdrawal, escrow operations
- Admin wallet endpoints
