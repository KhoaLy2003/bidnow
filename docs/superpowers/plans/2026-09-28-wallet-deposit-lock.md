# WALLET-303 Deposit Lock Internal API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give wallet-service three internal endpoints (check a deposit lock, take one idempotently, read balances) that the Bidding Service will call on a user's first bid per auction.

**Architecture:** A new `deposit_locks` table (one row per wallet+auction) acts as the implicit auction-registration record. `DepositLockServiceImpl.lockDeposit` takes a pessimistic row lock on the wallet (`SELECT … FOR UPDATE`) *before* reading anything else. It then checks for an existing lock, moves funds from available to locked, and writes a HOLD transaction plus the lock row, all in one DB transaction. `WalletInternalController` exposes this under `/api/v1/internal/wallet`. That path is permitted without auth in wallet-service and is never routed by the gateway.

**Tech Stack:** Java 17, Spring Boot 3.2.4, Spring Data JPA (Hibernate), Liquibase (formatted SQL), PostgreSQL, Lombok, JUnit 5, Mockito, AssertJ, standalone MockMvc.

**Spec:** `docs/superpowers/specs/2026-09-28-wallet-deposit-lock-design.md`

## Global Constraints

- Scope is wallet-service only. Do not touch bidding-service, auction-service or the api-gateway.
- Do not modify the `common` module.
- All endpoints return `ResponseEntity<BaseResponse<T>>`. Errors use the common `ErrorResponse`.
- Base path: `/api/v1/internal/wallet`.
- Error codes: `WALLET_NOT_FOUND` (404), `WALLET_NOT_ACTIVE` (403), `INSUFFICIENT_BALANCE` (400, `errors: {availableBalance, required}`), `DEPOSIT_LOCK_CLOSED` (409). Validation failures use the existing `INVALID_INPUT` (400).
- The deposit amount is trusted from the caller and validated only as `>= 0`.
- A zero deposit inserts a `LOCKED` row with amount 0, with no balance change and no HOLD transaction.
- Invariant `total_balance = available_balance + locked_balance` must hold after every write. Locking never changes `total_balance`.
- Never read the wallet without a lock before `findByUserIdForUpdate` in the same transaction, because Hibernate returns the cached, stale entity.
- **Do not run `git commit` (or `git add`) at any point.** Leave all changes uncommitted in the working tree on branch `feature/wallet-deposit-lock`; the user commits.
- Run Maven from `backend/`: `mvn -q -pl wallet-service -am test …` (`-am` builds `common` first).

---

## File Map

All paths are relative to `backend/wallet-service/`.

| File | Action | Responsibility |
|---|---|---|
| `src/main/resources/db/changelog/migrations/03-init-deposit-locks.sql` | Create | `deposit_locks` table |
| `src/main/resources/db/changelog/db.changelog-master.xml` | Modify | Include migration 03 |
| `src/main/java/com/bidnow/wallet/domain/enums/DepositLockStatus.java` | Create | `LOCKED, RELEASED, FORFEITED` |
| `src/main/java/com/bidnow/wallet/domain/entity/DepositLock.java` | Create | JPA entity |
| `src/main/java/com/bidnow/wallet/repository/DepositLockRepository.java` | Create | Lookup by wallet+auction |
| `src/main/java/com/bidnow/wallet/repository/WalletRepository.java` | Modify | Add `findByUserIdForUpdate` |
| `src/main/java/com/bidnow/wallet/constant/WalletErrorCodes.java` | Create | Wallet error-code constants |
| `src/main/java/com/bidnow/wallet/exception/InsufficientBalanceException.java` | Create | 400 exception carrying amounts |
| `src/main/java/com/bidnow/wallet/exception/ConflictException.java` | Create | 409 exception |
| `src/main/java/com/bidnow/wallet/exception/WalletExceptionHandler.java` | Create | Maps `InsufficientBalanceException` → `ErrorResponse.errors` |
| `src/main/java/com/bidnow/wallet/dto/request/DepositLockRequest.java` | Create | POST body |
| `src/main/java/com/bidnow/wallet/dto/response/DepositLockStatusResponse.java` | Create | GET deposit-lock payload |
| `src/main/java/com/bidnow/wallet/dto/response/DepositLockResponse.java` | Create | POST deposit-lock payload |
| `src/main/java/com/bidnow/wallet/dto/response/WalletBalanceResponse.java` | Create | GET balance payload |
| `src/main/java/com/bidnow/wallet/service/DepositLockService.java` | Create | Interface |
| `src/main/java/com/bidnow/wallet/service/impl/DepositLockServiceImpl.java` | Create | Lock flow |
| `src/main/java/com/bidnow/wallet/service/WalletService.java` | Modify | Add `getBalance` |
| `src/main/java/com/bidnow/wallet/service/impl/WalletServiceImpl.java` | Modify | `getBalance`; `deposit()` uses `findByUserIdForUpdate` |
| `src/main/java/com/bidnow/wallet/controller/WalletInternalController.java` | Create | Three internal endpoints |
| `src/main/java/com/bidnow/wallet/config/SecurityConfig.java` | Modify | permitAll `/api/v1/internal/**` |
| `src/test/java/com/bidnow/wallet/exception/WalletExceptionHandlerTest.java` | Create | Handler test |
| `src/test/java/com/bidnow/wallet/service/impl/DepositLockServiceImplTest.java` | Create | Service tests |
| `src/test/java/com/bidnow/wallet/service/impl/WalletServiceImplTest.java` | Modify | `getBalance` tests; deposit stubs |
| `src/test/java/com/bidnow/wallet/controller/WalletInternalControllerTest.java` | Create | HTTP-level tests |

Docs (repo root): `docs/diagrams/08-auction-registration-flow.md` and `docs/architecture.md`.

---

### Task 1: Persistence — migration, entity, repositories

**Files:**
- Create: `src/main/resources/db/changelog/migrations/03-init-deposit-locks.sql`
- Modify: `src/main/resources/db/changelog/db.changelog-master.xml`
- Create: `src/main/java/com/bidnow/wallet/domain/enums/DepositLockStatus.java`
- Create: `src/main/java/com/bidnow/wallet/domain/entity/DepositLock.java`
- Create: `src/main/java/com/bidnow/wallet/repository/DepositLockRepository.java`
- Modify: `src/main/java/com/bidnow/wallet/repository/WalletRepository.java`

**Interfaces:**
- Produces:
  - `enum DepositLockStatus { LOCKED, RELEASED, FORFEITED }`
  - `DepositLock` (Lombok builder): `UUID id, UUID walletId, UUID auctionId, BigDecimal amount, DepositLockStatus status, LocalDateTime lockedAt, LocalDateTime releasedAt`. It also inherits `createdAt`/`updatedAt` from `BaseEntity`.
  - `DepositLockRepository.findByWalletIdAndAuctionId(UUID walletId, UUID auctionId): Optional<DepositLock>`
  - `WalletRepository.findByUserIdForUpdate(UUID userId): Optional<Wallet>`, which takes a `PESSIMISTIC_WRITE` lock

This task is pure schema and mapping, with no business logic to unit-test. Verification is a compile plus a green run of the existing suite. The mapping is exercised by Tasks 3–5.

- [ ] **Step 1: Create the migration**

`src/main/resources/db/changelog/migrations/03-init-deposit-locks.sql`:

```sql
-- liquibase formatted sql

-- changeset bidnow:wallet_003
CREATE TABLE deposit_locks
(
    id          UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    wallet_id   UUID           NOT NULL REFERENCES wallets (id),
    auction_id  UUID           NOT NULL,
    amount      NUMERIC(19, 4) NOT NULL,
    status      VARCHAR(20)    NOT NULL,
    locked_at   TIMESTAMP      NOT NULL,
    released_at TIMESTAMP,
    created_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_deposit_locks_wallet_auction UNIQUE (wallet_id, auction_id),
    CONSTRAINT chk_deposit_locks_amount_non_negative CHECK (amount >= 0)
);

CREATE INDEX idx_deposit_locks_auction_status ON deposit_locks (auction_id, status);
```

- [ ] **Step 2: Register it in the changelog master**

In `src/main/resources/db/changelog/db.changelog-master.xml`, add the third include after the transactions line:

```xml
    <include file="db/changelog/migrations/01-init-wallets.sql"/>
    <include file="db/changelog/migrations/02-init-transactions.sql"/>
    <include file="db/changelog/migrations/03-init-deposit-locks.sql"/>
```

- [ ] **Step 3: Create the status enum**

`src/main/java/com/bidnow/wallet/domain/enums/DepositLockStatus.java`:

```java
package com.bidnow.wallet.domain.enums;

public enum DepositLockStatus {
    LOCKED, RELEASED, FORFEITED
}
```

- [ ] **Step 4: Create the entity**

`src/main/java/com/bidnow/wallet/domain/entity/DepositLock.java`:

```java
package com.bidnow.wallet.domain.entity;

import com.bidnow.common.entity.BaseEntity;
import com.bidnow.wallet.domain.enums.DepositLockStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "deposit_locks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepositLock extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "auction_id", nullable = false)
    private UUID auctionId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DepositLockStatus status;

    @Column(name = "locked_at", nullable = false)
    private LocalDateTime lockedAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;
}
```

- [ ] **Step 5: Create the repository**

`src/main/java/com/bidnow/wallet/repository/DepositLockRepository.java`:

```java
package com.bidnow.wallet.repository;

import com.bidnow.wallet.domain.entity.DepositLock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DepositLockRepository extends JpaRepository<DepositLock, UUID> {
    Optional<DepositLock> findByWalletIdAndAuctionId(UUID walletId, UUID auctionId);
}
```

- [ ] **Step 6: Add the locking finder to `WalletRepository`**

Replace the whole of `src/main/java/com/bidnow/wallet/repository/WalletRepository.java` with:

```java
package com.bidnow.wallet.repository;

import com.bidnow.wallet.domain.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    Optional<Wallet> findByUserId(UUID userId);

    /**
     * Loads the wallet with SELECT ... FOR UPDATE. Must be the first read of this wallet in the
     * transaction — an earlier unlocked read would leave a stale cached entity in the persistence context.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.userId = :userId")
    Optional<Wallet> findByUserIdForUpdate(@Param("userId") UUID userId);
}
```

- [ ] **Step 7: Compile and run the existing suite**

Run (from `backend/`): `mvn -q -pl wallet-service -am test`
Expected: BUILD SUCCESS, with all existing wallet-service tests passing.

---

### Task 2: Error codes, exceptions, and exception handler

**Files:**
- Create: `src/main/java/com/bidnow/wallet/constant/WalletErrorCodes.java`
- Create: `src/main/java/com/bidnow/wallet/exception/InsufficientBalanceException.java`
- Create: `src/main/java/com/bidnow/wallet/exception/ConflictException.java`
- Create: `src/main/java/com/bidnow/wallet/exception/WalletExceptionHandler.java`
- Test: `src/test/java/com/bidnow/wallet/exception/WalletExceptionHandlerTest.java`

**Interfaces:**
- Consumes: from `common`, `BadRequestException(String message, String errorCode)`, `BaseException(String message, String errorCode, HttpStatus status)` (protected), and `ErrorResponse` (builder with `status, errorCode, message, path, errors: Map<String,String>`).
- Produces:
  - `WalletErrorCodes.WALLET_NOT_FOUND`, `.WALLET_NOT_ACTIVE`, `.INSUFFICIENT_BALANCE`, `.DEPOSIT_LOCK_CLOSED` (String constants equal to their names)
  - `new InsufficientBalanceException(BigDecimal availableBalance, BigDecimal required)`, with getters `getAvailableBalance()` and `getRequired()`, and errorCode `INSUFFICIENT_BALANCE`
  - `new ConflictException(String message, String errorCode)`, which maps to HTTP 409
  - `WalletExceptionHandler.handleInsufficientBalance(InsufficientBalanceException, HttpServletRequest): ResponseEntity<ErrorResponse>`

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bidnow/wallet/exception/WalletExceptionHandlerTest.java`:

```java
package com.bidnow.wallet.exception;

import com.bidnow.common.dto.ErrorResponse;
import com.bidnow.wallet.constant.WalletErrorCodes;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class WalletExceptionHandlerTest {

    private final WalletExceptionHandler handler = new WalletExceptionHandler();

    @Test
    void handleInsufficientBalance_returns400WithAmountsInErrorsMap() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/internal/wallet/deposit-lock");
        InsufficientBalanceException ex =
                new InsufficientBalanceException(new BigDecimal("30.00"), new BigDecimal("50.00"));

        ResponseEntity<ErrorResponse> response = handler.handleInsufficientBalance(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(400);
        assertThat(body.getErrorCode()).isEqualTo(WalletErrorCodes.INSUFFICIENT_BALANCE);
        assertThat(body.getPath()).isEqualTo("/api/v1/internal/wallet/deposit-lock");
        assertThat(body.getErrors())
                .containsEntry("availableBalance", "30.00")
                .containsEntry("required", "50.00");
    }

    @Test
    void conflictException_hasConflictStatus() {
        ConflictException ex = new ConflictException("closed", WalletErrorCodes.DEPOSIT_LOCK_CLOSED);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getErrorCode()).isEqualTo("DEPOSIT_LOCK_CLOSED");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run (from `backend/`): `mvn -q -pl wallet-service -am test -Dtest=WalletExceptionHandlerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR, because `WalletExceptionHandler`, `InsufficientBalanceException`, `ConflictException` and `WalletErrorCodes` don't exist yet.

- [ ] **Step 3: Implement the error codes**

`src/main/java/com/bidnow/wallet/constant/WalletErrorCodes.java`:

```java
package com.bidnow.wallet.constant;

public final class WalletErrorCodes {
    public static final String WALLET_NOT_FOUND = "WALLET_NOT_FOUND";
    public static final String WALLET_NOT_ACTIVE = "WALLET_NOT_ACTIVE";
    public static final String INSUFFICIENT_BALANCE = "INSUFFICIENT_BALANCE";
    public static final String DEPOSIT_LOCK_CLOSED = "DEPOSIT_LOCK_CLOSED";

    private WalletErrorCodes() {
    }
}
```

- [ ] **Step 4: Implement the exceptions**

`src/main/java/com/bidnow/wallet/exception/InsufficientBalanceException.java`:

```java
package com.bidnow.wallet.exception;

import com.bidnow.common.exception.BadRequestException;
import com.bidnow.wallet.constant.WalletErrorCodes;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class InsufficientBalanceException extends BadRequestException {

    private final BigDecimal availableBalance;
    private final BigDecimal required;

    public InsufficientBalanceException(BigDecimal availableBalance, BigDecimal required) {
        super("Insufficient available balance: available=" + availableBalance.toPlainString()
                + ", required=" + required.toPlainString(), WalletErrorCodes.INSUFFICIENT_BALANCE);
        this.availableBalance = availableBalance;
        this.required = required;
    }
}
```

`src/main/java/com/bidnow/wallet/exception/ConflictException.java`:

```java
package com.bidnow.wallet.exception;

import com.bidnow.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class ConflictException extends BaseException {

    public ConflictException(String message, String errorCode) {
        super(message, errorCode, HttpStatus.CONFLICT);
    }
}
```

- [ ] **Step 5: Implement the handler**

It must run before the common `GlobalExceptionHandler`. Otherwise that handler's `BaseException` method would claim the exception and drop the amounts.

`src/main/java/com/bidnow/wallet/exception/WalletExceptionHandler.java`:

```java
package com.bidnow.wallet.exception;

import com.bidnow.common.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WalletExceptionHandler {

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalance(InsufficientBalanceException ex,
                                                                   HttpServletRequest request) {
        log.warn("Insufficient balance: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .errors(Map.of(
                        "availableBalance", ex.getAvailableBalance().toPlainString(),
                        "required", ex.getRequired().toPlainString()))
                .build();
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `mvn -q -pl wallet-service -am test -Dtest=WalletExceptionHandlerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (2 tests).

---

### Task 3: DepositLockService — check and lock flow

**Files:**
- Create: `src/main/java/com/bidnow/wallet/dto/request/DepositLockRequest.java`
- Create: `src/main/java/com/bidnow/wallet/dto/response/DepositLockStatusResponse.java`
- Create: `src/main/java/com/bidnow/wallet/dto/response/DepositLockResponse.java`
- Create: `src/main/java/com/bidnow/wallet/service/DepositLockService.java`
- Create: `src/main/java/com/bidnow/wallet/service/impl/DepositLockServiceImpl.java`
- Test: `src/test/java/com/bidnow/wallet/service/impl/DepositLockServiceImplTest.java`

**Interfaces:**
- Consumes (Task 1): `DepositLock`, `DepositLockStatus`, `DepositLockRepository.findByWalletIdAndAuctionId`, `WalletRepository.findByUserIdForUpdate`.
- Consumes (Task 2): `WalletErrorCodes`, `InsufficientBalanceException(BigDecimal, BigDecimal)`, `ConflictException(String, String)`.
- Consumes (existing): `Wallet`, `WalletStatus`, `Transaction`, `TransactionType.HOLD`, `TransactionStatus.COMPLETED`, `TransactionRepository`, `NotFoundException(String, String)`, `ForbiddenException(String, String)`.
- Produces:
  - `DepositLockRequest` (`@Data`, no-args + all-args constructor `(UUID userId, UUID auctionId, BigDecimal depositAmount)`)
  - `DepositLockStatusResponse` (`@Data @Builder`, `boolean locked, BigDecimal amount, String status, LocalDateTime lockedAt`; null fields omitted from JSON)
  - `DepositLockResponse` (`@Data @Builder`, `UUID lockId, BigDecimal amount, String status, boolean alreadyLocked, BigDecimal availableBalance, BigDecimal lockedBalance`)
  - `DepositLockService.getDepositLock(UUID userId, UUID auctionId): DepositLockStatusResponse`
  - `DepositLockService.lockDeposit(DepositLockRequest request): DepositLockResponse`

- [ ] **Step 1: Create the DTOs**

`src/main/java/com/bidnow/wallet/dto/request/DepositLockRequest.java`:

```java
package com.bidnow.wallet.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepositLockRequest {

    @NotNull(message = "userId is required")
    private UUID userId;

    @NotNull(message = "auctionId is required")
    private UUID auctionId;

    @NotNull(message = "depositAmount is required")
    @DecimalMin(value = "0.00", message = "depositAmount must be non-negative")
    private BigDecimal depositAmount;
}
```

`src/main/java/com/bidnow/wallet/dto/response/DepositLockStatusResponse.java`:

```java
package com.bidnow.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DepositLockStatusResponse {
    private boolean locked;
    private BigDecimal amount;
    private String status;
    private LocalDateTime lockedAt;
}
```

`src/main/java/com/bidnow/wallet/dto/response/DepositLockResponse.java`:

```java
package com.bidnow.wallet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class DepositLockResponse {
    private UUID lockId;
    private BigDecimal amount;
    private String status;
    private boolean alreadyLocked;
    private BigDecimal availableBalance;
    private BigDecimal lockedBalance;
}
```

- [ ] **Step 2: Create the service interface**

`src/main/java/com/bidnow/wallet/service/DepositLockService.java`:

```java
package com.bidnow.wallet.service;

import com.bidnow.wallet.dto.request.DepositLockRequest;
import com.bidnow.wallet.dto.response.DepositLockResponse;
import com.bidnow.wallet.dto.response.DepositLockStatusResponse;

import java.util.UUID;

public interface DepositLockService {

    DepositLockStatusResponse getDepositLock(UUID userId, UUID auctionId);

    DepositLockResponse lockDeposit(DepositLockRequest request);
}
```

- [ ] **Step 3: Write the failing tests**

`src/test/java/com/bidnow/wallet/service/impl/DepositLockServiceImplTest.java`:

```java
package com.bidnow.wallet.service.impl;

import com.bidnow.common.exception.ForbiddenException;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.wallet.constant.WalletErrorCodes;
import com.bidnow.wallet.domain.entity.DepositLock;
import com.bidnow.wallet.domain.entity.Transaction;
import com.bidnow.wallet.domain.entity.Wallet;
import com.bidnow.wallet.domain.enums.DepositLockStatus;
import com.bidnow.wallet.domain.enums.TransactionStatus;
import com.bidnow.wallet.domain.enums.TransactionType;
import com.bidnow.wallet.domain.enums.WalletStatus;
import com.bidnow.wallet.dto.request.DepositLockRequest;
import com.bidnow.wallet.dto.response.DepositLockResponse;
import com.bidnow.wallet.dto.response.DepositLockStatusResponse;
import com.bidnow.wallet.exception.ConflictException;
import com.bidnow.wallet.exception.InsufficientBalanceException;
import com.bidnow.wallet.repository.DepositLockRepository;
import com.bidnow.wallet.repository.TransactionRepository;
import com.bidnow.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepositLockServiceImplTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private DepositLockRepository depositLockRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private DepositLockServiceImpl depositLockService;

    private UUID userId;
    private UUID walletId;
    private UUID auctionId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        walletId = UUID.randomUUID();
        auctionId = UUID.randomUUID();
    }

    private Wallet wallet(String total, String available, String locked, WalletStatus status) {
        return Wallet.builder()
                .id(walletId)
                .userId(userId)
                .totalBalance(new BigDecimal(total))
                .availableBalance(new BigDecimal(available))
                .lockedBalance(new BigDecimal(locked))
                .currency("USD")
                .status(status)
                .build();
    }

    private DepositLock lock(String amount, DepositLockStatus status) {
        return DepositLock.builder()
                .id(UUID.randomUUID())
                .walletId(walletId)
                .auctionId(auctionId)
                .amount(new BigDecimal(amount))
                .status(status)
                .lockedAt(LocalDateTime.of(2026, 9, 28, 10, 0))
                .build();
    }

    private DepositLockRequest request(String amount) {
        return new DepositLockRequest(userId, auctionId, new BigDecimal(amount));
    }

    // ── getDepositLock ────────────────────────────────────────────────────────

    @Test
    void getDepositLock_noRow_returnsNotLocked() {
        when(walletRepository.findByUserId(userId))
                .thenReturn(Optional.of(wallet("200.00", "200.00", "0.00", WalletStatus.ACTIVE)));
        when(depositLockRepository.findByWalletIdAndAuctionId(walletId, auctionId)).thenReturn(Optional.empty());

        DepositLockStatusResponse result = depositLockService.getDepositLock(userId, auctionId);

        assertThat(result.isLocked()).isFalse();
        assertThat(result.getAmount()).isNull();
        assertThat(result.getStatus()).isNull();
    }

    @Test
    void getDepositLock_lockedRow_returnsLockedWithAmount() {
        DepositLock existing = lock("50.00", DepositLockStatus.LOCKED);
        when(walletRepository.findByUserId(userId))
                .thenReturn(Optional.of(wallet("200.00", "150.00", "50.00", WalletStatus.ACTIVE)));
        when(depositLockRepository.findByWalletIdAndAuctionId(walletId, auctionId)).thenReturn(Optional.of(existing));

        DepositLockStatusResponse result = depositLockService.getDepositLock(userId, auctionId);

        assertThat(result.isLocked()).isTrue();
        assertThat(result.getAmount()).isEqualByComparingTo("50.00");
        assertThat(result.getStatus()).isEqualTo("LOCKED");
        assertThat(result.getLockedAt()).isEqualTo(existing.getLockedAt());
    }

    @Test
    void getDepositLock_releasedRow_returnsNotLockedWithStatus() {
        when(walletRepository.findByUserId(userId))
                .thenReturn(Optional.of(wallet("200.00", "200.00", "0.00", WalletStatus.ACTIVE)));
        when(depositLockRepository.findByWalletIdAndAuctionId(walletId, auctionId))
                .thenReturn(Optional.of(lock("50.00", DepositLockStatus.RELEASED)));

        DepositLockStatusResponse result = depositLockService.getDepositLock(userId, auctionId);

        assertThat(result.isLocked()).isFalse();
        assertThat(result.getStatus()).isEqualTo("RELEASED");
    }

    @Test
    void getDepositLock_walletMissing_throwsNotFound() {
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> depositLockService.getDepositLock(userId, auctionId))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo(WalletErrorCodes.WALLET_NOT_FOUND);
    }

    // ── lockDeposit ───────────────────────────────────────────────────────────

    @Test
    void lockDeposit_firstBid_movesFundsAndRecordsHoldAndLock() {
        Wallet w = wallet("200.00", "200.00", "0.00", WalletStatus.ACTIVE);
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(w));
        when(depositLockRepository.findByWalletIdAndAuctionId(walletId, auctionId)).thenReturn(Optional.empty());
        when(depositLockRepository.saveAndFlush(any(DepositLock.class))).thenAnswer(inv -> {
            DepositLock saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        DepositLockResponse result = depositLockService.lockDeposit(request("50.00"));

        assertThat(w.getAvailableBalance()).isEqualByComparingTo("150.00");
        assertThat(w.getLockedBalance()).isEqualByComparingTo("50.00");
        assertThat(w.getTotalBalance()).isEqualByComparingTo("200.00");
        verify(walletRepository).save(w);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        Transaction hold = txCaptor.getValue();
        assertThat(hold.getWalletId()).isEqualTo(walletId);
        assertThat(hold.getType()).isEqualTo(TransactionType.HOLD);
        assertThat(hold.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(hold.getAmount()).isEqualByComparingTo("50.00");
        assertThat(hold.getAvailableBalanceBefore()).isEqualByComparingTo("200.00");
        assertThat(hold.getAvailableBalanceAfter()).isEqualByComparingTo("150.00");
        assertThat(hold.getReferenceId()).isEqualTo(auctionId);

        ArgumentCaptor<DepositLock> lockCaptor = ArgumentCaptor.forClass(DepositLock.class);
        verify(depositLockRepository).saveAndFlush(lockCaptor.capture());
        DepositLock savedLock = lockCaptor.getValue();
        assertThat(savedLock.getWalletId()).isEqualTo(walletId);
        assertThat(savedLock.getAuctionId()).isEqualTo(auctionId);
        assertThat(savedLock.getAmount()).isEqualByComparingTo("50.00");
        assertThat(savedLock.getStatus()).isEqualTo(DepositLockStatus.LOCKED);
        assertThat(savedLock.getLockedAt()).isNotNull();

        assertThat(result.isAlreadyLocked()).isFalse();
        assertThat(result.getLockId()).isEqualTo(savedLock.getId());
        assertThat(result.getStatus()).isEqualTo("LOCKED");
        assertThat(result.getAmount()).isEqualByComparingTo("50.00");
        assertThat(result.getAvailableBalance()).isEqualByComparingTo("150.00");
        assertThat(result.getLockedBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    void lockDeposit_takesRowLockAndNeverReadsWalletUnlocked() {
        Wallet w = wallet("200.00", "200.00", "0.00", WalletStatus.ACTIVE);
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(w));
        when(depositLockRepository.findByWalletIdAndAuctionId(walletId, auctionId)).thenReturn(Optional.empty());
        when(depositLockRepository.saveAndFlush(any(DepositLock.class))).thenAnswer(inv -> inv.getArgument(0));

        depositLockService.lockDeposit(request("50.00"));

        verify(walletRepository).findByUserIdForUpdate(userId);
        verify(walletRepository, never()).findByUserId(any());
    }

    @Test
    void lockDeposit_alreadyLocked_isIdempotentAndChangesNothing() {
        Wallet w = wallet("200.00", "150.00", "50.00", WalletStatus.ACTIVE);
        DepositLock existing = lock("50.00", DepositLockStatus.LOCKED);
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(w));
        when(depositLockRepository.findByWalletIdAndAuctionId(walletId, auctionId)).thenReturn(Optional.of(existing));

        DepositLockResponse result = depositLockService.lockDeposit(request("50.00"));

        assertThat(result.isAlreadyLocked()).isTrue();
        assertThat(result.getLockId()).isEqualTo(existing.getId());
        assertThat(result.getAvailableBalance()).isEqualByComparingTo("150.00");
        assertThat(result.getLockedBalance()).isEqualByComparingTo("50.00");
        assertThat(w.getAvailableBalance()).isEqualByComparingTo("150.00");
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verify(depositLockRepository, never()).saveAndFlush(any());
    }

    @Test
    void lockDeposit_insufficientBalance_throwsWithAmountsAndSavesNothing() {
        when(walletRepository.findByUserIdForUpdate(userId))
                .thenReturn(Optional.of(wallet("30.00", "30.00", "0.00", WalletStatus.ACTIVE)));
        when(depositLockRepository.findByWalletIdAndAuctionId(walletId, auctionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> depositLockService.lockDeposit(request("50.00")))
                .isInstanceOfSatisfying(InsufficientBalanceException.class, ex -> {
                    assertThat(ex.getAvailableBalance()).isEqualByComparingTo("30.00");
                    assertThat(ex.getRequired()).isEqualByComparingTo("50.00");
                });
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verify(depositLockRepository, never()).saveAndFlush(any());
    }

    @Test
    void lockDeposit_walletMissing_throwsNotFound() {
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> depositLockService.lockDeposit(request("50.00")))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo(WalletErrorCodes.WALLET_NOT_FOUND);
    }

    @Test
    void lockDeposit_zeroDeposit_insertsLockWithoutHoldOrBalanceChange() {
        Wallet w = wallet("0.00", "0.00", "0.00", WalletStatus.ACTIVE);
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(w));
        when(depositLockRepository.findByWalletIdAndAuctionId(walletId, auctionId)).thenReturn(Optional.empty());
        when(depositLockRepository.saveAndFlush(any(DepositLock.class))).thenAnswer(inv -> inv.getArgument(0));

        DepositLockResponse result = depositLockService.lockDeposit(request("0.00"));

        assertThat(result.isAlreadyLocked()).isFalse();
        assertThat(result.getAmount()).isEqualByComparingTo("0.00");
        verify(depositLockRepository).saveAndFlush(any(DepositLock.class));
        verify(transactionRepository, never()).save(any());
        verify(walletRepository, never()).save(any());
    }

    @Test
    void lockDeposit_releasedRow_throwsConflict() {
        when(walletRepository.findByUserIdForUpdate(userId))
                .thenReturn(Optional.of(wallet("200.00", "200.00", "0.00", WalletStatus.ACTIVE)));
        when(depositLockRepository.findByWalletIdAndAuctionId(walletId, auctionId))
                .thenReturn(Optional.of(lock("50.00", DepositLockStatus.RELEASED)));

        assertThatThrownBy(() -> depositLockService.lockDeposit(request("50.00")))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo(WalletErrorCodes.DEPOSIT_LOCK_CLOSED);
        verify(depositLockRepository, never()).saveAndFlush(any());
    }

    @Test
    void lockDeposit_forfeitedRow_throwsConflict() {
        when(walletRepository.findByUserIdForUpdate(userId))
                .thenReturn(Optional.of(wallet("150.00", "150.00", "0.00", WalletStatus.ACTIVE)));
        when(depositLockRepository.findByWalletIdAndAuctionId(walletId, auctionId))
                .thenReturn(Optional.of(lock("50.00", DepositLockStatus.FORFEITED)));

        assertThatThrownBy(() -> depositLockService.lockDeposit(request("50.00")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void lockDeposit_suspendedWalletWithoutLock_throwsForbidden() {
        when(walletRepository.findByUserIdForUpdate(userId))
                .thenReturn(Optional.of(wallet("200.00", "200.00", "0.00", WalletStatus.SUSPENDED)));
        when(depositLockRepository.findByWalletIdAndAuctionId(walletId, auctionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> depositLockService.lockDeposit(request("50.00")))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode").isEqualTo(WalletErrorCodes.WALLET_NOT_ACTIVE);
        verify(depositLockRepository, never()).saveAndFlush(any());
    }

    @Test
    void lockDeposit_suspendedWalletWithExistingLock_stillReturnsAlreadyLocked() {
        when(walletRepository.findByUserIdForUpdate(userId))
                .thenReturn(Optional.of(wallet("200.00", "150.00", "50.00", WalletStatus.SUSPENDED)));
        when(depositLockRepository.findByWalletIdAndAuctionId(walletId, auctionId))
                .thenReturn(Optional.of(lock("50.00", DepositLockStatus.LOCKED)));

        DepositLockResponse result = depositLockService.lockDeposit(request("50.00"));

        assertThat(result.isAlreadyLocked()).isTrue();
    }
}
```

- [ ] **Step 4: Run the tests to verify they fail**

Run (from `backend/`): `mvn -q -pl wallet-service -am test -Dtest=DepositLockServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR, because `DepositLockServiceImpl` doesn't exist yet.

- [ ] **Step 5: Implement the service**

`src/main/java/com/bidnow/wallet/service/impl/DepositLockServiceImpl.java`:

```java
package com.bidnow.wallet.service.impl;

import com.bidnow.common.exception.ForbiddenException;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.wallet.constant.WalletErrorCodes;
import com.bidnow.wallet.domain.entity.DepositLock;
import com.bidnow.wallet.domain.entity.Transaction;
import com.bidnow.wallet.domain.entity.Wallet;
import com.bidnow.wallet.domain.enums.DepositLockStatus;
import com.bidnow.wallet.domain.enums.TransactionStatus;
import com.bidnow.wallet.domain.enums.TransactionType;
import com.bidnow.wallet.domain.enums.WalletStatus;
import com.bidnow.wallet.dto.request.DepositLockRequest;
import com.bidnow.wallet.dto.response.DepositLockResponse;
import com.bidnow.wallet.dto.response.DepositLockStatusResponse;
import com.bidnow.wallet.exception.ConflictException;
import com.bidnow.wallet.exception.InsufficientBalanceException;
import com.bidnow.wallet.repository.DepositLockRepository;
import com.bidnow.wallet.repository.TransactionRepository;
import com.bidnow.wallet.repository.WalletRepository;
import com.bidnow.wallet.service.DepositLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepositLockServiceImpl implements DepositLockService {

    private final WalletRepository walletRepository;
    private final DepositLockRepository depositLockRepository;
    private final TransactionRepository transactionRepository;

    @Override
    @Transactional(readOnly = true)
    public DepositLockStatusResponse getDepositLock(UUID userId, UUID auctionId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> walletNotFound(userId));
        return depositLockRepository.findByWalletIdAndAuctionId(wallet.getId(), auctionId)
                .map(lock -> DepositLockStatusResponse.builder()
                        .locked(lock.getStatus() == DepositLockStatus.LOCKED)
                        .amount(lock.getAmount())
                        .status(lock.getStatus().name())
                        .lockedAt(lock.getLockedAt())
                        .build())
                .orElseGet(() -> DepositLockStatusResponse.builder().locked(false).build());
    }

    @Override
    @Transactional
    public DepositLockResponse lockDeposit(DepositLockRequest request) {
        UUID userId = request.getUserId();
        UUID auctionId = request.getAuctionId();
        BigDecimal amount = request.getDepositAmount();

        // Row lock first: concurrent first bids for this user serialize here.
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> walletNotFound(userId));

        Optional<DepositLock> existing = depositLockRepository.findByWalletIdAndAuctionId(wallet.getId(), auctionId);
        if (existing.isPresent()) {
            DepositLock lock = existing.get();
            if (lock.getStatus() != DepositLockStatus.LOCKED) {
                throw new ConflictException("Deposit lock for auction " + auctionId + " is already "
                        + lock.getStatus(), WalletErrorCodes.DEPOSIT_LOCK_CLOSED);
            }
            log.debug("Deposit already locked for userId={}, auctionId={}", userId, auctionId);
            return toResponse(lock, wallet, true);
        }

        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new ForbiddenException("Wallet is not active for userId: " + userId,
                    WalletErrorCodes.WALLET_NOT_ACTIVE);
        }
        if (wallet.getAvailableBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(wallet.getAvailableBalance(), amount);
        }

        if (amount.signum() > 0) {
            BigDecimal balanceBefore = wallet.getAvailableBalance();
            BigDecimal balanceAfter = balanceBefore.subtract(amount);
            wallet.setAvailableBalance(balanceAfter);
            wallet.setLockedBalance(wallet.getLockedBalance().add(amount));
            walletRepository.save(wallet);

            transactionRepository.save(Transaction.builder()
                    .walletId(wallet.getId())
                    .type(TransactionType.HOLD)
                    .amount(amount)
                    .availableBalanceBefore(balanceBefore)
                    .availableBalanceAfter(balanceAfter)
                    .referenceId(auctionId)
                    .status(TransactionStatus.COMPLETED)
                    .description("Deposit lock for auction " + auctionId)
                    .build());
        }

        // saveAndFlush so a unique-constraint violation surfaces here, not at commit.
        DepositLock lock = depositLockRepository.saveAndFlush(DepositLock.builder()
                .walletId(wallet.getId())
                .auctionId(auctionId)
                .amount(amount)
                .status(DepositLockStatus.LOCKED)
                .lockedAt(LocalDateTime.now())
                .build());

        log.info("Deposit locked for userId={}, auctionId={}, amount={}", userId, auctionId, amount);
        return toResponse(lock, wallet, false);
    }

    private DepositLockResponse toResponse(DepositLock lock, Wallet wallet, boolean alreadyLocked) {
        return DepositLockResponse.builder()
                .lockId(lock.getId())
                .amount(lock.getAmount())
                .status(lock.getStatus().name())
                .alreadyLocked(alreadyLocked)
                .availableBalance(wallet.getAvailableBalance())
                .lockedBalance(wallet.getLockedBalance())
                .build();
    }

    private NotFoundException walletNotFound(UUID userId) {
        return new NotFoundException("Wallet not found for userId: " + userId, WalletErrorCodes.WALLET_NOT_FOUND);
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `mvn -q -pl wallet-service -am test -Dtest=DepositLockServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (14 tests).

---

### Task 4: Balance lookup and deposit() row-lock hardening

**Files:**
- Create: `src/main/java/com/bidnow/wallet/dto/response/WalletBalanceResponse.java`
- Modify: `src/main/java/com/bidnow/wallet/service/WalletService.java`
- Modify: `src/main/java/com/bidnow/wallet/service/impl/WalletServiceImpl.java`, in the `deposit()` method's first lookup and a new method
- Test: `src/test/java/com/bidnow/wallet/service/impl/WalletServiceImplTest.java`

**Interfaces:**
- Consumes (Task 1): `WalletRepository.findByUserIdForUpdate`. Consumes (Task 2): `WalletErrorCodes.WALLET_NOT_FOUND`.
- Produces:
  - `WalletBalanceResponse` (`@Data @Builder`, `BigDecimal totalBalance, availableBalance, lockedBalance; String currency`)
  - `WalletService.getBalance(UUID userId): WalletBalanceResponse`

- [ ] **Step 1: Create the DTO and the interface method**

`src/main/java/com/bidnow/wallet/dto/response/WalletBalanceResponse.java`:

```java
package com.bidnow.wallet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class WalletBalanceResponse {
    private BigDecimal totalBalance;
    private BigDecimal availableBalance;
    private BigDecimal lockedBalance;
    private String currency;
}
```

In `src/main/java/com/bidnow/wallet/service/WalletService.java`, add the import `com.bidnow.wallet.dto.response.WalletBalanceResponse` and this method to the interface:

```java
    WalletBalanceResponse getBalance(UUID userId);
```

- [ ] **Step 2: Write the failing tests and switch the deposit stubs**

In `src/test/java/com/bidnow/wallet/service/impl/WalletServiceImplTest.java`:

(a) Add these imports: `com.bidnow.wallet.constant.WalletErrorCodes` and `com.bidnow.wallet.dto.response.WalletBalanceResponse`.

(b) In the three deposit tests, change the stub from `findByUserId` to `findByUserIdForUpdate`. Leave the other `findByUserId` stubs alone.
- `deposit_happyPath_creditsBalancesAndRecordsTransaction` (line ~135): `when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));`
- `deposit_suspendedWallet_throwsBadRequestException` (line ~172): `when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));`
- `deposit_walletNotFound_throwsNotFoundException` (line ~184): `when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());`

(c) Append these tests before the final closing brace:

```java
    // ── getBalance tests ──────────────────────────────────────────────────────

    @Test
    void getBalance_walletFound_returnsBalances() {
        UUID userId = UUID.randomUUID();
        Wallet wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .totalBalance(new BigDecimal("200.00"))
                .availableBalance(new BigDecimal("150.00"))
                .lockedBalance(new BigDecimal("50.00"))
                .currency("USD")
                .status(WalletStatus.ACTIVE)
                .build();
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));

        WalletBalanceResponse result = walletService.getBalance(userId);

        assertThat(result.getTotalBalance()).isEqualByComparingTo("200.00");
        assertThat(result.getAvailableBalance()).isEqualByComparingTo("150.00");
        assertThat(result.getLockedBalance()).isEqualByComparingTo("50.00");
        assertThat(result.getCurrency()).isEqualTo("USD");
    }

    @Test
    void getBalance_walletNotFound_throwsNotFoundWithWalletCode() {
        UUID userId = UUID.randomUUID();
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.getBalance(userId))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo(WalletErrorCodes.WALLET_NOT_FOUND);
    }
```

- [ ] **Step 3: Run the tests to verify they fail**

Run (from `backend/`): `mvn -q -pl wallet-service -am test -Dtest=WalletServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR, because `WalletServiceImpl` doesn't implement `getBalance`.

- [ ] **Step 4: Implement**

In `src/main/java/com/bidnow/wallet/service/impl/WalletServiceImpl.java`:

(a) Add the imports `com.bidnow.wallet.constant.WalletErrorCodes` and `com.bidnow.wallet.dto.response.WalletBalanceResponse`.

(b) In `deposit(UUID userId, DepositRequest request)`, replace the first lookup:

```java
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException(
                        "Wallet not found for userId: " + userId, ErrorCodes.NOT_FOUND));
```

with:

```java
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException(
                        "Wallet not found for userId: " + userId, ErrorCodes.NOT_FOUND));
```

The error code stays `ErrorCodes.NOT_FOUND`, because the public deposit API is unchanged.

(c) Add this method after `getMyWallet`:

```java
    @Override
    @Transactional(readOnly = true)
    public WalletBalanceResponse getBalance(UUID userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException(
                        "Wallet not found for userId: " + userId, WalletErrorCodes.WALLET_NOT_FOUND));
        return WalletBalanceResponse.builder()
                .totalBalance(wallet.getTotalBalance())
                .availableBalance(wallet.getAvailableBalance())
                .lockedBalance(wallet.getLockedBalance())
                .currency(wallet.getCurrency())
                .build();
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn -q -pl wallet-service -am test -Dtest=WalletServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS. All existing tests plus the 2 new ones should pass, with no `UnnecessaryStubbingException`.

---

### Task 5: Internal controller and security

**Files:**
- Create: `src/main/java/com/bidnow/wallet/controller/WalletInternalController.java`
- Modify: `src/main/java/com/bidnow/wallet/config/SecurityConfig.java`
- Test: `src/test/java/com/bidnow/wallet/controller/WalletInternalControllerTest.java`

**Interfaces:**
- Consumes (Task 3): `DepositLockService.getDepositLock`, `DepositLockService.lockDeposit`, `DepositLockRequest`, `DepositLockStatusResponse`, `DepositLockResponse`.
- Consumes (Task 4): `WalletService.getBalance`, `WalletBalanceResponse`.
- Consumes (Task 2): `WalletExceptionHandler`, `InsufficientBalanceException`, `ConflictException`, `WalletErrorCodes`.
- Produces:
  - `GET /api/v1/internal/wallet/deposit-lock?userId&auctionId` returns `BaseResponse<DepositLockStatusResponse>`
  - `POST /api/v1/internal/wallet/deposit-lock` returns `BaseResponse<DepositLockResponse>`
  - `GET /api/v1/internal/wallet/balance/{userId}` returns `BaseResponse<WalletBalanceResponse>`

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/bidnow/wallet/controller/WalletInternalControllerTest.java`:

```java
package com.bidnow.wallet.controller;

import com.bidnow.common.exception.ForbiddenException;
import com.bidnow.common.exception.GlobalExceptionHandler;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.wallet.constant.WalletErrorCodes;
import com.bidnow.wallet.dto.request.DepositLockRequest;
import com.bidnow.wallet.dto.response.DepositLockResponse;
import com.bidnow.wallet.dto.response.DepositLockStatusResponse;
import com.bidnow.wallet.dto.response.WalletBalanceResponse;
import com.bidnow.wallet.exception.ConflictException;
import com.bidnow.wallet.exception.InsufficientBalanceException;
import com.bidnow.wallet.exception.WalletExceptionHandler;
import com.bidnow.wallet.service.DepositLockService;
import com.bidnow.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WalletInternalControllerTest {

    private static final String BASE = "/api/v1/internal/wallet";

    @Mock
    private DepositLockService depositLockService;

    @Mock
    private WalletService walletService;

    private MockMvc mockMvc;

    private final UUID userId = UUID.randomUUID();
    private final UUID auctionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WalletInternalController(depositLockService, walletService))
                .setControllerAdvice(new WalletExceptionHandler(), new GlobalExceptionHandler())
                .build();
    }

    private String lockBody(String amount) {
        return "{\"userId\":\"" + userId + "\",\"auctionId\":\"" + auctionId + "\",\"depositAmount\":" + amount + "}";
    }

    // ── GET /deposit-lock ─────────────────────────────────────────────────────

    @Test
    void getDepositLock_notLocked_returnsLockedFalseOnly() throws Exception {
        when(depositLockService.getDepositLock(userId, auctionId))
                .thenReturn(DepositLockStatusResponse.builder().locked(false).build());

        mockMvc.perform(get(BASE + "/deposit-lock").param("userId", userId.toString())
                        .param("auctionId", auctionId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locked").value(false))
                .andExpect(jsonPath("$.data.amount").doesNotExist())
                .andExpect(jsonPath("$.data.status").doesNotExist());
    }

    @Test
    void getDepositLock_locked_returnsAmount() throws Exception {
        when(depositLockService.getDepositLock(userId, auctionId))
                .thenReturn(DepositLockStatusResponse.builder()
                        .locked(true).amount(new BigDecimal("50.00")).status("LOCKED").build());

        mockMvc.perform(get(BASE + "/deposit-lock").param("userId", userId.toString())
                        .param("auctionId", auctionId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locked").value(true))
                .andExpect(jsonPath("$.data.amount").value(50.00))
                .andExpect(jsonPath("$.data.status").value("LOCKED"));
    }

    @Test
    void getDepositLock_walletMissing_returns404() throws Exception {
        when(depositLockService.getDepositLock(userId, auctionId))
                .thenThrow(new NotFoundException("Wallet not found", WalletErrorCodes.WALLET_NOT_FOUND));

        mockMvc.perform(get(BASE + "/deposit-lock").param("userId", userId.toString())
                        .param("auctionId", auctionId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WALLET_NOT_FOUND"));
    }

    // ── POST /deposit-lock ────────────────────────────────────────────────────

    @Test
    void lockDeposit_success_returns200WithBalances() throws Exception {
        UUID lockId = UUID.randomUUID();
        when(depositLockService.lockDeposit(any(DepositLockRequest.class)))
                .thenReturn(DepositLockResponse.builder()
                        .lockId(lockId).amount(new BigDecimal("50.00")).status("LOCKED").alreadyLocked(false)
                        .availableBalance(new BigDecimal("150.00")).lockedBalance(new BigDecimal("50.00"))
                        .build());

        mockMvc.perform(post(BASE + "/deposit-lock").contentType(MediaType.APPLICATION_JSON).content(lockBody("50.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lockId").value(lockId.toString()))
                .andExpect(jsonPath("$.data.alreadyLocked").value(false))
                .andExpect(jsonPath("$.data.availableBalance").value(150.00))
                .andExpect(jsonPath("$.data.lockedBalance").value(50.00));
    }

    @Test
    void lockDeposit_insufficientBalance_returns400WithAmounts() throws Exception {
        when(depositLockService.lockDeposit(any(DepositLockRequest.class)))
                .thenThrow(new InsufficientBalanceException(new BigDecimal("30.00"), new BigDecimal("50.00")));

        mockMvc.perform(post(BASE + "/deposit-lock").contentType(MediaType.APPLICATION_JSON).content(lockBody("50.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_BALANCE"))
                .andExpect(jsonPath("$.errors.availableBalance").value("30.00"))
                .andExpect(jsonPath("$.errors.required").value("50.00"));
    }

    @Test
    void lockDeposit_negativeAmount_returns400AndSkipsService() throws Exception {
        mockMvc.perform(post(BASE + "/deposit-lock").contentType(MediaType.APPLICATION_JSON).content(lockBody("-1.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
        verify(depositLockService, never()).lockDeposit(any());
    }

    @Test
    void lockDeposit_missingAuctionId_returns400() throws Exception {
        String body = "{\"userId\":\"" + userId + "\",\"depositAmount\":50.00}";

        mockMvc.perform(post(BASE + "/deposit-lock").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verify(depositLockService, never()).lockDeposit(any());
    }

    @Test
    void lockDeposit_walletNotActive_returns403() throws Exception {
        when(depositLockService.lockDeposit(any(DepositLockRequest.class)))
                .thenThrow(new ForbiddenException("not active", WalletErrorCodes.WALLET_NOT_ACTIVE));

        mockMvc.perform(post(BASE + "/deposit-lock").contentType(MediaType.APPLICATION_JSON).content(lockBody("50.00")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("WALLET_NOT_ACTIVE"));
    }

    @Test
    void lockDeposit_lockClosed_returns409() throws Exception {
        when(depositLockService.lockDeposit(any(DepositLockRequest.class)))
                .thenThrow(new ConflictException("closed", WalletErrorCodes.DEPOSIT_LOCK_CLOSED));

        mockMvc.perform(post(BASE + "/deposit-lock").contentType(MediaType.APPLICATION_JSON).content(lockBody("50.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DEPOSIT_LOCK_CLOSED"));
    }

    @Test
    void lockDeposit_uniqueViolation_retriesOnceAndReturnsAlreadyLocked() throws Exception {
        when(depositLockService.lockDeposit(any(DepositLockRequest.class)))
                .thenThrow(new DataIntegrityViolationException("uq_deposit_locks_wallet_auction"))
                .thenReturn(DepositLockResponse.builder()
                        .lockId(UUID.randomUUID()).amount(new BigDecimal("50.00")).status("LOCKED").alreadyLocked(true)
                        .availableBalance(new BigDecimal("150.00")).lockedBalance(new BigDecimal("50.00"))
                        .build());

        mockMvc.perform(post(BASE + "/deposit-lock").contentType(MediaType.APPLICATION_JSON).content(lockBody("50.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alreadyLocked").value(true));
        verify(depositLockService, times(2)).lockDeposit(any(DepositLockRequest.class));
    }

    // ── GET /balance/{userId} ─────────────────────────────────────────────────

    @Test
    void getBalance_returnsBalances() throws Exception {
        when(walletService.getBalance(userId)).thenReturn(WalletBalanceResponse.builder()
                .totalBalance(new BigDecimal("200.00")).availableBalance(new BigDecimal("150.00"))
                .lockedBalance(new BigDecimal("50.00")).currency("USD").build());

        mockMvc.perform(get(BASE + "/balance/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalBalance").value(200.00))
                .andExpect(jsonPath("$.data.availableBalance").value(150.00))
                .andExpect(jsonPath("$.data.lockedBalance").value(50.00))
                .andExpect(jsonPath("$.data.currency").value("USD"));
    }

    @Test
    void getBalance_walletMissing_returns404() throws Exception {
        when(walletService.getBalance(userId))
                .thenThrow(new NotFoundException("Wallet not found", WalletErrorCodes.WALLET_NOT_FOUND));

        mockMvc.perform(get(BASE + "/balance/{userId}", userId))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run (from `backend/`): `mvn -q -pl wallet-service -am test -Dtest=WalletInternalControllerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR, because `WalletInternalController` doesn't exist yet.

- [ ] **Step 3: Implement the controller**

`src/main/java/com/bidnow/wallet/controller/WalletInternalController.java`:

```java
package com.bidnow.wallet.controller;

import com.bidnow.common.dto.BaseResponse;
import com.bidnow.wallet.dto.request.DepositLockRequest;
import com.bidnow.wallet.dto.response.DepositLockResponse;
import com.bidnow.wallet.dto.response.DepositLockStatusResponse;
import com.bidnow.wallet.dto.response.WalletBalanceResponse;
import com.bidnow.wallet.service.DepositLockService;
import com.bidnow.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/internal/wallet")
@RequiredArgsConstructor
@Tag(name = "Internal Wallet Interface", description = "Internal endpoints for service-to-service deposit locking and balance checks")
public class WalletInternalController {

    private final DepositLockService depositLockService;
    private final WalletService walletService;

    @Operation(summary = "Get deposit lock status (Internal)", description = "Returns whether the user's deposit is locked for the auction.")
    @GetMapping("/deposit-lock")
    public ResponseEntity<BaseResponse<DepositLockStatusResponse>> getDepositLock(@RequestParam UUID userId,
                                                                                  @RequestParam UUID auctionId) {
        return ResponseEntity.ok(BaseResponse.success(depositLockService.getDepositLock(userId, auctionId)));
    }

    @Operation(summary = "Lock deposit (Internal)", description = "Idempotently locks the auction deposit from the user's available balance.")
    @PostMapping("/deposit-lock")
    public ResponseEntity<BaseResponse<DepositLockResponse>> lockDeposit(@Valid @RequestBody DepositLockRequest request) {
        DepositLockResponse response;
        try {
            response = depositLockService.lockDeposit(request);
        } catch (DataIntegrityViolationException ex) {
            // Backstop for the unique (wallet_id, auction_id) constraint: the retry sees the existing row.
            log.warn("Deposit lock unique violation for userId={}, auctionId={}; retrying once",
                    request.getUserId(), request.getAuctionId());
            response = depositLockService.lockDeposit(request);
        }
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    @Operation(summary = "Get wallet balance (Internal)", description = "Returns total, available and locked balances for the user.")
    @GetMapping("/balance/{userId}")
    public ResponseEntity<BaseResponse<WalletBalanceResponse>> getBalance(@PathVariable UUID userId) {
        return ResponseEntity.ok(BaseResponse.success(walletService.getBalance(userId)));
    }
}
```

- [ ] **Step 4: Permit internal paths in `SecurityConfig`**

In `src/main/java/com/bidnow/wallet/config/SecurityConfig.java`, change the `authorizeHttpRequests` block to:

```java
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(SecurityConstants.PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers("/api/v1/internal/**").permitAll()
                        .anyRequest().authenticated()
                )
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn -q -pl wallet-service -am test -Dtest=WalletInternalControllerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (12 tests).

If `lockDeposit_negativeAmount_returns400AndSkipsService` fails on `$.errorCode`, check which exception type the common handler receives. `MethodArgumentNotValidException` extends `BindException`, so it should map to `INVALID_INPUT`. Do not change `common`; adjust only the assertion if the code differs.

---

### Task 6: Documentation

**Files:**
- Modify: `docs/diagrams/08-auction-registration-flow.md`
- Modify: `docs/architecture.md`

**Interfaces:** none. This task is docs only.

- [ ] **Step 1: Mark the registration flow superseded**

In `docs/diagrams/08-auction-registration-flow.md`, insert directly under the `# Auction Participation Registration Flow` heading:

```markdown
> **⚠️ Superseded.** Explicit pre-registration was removed. A user's **first bid** now implicitly registers them: the Bidding Service calls Wallet Service `POST /api/v1/internal/wallet/deposit-lock`, and the resulting `deposit_locks` row is the registration record. See `docs/superpowers/specs/2026-05-21-wallet-financial-management-design.md` (Flow 3) and `docs/superpowers/specs/2026-09-28-wallet-deposit-lock-design.md`. The diagram below is kept for history only.
```

- [ ] **Step 2: Document the internal API in the architecture doc**

In `docs/architecture.md`, find the paragraph containing `**Message Broker**: Ensures eventual consistency` (line ~61). Add this section after the end of that list:

```markdown
### Service-to-Service Internal APIs

Synchronous internal calls go directly between services via Eureka + OpenFeign, never through the API Gateway. The gateway's `AuthenticationFilter` blocks `/api/v1/**/internal/**`, and each owning service `permitAll`s its own internal paths.

| Owner | Endpoint | Caller | Purpose |
|---|---|---|---|
| Wallet | `GET /api/v1/internal/wallet/deposit-lock?userId=&auctionId=` | Bidding | Is the user's deposit locked for this auction? |
| Wallet | `POST /api/v1/internal/wallet/deposit-lock` `{userId, auctionId, depositAmount}` | Bidding | Idempotently lock the deposit on first bid (implicit registration). Errors: `INSUFFICIENT_BALANCE` 400, `WALLET_NOT_ACTIVE` 403, `WALLET_NOT_FOUND` 404, `DEPOSIT_LOCK_CLOSED` 409 |
| Wallet | `GET /api/v1/internal/wallet/balance/{userId}` | Bidding | Total / available / locked balances |
```

---

### Task 7: Full verification

**Files:** none modified.

- [ ] **Step 1: Run the full wallet-service suite**

Run (from `backend/`): `mvn -q -pl wallet-service -am clean test`
Expected: BUILD SUCCESS with 0 failures. The new tests are `WalletExceptionHandlerTest` (2), `DepositLockServiceImplTest` (14), `WalletInternalControllerTest` (12) and `WalletServiceImplTest` (+2).

- [ ] **Step 2: Manual smoke test (only if a local Postgres + wallet-service stack is available)**

Start the stack per `backend/README.md`, then call wallet-service directly with no auth headers. Use its port from `wallet-service/src/main/resources/application.yml`, and a `userId` that has a wallet with at least 50 available.

```bash
curl -s "http://localhost:<wallet-port>/api/v1/internal/wallet/balance/<userId>"
```

Expected: 200 with balances. This confirms `permitAll`. A 401 or 403 means Task 5 Step 4 was not applied.

```bash
curl -s -X POST "http://localhost:<wallet-port>/api/v1/internal/wallet/deposit-lock" -H "Content-Type: application/json" -d "{\"userId\":\"<userId>\",\"auctionId\":\"11111111-1111-1111-1111-111111111111\",\"depositAmount\":50.00}"
```

Expected: 200 with `alreadyLocked:false`. Running it again gives `alreadyLocked:true` with the same balances. That confirms Liquibase migration 03 applied and the mapping works against real Postgres.

If no stack is available, skip this step and report it as not run.
