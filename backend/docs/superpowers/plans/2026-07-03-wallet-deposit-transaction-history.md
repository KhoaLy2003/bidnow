# Wallet Deposit & Transaction History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add mock deposit and paginated transaction history to wallet-service, with a gateway-ready schema and safe Kafka event publishing via Spring's `@TransactionalEventListener`.

**Architecture:** Three sequential tasks: (1) DB migration + Transaction entity/repository, (2) deposit endpoint TDD end-to-end including Kafka publisher, (3) transaction history endpoint TDD. Kafka event `DEPOSIT_RECEIVED` is published via `@TransactionalEventListener(AFTER_COMMIT)` so it only fires if the DB commit succeeds.

**Tech Stack:** Spring Boot 3.2.4, Spring Data JPA + JpaSpecificationExecutor, Spring Kafka, Liquibase, PostgreSQL, Lombok, Jakarta Validation, JUnit 5 + Mockito + AssertJ

## Global Constraints

- `Transaction` is immutable — does **NOT** extend `BaseEntity` (which adds `updated_at`); use `@CreationTimestamp` on `created_at` directly.
- All API responses use `BaseResponse<T>` via `BaseResponse.success(data)`.
- Paginated responses use `PageResponse<T>` via `PageResponse.of(page)`.
- `@AuthenticatedUserId` on controller params resolves `UUID userId` from the `X-User-Id` header.
- Exception constructors: `NotFoundException(message, ErrorCodes.NOT_FOUND)`, `BadRequestException(message, ErrorCodes.INVALID_INPUT)`.
- Kafka producers use `KafkaTemplate<String, Object>`.
- DB migrations: Liquibase SQL files in `wallet-service/src/main/resources/db/changelog/migrations/`, registered in `db.changelog-master.xml`.
- `SpecificationBuilder.withBetweenIfPresent(field, from, to)` skips the condition silently if either bound is null — partial date ranges are ignored.
- Tests use Mockito unit test style (`@ExtendWith(MockitoExtension.class)`), no `@SpringBootTest`.
- `WalletStatus` values: `ACTIVE`, `SUSPENDED`.
- `TransactionType` enum: `DEPOSIT, HOLD, HOLD_CANCEL, PAYMENT, FORFEIT, REFUND, FEE, WITHDRAWAL` (all defined; only `DEPOSIT` used in this issue).
- `TransactionStatus` enum: `PENDING, COMPLETED, FAILED`.

---

## File Map

### New — `common` module
| File | Responsibility |
|---|---|
| `common/src/main/java/com/bidnow/common/dto/event/DepositReceivedEvent.java` | Kafka event DTO consumed by downstream services |

### New — `wallet-service`
| File | Responsibility |
|---|---|
| `wallet-service/src/main/java/com/bidnow/wallet/domain/enums/TransactionType.java` | Transaction type enum |
| `wallet-service/src/main/java/com/bidnow/wallet/domain/enums/TransactionStatus.java` | Transaction status enum |
| `wallet-service/src/main/java/com/bidnow/wallet/domain/entity/Transaction.java` | Immutable transaction record entity |
| `wallet-service/src/main/java/com/bidnow/wallet/repository/TransactionRepository.java` | JPA repository with Specification support |
| `wallet-service/src/main/java/com/bidnow/wallet/dto/request/DepositRequest.java` | Deposit request body with Jakarta validation |
| `wallet-service/src/main/java/com/bidnow/wallet/dto/response/DepositResponse.java` | Deposit response DTO |
| `wallet-service/src/main/java/com/bidnow/wallet/dto/response/TransactionResponse.java` | Per-transaction response DTO |
| `wallet-service/src/main/java/com/bidnow/wallet/kafka/DepositCompletedApplicationEvent.java` | Spring `ApplicationEvent` — internal trigger for Kafka publish after commit |
| `wallet-service/src/main/java/com/bidnow/wallet/kafka/WalletEventPublisher.java` | `@TransactionalEventListener` that publishes to Kafka after DB commit |
| `wallet-service/src/main/resources/db/changelog/migrations/02-init-transactions.sql` | Creates `transactions` table |

### Modified — `wallet-service`
| File | Change |
|---|---|
| `wallet-service/src/main/resources/db/changelog/db.changelog-master.xml` | Register `02-init-transactions.sql` |
| `wallet-service/src/main/java/com/bidnow/wallet/service/WalletService.java` | Add `deposit()` and `getTransactions()` |
| `wallet-service/src/main/java/com/bidnow/wallet/service/impl/WalletServiceImpl.java` | Implement both methods; add new injected deps |
| `wallet-service/src/main/java/com/bidnow/wallet/controller/WalletController.java` | Add `POST /deposit` and `GET /transactions` |
| `wallet-service/src/test/java/com/bidnow/wallet/service/impl/WalletServiceImplTest.java` | Add mocks + deposit + getTransactions tests |
| `wallet-service/src/test/java/com/bidnow/wallet/controller/WalletControllerTest.java` | Add deposit + getTransactions controller tests |

---

### Task 1: DB Migration + Transaction Domain

**Files:**
- Create: `wallet-service/src/main/resources/db/changelog/migrations/02-init-transactions.sql`
- Modify: `wallet-service/src/main/resources/db/changelog/db.changelog-master.xml`
- Create: `wallet-service/src/main/java/com/bidnow/wallet/domain/enums/TransactionType.java`
- Create: `wallet-service/src/main/java/com/bidnow/wallet/domain/enums/TransactionStatus.java`
- Create: `wallet-service/src/main/java/com/bidnow/wallet/domain/entity/Transaction.java`
- Create: `wallet-service/src/main/java/com/bidnow/wallet/repository/TransactionRepository.java`

**Interfaces:**
- Produces: `Transaction` entity with fields `id`, `walletId`, `type` (TransactionType), `amount`, `availableBalanceBefore`, `availableBalanceAfter`, `referenceId`, `description`, `status` (TransactionStatus), `paymentGatewayTxId`, `metadata`, `createdAt`
- Produces: `TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction>`

- [ ] **Step 1: Create the migration SQL**

`wallet-service/src/main/resources/db/changelog/migrations/02-init-transactions.sql`:
```sql
-- liquibase formatted sql

-- changeset bidnow:wallet_002
CREATE TABLE transactions
(
    id                       UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    wallet_id                UUID           NOT NULL REFERENCES wallets (id),
    type                     VARCHAR(20)    NOT NULL,
    amount                   DECIMAL(19, 4) NOT NULL,
    available_balance_before DECIMAL(19, 4) NOT NULL,
    available_balance_after  DECIMAL(19, 4) NOT NULL,
    reference_id             UUID,
    description              TEXT,
    status                   VARCHAR(20)    NOT NULL,
    payment_gateway_tx_id    VARCHAR(100),
    metadata                 JSONB,
    created_at               TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_transactions_wallet_id ON transactions (wallet_id);
CREATE INDEX idx_transactions_type ON transactions (type);
CREATE INDEX idx_transactions_created_at ON transactions (created_at);
```

- [ ] **Step 2: Register migration in db.changelog-master.xml**

`wallet-service/src/main/resources/db/changelog/db.changelog-master.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.0.xsd">

    <include file="db/changelog/migrations/01-init-wallets.sql"/>
    <include file="db/changelog/migrations/02-init-transactions.sql"/>

</databaseChangeLog>
```

- [ ] **Step 3: Create TransactionType enum**

`wallet-service/src/main/java/com/bidnow/wallet/domain/enums/TransactionType.java`:
```java
package com.bidnow.wallet.domain.enums;

public enum TransactionType {
    DEPOSIT, HOLD, HOLD_CANCEL, PAYMENT, FORFEIT, REFUND, FEE, WITHDRAWAL
}
```

- [ ] **Step 4: Create TransactionStatus enum**

`wallet-service/src/main/java/com/bidnow/wallet/domain/enums/TransactionStatus.java`:
```java
package com.bidnow.wallet.domain.enums;

public enum TransactionStatus {
    PENDING, COMPLETED, FAILED
}
```

- [ ] **Step 5: Create Transaction entity**

`wallet-service/src/main/java/com/bidnow/wallet/domain/entity/Transaction.java`:
```java
package com.bidnow.wallet.domain.entity;

import com.bidnow.wallet.domain.enums.TransactionStatus;
import com.bidnow.wallet.domain.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TransactionType type;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "available_balance_before", nullable = false, precision = 19, scale = 4)
    private BigDecimal availableBalanceBefore;

    @Column(name = "available_balance_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal availableBalanceAfter;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "payment_gateway_tx_id", length = 100)
    private String paymentGatewayTxId;

    @Column(name = "metadata", columnDefinition = "JSONB")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 6: Create TransactionRepository**

`wallet-service/src/main/java/com/bidnow/wallet/repository/TransactionRepository.java`:
```java
package com.bidnow.wallet.repository;

import com.bidnow.wallet.domain.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {
}
```

- [ ] **Step 7: Verify compilation**

```bash
mvn compile -pl wallet-service --no-transfer-progress
```
Expected: `BUILD SUCCESS`

- [ ] **Step 8: Commit**

```bash
git add wallet-service/src/main/resources/db/changelog/migrations/02-init-transactions.sql \
        wallet-service/src/main/resources/db/changelog/db.changelog-master.xml \
        wallet-service/src/main/java/com/bidnow/wallet/domain/enums/TransactionType.java \
        wallet-service/src/main/java/com/bidnow/wallet/domain/enums/TransactionStatus.java \
        wallet-service/src/main/java/com/bidnow/wallet/domain/entity/Transaction.java \
        wallet-service/src/main/java/com/bidnow/wallet/repository/TransactionRepository.java
git commit -m "feat(wallet): add Transaction entity and DB migration [WALLET-302]"
```

---

### Task 2: Deposit Endpoint

**Files:**
- Create: `common/src/main/java/com/bidnow/common/dto/event/DepositReceivedEvent.java`
- Create: `wallet-service/src/main/java/com/bidnow/wallet/dto/request/DepositRequest.java`
- Create: `wallet-service/src/main/java/com/bidnow/wallet/dto/response/DepositResponse.java`
- Create: `wallet-service/src/main/java/com/bidnow/wallet/kafka/DepositCompletedApplicationEvent.java`
- Create: `wallet-service/src/main/java/com/bidnow/wallet/kafka/WalletEventPublisher.java`
- Modify: `wallet-service/src/main/java/com/bidnow/wallet/service/WalletService.java`
- Modify: `wallet-service/src/main/java/com/bidnow/wallet/service/impl/WalletServiceImpl.java`
- Modify: `wallet-service/src/main/java/com/bidnow/wallet/controller/WalletController.java`
- Test: `wallet-service/src/test/java/com/bidnow/wallet/service/impl/WalletServiceImplTest.java`
- Test: `wallet-service/src/test/java/com/bidnow/wallet/controller/WalletControllerTest.java`

**Interfaces:**
- Consumes from Task 1: `Transaction`, `TransactionType`, `TransactionStatus`, `TransactionRepository`
- Produces: `WalletService.deposit(UUID userId, DepositRequest request): DepositResponse`
- Produces: `DepositCompletedApplicationEvent(Object source, UUID userId, UUID walletId, BigDecimal amount, BigDecimal newBalance)`

- [ ] **Step 1: Write failing service tests for deposit**

Replace `wallet-service/src/test/java/com/bidnow/wallet/service/impl/WalletServiceImplTest.java` with the full updated class (preserves all existing tests, adds three new ones):

```java
package com.bidnow.wallet.service.impl;

import com.bidnow.common.exception.BadRequestException;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.wallet.domain.entity.Transaction;
import com.bidnow.wallet.domain.entity.Wallet;
import com.bidnow.wallet.domain.enums.TransactionStatus;
import com.bidnow.wallet.domain.enums.TransactionType;
import com.bidnow.wallet.domain.enums.WalletStatus;
import com.bidnow.wallet.dto.request.DepositRequest;
import com.bidnow.wallet.dto.response.DepositResponse;
import com.bidnow.wallet.dto.response.WalletResponse;
import com.bidnow.wallet.kafka.DepositCompletedApplicationEvent;
import com.bidnow.wallet.repository.TransactionRepository;
import com.bidnow.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private WalletServiceImpl walletService;

    // ── existing tests ────────────────────────────────────────────────────────

    @Test
    void createWalletIfAbsent_walletAlreadyExists_doesNotSave() {
        UUID userId = UUID.randomUUID();
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(new Wallet()));

        walletService.createWalletIfAbsent(userId);

        verify(walletRepository, never()).save(any());
    }

    @Test
    void createWalletIfAbsent_noWallet_savesWithZeroBalancesAndActiveStatus() {
        UUID userId = UUID.randomUUID();
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        walletService.createWalletIfAbsent(userId);

        ArgumentCaptor<Wallet> captor = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository).save(captor.capture());
        Wallet saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getTotalBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getAvailableBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getLockedBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getCurrency()).isEqualTo("USD");
        assertThat(saved.getStatus()).isEqualTo(WalletStatus.ACTIVE);
    }

    @Test
    void getMyWallet_walletFound_returnsMappedResponse() {
        UUID userId = UUID.randomUUID();
        Wallet wallet = Wallet.builder()
                .userId(userId)
                .totalBalance(new BigDecimal("100.00"))
                .availableBalance(new BigDecimal("80.00"))
                .lockedBalance(new BigDecimal("20.00"))
                .currency("USD")
                .status(WalletStatus.ACTIVE)
                .build();
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));

        WalletResponse response = walletService.getMyWallet(userId);

        assertThat(response.getTotalBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(response.getAvailableBalance()).isEqualByComparingTo(new BigDecimal("80.00"));
        assertThat(response.getLockedBalance()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(response.getCurrency()).isEqualTo("USD");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void getMyWallet_walletNotFound_throwsNotFoundException() {
        UUID userId = UUID.randomUUID();
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.getMyWallet(userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(userId.toString());
    }

    // ── deposit tests ─────────────────────────────────────────────────────────

    @Test
    void deposit_happyPath_creditsBalancesAndRecordsTransaction() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        Wallet wallet = Wallet.builder()
                .id(walletId)
                .userId(userId)
                .totalBalance(new BigDecimal("100.00"))
                .availableBalance(new BigDecimal("100.00"))
                .lockedBalance(BigDecimal.ZERO)
                .currency("USD")
                .status(WalletStatus.ACTIVE)
                .build();
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DepositResponse response = walletService.deposit(userId, new DepositRequest(new BigDecimal("500.00")));

        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo(new BigDecimal("600.00"));
        assertThat(wallet.getTotalBalance()).isEqualByComparingTo(new BigDecimal("600.00"));

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        Transaction saved = txCaptor.getValue();
        assertThat(saved.getWalletId()).isEqualTo(walletId);
        assertThat(saved.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(saved.getAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(saved.getAvailableBalanceBefore()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(saved.getAvailableBalanceAfter()).isEqualByComparingTo(new BigDecimal("600.00"));
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

        verify(eventPublisher).publishEvent(any(DepositCompletedApplicationEvent.class));

        assertThat(response.getNewBalance()).isEqualByComparingTo(new BigDecimal("600.00"));
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void deposit_suspendedWallet_throwsBadRequestException() {
        UUID userId = UUID.randomUUID();
        Wallet wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .totalBalance(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO)
                .lockedBalance(BigDecimal.ZERO)
                .currency("USD")
                .status(WalletStatus.SUSPENDED)
                .build();
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> walletService.deposit(userId, new DepositRequest(new BigDecimal("100.00"))))
                .isInstanceOf(BadRequestException.class);

        verify(transactionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void deposit_walletNotFound_throwsNotFoundException() {
        UUID userId = UUID.randomUUID();
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.deposit(userId, new DepositRequest(new BigDecimal("100.00"))))
                .isInstanceOf(NotFoundException.class);
    }
}
```

- [ ] **Step 2: Run to verify tests fail**

```bash
mvn test -pl wallet-service -Dtest=WalletServiceImplTest --no-transfer-progress
```
Expected: FAIL — `deposit_*` tests fail because `deposit()` does not exist yet. Existing 4 tests still pass.

- [ ] **Step 3: Create DepositRequest**

`wallet-service/src/main/java/com/bidnow/wallet/dto/request/DepositRequest.java`:
```java
package com.bidnow.wallet.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepositRequest {

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than 0")
    private BigDecimal amount;
}
```

- [ ] **Step 4: Create DepositResponse**

`wallet-service/src/main/java/com/bidnow/wallet/dto/response/DepositResponse.java`:
```java
package com.bidnow.wallet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class DepositResponse {
    private UUID transactionId;
    private BigDecimal newBalance;
    private String status;
}
```

- [ ] **Step 5: Create DepositReceivedEvent in common**

`common/src/main/java/com/bidnow/common/dto/event/DepositReceivedEvent.java`:
```java
package com.bidnow.common.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepositReceivedEvent {
    private UUID userId;
    private UUID walletId;
    private BigDecimal amount;
    private BigDecimal newBalance;
}
```

- [ ] **Step 6: Create DepositCompletedApplicationEvent**

`wallet-service/src/main/java/com/bidnow/wallet/kafka/DepositCompletedApplicationEvent.java`:
```java
package com.bidnow.wallet.kafka;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
public class DepositCompletedApplicationEvent extends ApplicationEvent {

    private final UUID userId;
    private final UUID walletId;
    private final BigDecimal amount;
    private final BigDecimal newBalance;

    public DepositCompletedApplicationEvent(Object source, UUID userId, UUID walletId,
                                             BigDecimal amount, BigDecimal newBalance) {
        super(source);
        this.userId = userId;
        this.walletId = walletId;
        this.amount = amount;
        this.newBalance = newBalance;
    }
}
```

- [ ] **Step 7: Create WalletEventPublisher**

`wallet-service/src/main/java/com/bidnow/wallet/kafka/WalletEventPublisher.java`:
```java
package com.bidnow.wallet.kafka;

import com.bidnow.common.dto.event.DepositReceivedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class WalletEventPublisher {

    private static final String DEPOSIT_RECEIVED_TOPIC = "deposit-received-topic";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDepositCompleted(DepositCompletedApplicationEvent event) {
        DepositReceivedEvent kafkaEvent = DepositReceivedEvent.builder()
                .userId(event.getUserId())
                .walletId(event.getWalletId())
                .amount(event.getAmount())
                .newBalance(event.getNewBalance())
                .build();
        kafkaTemplate.send(DEPOSIT_RECEIVED_TOPIC, event.getUserId().toString(), kafkaEvent)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish DepositReceivedEvent for userId={}", event.getUserId(), ex);
                    } else {
                        log.info("Published DepositReceivedEvent for userId={}", event.getUserId());
                    }
                });
    }
}
```

- [ ] **Step 8: Update WalletService interface**

`wallet-service/src/main/java/com/bidnow/wallet/service/WalletService.java`:
```java
package com.bidnow.wallet.service;

import com.bidnow.wallet.dto.request.DepositRequest;
import com.bidnow.wallet.dto.response.DepositResponse;
import com.bidnow.wallet.dto.response.WalletResponse;

import java.util.UUID;

public interface WalletService {
    void createWalletIfAbsent(UUID userId);
    WalletResponse getMyWallet(UUID userId);
    DepositResponse deposit(UUID userId, DepositRequest request);
}
```

- [ ] **Step 9: Implement deposit() in WalletServiceImpl**

`wallet-service/src/main/java/com/bidnow/wallet/service/impl/WalletServiceImpl.java`:
```java
package com.bidnow.wallet.service.impl;

import com.bidnow.common.constant.ErrorCodes;
import com.bidnow.common.exception.BadRequestException;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.wallet.domain.entity.Transaction;
import com.bidnow.wallet.domain.entity.Wallet;
import com.bidnow.wallet.domain.enums.TransactionStatus;
import com.bidnow.wallet.domain.enums.TransactionType;
import com.bidnow.wallet.domain.enums.WalletStatus;
import com.bidnow.wallet.dto.request.DepositRequest;
import com.bidnow.wallet.dto.response.DepositResponse;
import com.bidnow.wallet.dto.response.WalletResponse;
import com.bidnow.wallet.kafka.DepositCompletedApplicationEvent;
import com.bidnow.wallet.repository.TransactionRepository;
import com.bidnow.wallet.repository.WalletRepository;
import com.bidnow.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void createWalletIfAbsent(UUID userId) {
        if (walletRepository.findByUserId(userId).isPresent()) {
            log.debug("Wallet already exists for userId={}, skipping creation", userId);
            return;
        }
        Wallet wallet = Wallet.builder()
                .userId(userId)
                .totalBalance(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO)
                .lockedBalance(BigDecimal.ZERO)
                .currency("USD")
                .status(WalletStatus.ACTIVE)
                .build();
        walletRepository.save(wallet);
        log.info("Created wallet for userId={}", userId);
    }

    @Override
    @Transactional(readOnly = true)
    public WalletResponse getMyWallet(UUID userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException(
                        "Wallet not found for userId: " + userId, ErrorCodes.NOT_FOUND));
        return WalletResponse.builder()
                .totalBalance(wallet.getTotalBalance())
                .availableBalance(wallet.getAvailableBalance())
                .lockedBalance(wallet.getLockedBalance())
                .currency(wallet.getCurrency())
                .status(wallet.getStatus().name())
                .build();
    }

    @Override
    @Transactional
    public DepositResponse deposit(UUID userId, DepositRequest request) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException(
                        "Wallet not found for userId: " + userId, ErrorCodes.NOT_FOUND));

        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new BadRequestException(
                    "Wallet is not active for userId: " + userId, ErrorCodes.INVALID_INPUT);
        }

        BigDecimal balanceBefore = wallet.getAvailableBalance();
        BigDecimal balanceAfter = balanceBefore.add(request.getAmount());

        wallet.setAvailableBalance(balanceAfter);
        wallet.setTotalBalance(wallet.getTotalBalance().add(request.getAmount()));
        walletRepository.save(wallet);

        Transaction transaction = Transaction.builder()
                .walletId(wallet.getId())
                .type(TransactionType.DEPOSIT)
                .amount(request.getAmount())
                .availableBalanceBefore(balanceBefore)
                .availableBalanceAfter(balanceAfter)
                .status(TransactionStatus.COMPLETED)
                .description("Mock deposit")
                .build();
        transactionRepository.save(transaction);

        eventPublisher.publishEvent(
                new DepositCompletedApplicationEvent(this, userId, wallet.getId(),
                        request.getAmount(), balanceAfter));

        log.info("Deposit completed for userId={}, amount={}, newBalance={}",
                userId, request.getAmount(), balanceAfter);

        return DepositResponse.builder()
                .transactionId(transaction.getId())
                .newBalance(balanceAfter)
                .status(transaction.getStatus().name())
                .build();
    }
}
```

- [ ] **Step 10: Install common then run service tests — expect all pass**

`DepositReceivedEvent` is a new class in `common` — reinstall it first so wallet-service picks it up:

```bash
mvn install -pl common -DskipTests --no-transfer-progress
mvn test -pl wallet-service -Dtest=WalletServiceImplTest --no-transfer-progress
```
Expected: `BUILD SUCCESS` for common install, then all 7 tests PASS.

- [ ] **Step 11: Add POST /deposit to WalletController**

`wallet-service/src/main/java/com/bidnow/wallet/controller/WalletController.java`:
```java
package com.bidnow.wallet.controller;

import com.bidnow.common.annotation.AuthenticatedUserId;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.wallet.dto.request.DepositRequest;
import com.bidnow.wallet.dto.response.DepositResponse;
import com.bidnow.wallet.dto.response.WalletResponse;
import com.bidnow.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping
    public ResponseEntity<BaseResponse<WalletResponse>> getMyWallet(@AuthenticatedUserId UUID userId) {
        return ResponseEntity.ok(BaseResponse.success(walletService.getMyWallet(userId)));
    }

    @PostMapping("/deposit")
    public ResponseEntity<BaseResponse<DepositResponse>> deposit(
            @AuthenticatedUserId UUID userId,
            @Valid @RequestBody DepositRequest request) {
        return ResponseEntity.ok(BaseResponse.success(walletService.deposit(userId, request)));
    }
}
```

- [ ] **Step 12: Add deposit controller tests**

Add the following to `wallet-service/src/test/java/com/bidnow/wallet/controller/WalletControllerTest.java` — append inside the class after the existing tests. Also add the new imports shown:

```java
// New imports to add at the top of WalletControllerTest:
import com.bidnow.wallet.dto.request.DepositRequest;
import com.bidnow.wallet.dto.response.DepositResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

// New field to add inside the class:
private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

// New test methods:

@Test
void deposit_validAmount_returns200WithDepositResponse() {
    UUID userId = UUID.randomUUID();
    DepositResponse depositResponse = DepositResponse.builder()
            .transactionId(UUID.randomUUID())
            .newBalance(new BigDecimal("600.00"))
            .status("COMPLETED")
            .build();
    when(walletService.deposit(eq(userId), any(DepositRequest.class))).thenReturn(depositResponse);

    ResponseEntity<BaseResponse<DepositResponse>> response =
            walletController.deposit(userId, new DepositRequest(new BigDecimal("500.00")));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getData().getNewBalance()).isEqualByComparingTo(new BigDecimal("600.00"));
    assertThat(response.getBody().getData().getStatus()).isEqualTo("COMPLETED");
}

@Test
void depositRequest_amountIsZero_failsValidation() {
    Set<ConstraintViolation<DepositRequest>> violations = validator.validate(new DepositRequest(BigDecimal.ZERO));
    assertThat(violations).isNotEmpty();
    assertThat(violations).anyMatch(v -> v.getMessage().contains("greater than 0"));
}

@Test
void depositRequest_amountIsNegative_failsValidation() {
    Set<ConstraintViolation<DepositRequest>> violations = validator.validate(new DepositRequest(new BigDecimal("-1.00")));
    assertThat(violations).isNotEmpty();
}

@Test
void depositRequest_amountIsNull_failsValidation() {
    Set<ConstraintViolation<DepositRequest>> violations = validator.validate(new DepositRequest(null));
    assertThat(violations).isNotEmpty();
    assertThat(violations).anyMatch(v -> v.getMessage().contains("required"));
}
```

- [ ] **Step 13: Run all wallet-service tests**

```bash
mvn install -pl common -DskipTests --no-transfer-progress
mvn test -pl wallet-service --no-transfer-progress
```
Expected: All 11 tests PASS.

- [ ] **Step 14: Commit**

```bash
git add common/src/main/java/com/bidnow/common/dto/event/DepositReceivedEvent.java \
        wallet-service/src/main/java/com/bidnow/wallet/dto/request/DepositRequest.java \
        wallet-service/src/main/java/com/bidnow/wallet/dto/response/DepositResponse.java \
        wallet-service/src/main/java/com/bidnow/wallet/kafka/DepositCompletedApplicationEvent.java \
        wallet-service/src/main/java/com/bidnow/wallet/kafka/WalletEventPublisher.java \
        wallet-service/src/main/java/com/bidnow/wallet/service/WalletService.java \
        wallet-service/src/main/java/com/bidnow/wallet/service/impl/WalletServiceImpl.java \
        wallet-service/src/main/java/com/bidnow/wallet/controller/WalletController.java \
        wallet-service/src/test/java/com/bidnow/wallet/service/impl/WalletServiceImplTest.java \
        wallet-service/src/test/java/com/bidnow/wallet/controller/WalletControllerTest.java
git commit -m "feat(wallet): implement deposit endpoint with Kafka event [WALLET-302]"
```

---

### Task 3: Transaction History Endpoint

**Files:**
- Create: `wallet-service/src/main/java/com/bidnow/wallet/dto/response/TransactionResponse.java`
- Modify: `wallet-service/src/main/java/com/bidnow/wallet/service/WalletService.java`
- Modify: `wallet-service/src/main/java/com/bidnow/wallet/service/impl/WalletServiceImpl.java`
- Modify: `wallet-service/src/main/java/com/bidnow/wallet/controller/WalletController.java`
- Test: `wallet-service/src/test/java/com/bidnow/wallet/service/impl/WalletServiceImplTest.java`
- Test: `wallet-service/src/test/java/com/bidnow/wallet/controller/WalletControllerTest.java`

**Interfaces:**
- Consumes from Task 1: `TransactionRepository` (JpaSpecificationExecutor), `Transaction`, `TransactionType`, `TransactionStatus`
- Consumes from common: `SpecificationBuilder`, `SearchOperator`, `PageResponse`
- Produces: `WalletService.getTransactions(UUID userId, TransactionType type, LocalDate startDate, LocalDate endDate, int page, int size): PageResponse<TransactionResponse>`
- Produces: `TransactionResponse` with fields `id` (UUID), `type` (String), `amount` (BigDecimal), `availableBalanceBefore` (BigDecimal), `availableBalanceAfter` (BigDecimal), `description` (String), `status` (String), `createdAt` (LocalDateTime)

- [ ] **Step 1: Write failing service tests for getTransactions**

Add the following tests to `WalletServiceImplTest`. Also add the new imports shown:

```java
// New imports to add:
import com.bidnow.common.dto.PageResponse;
import com.bidnow.wallet.dto.response.TransactionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// New test methods:

@Test
void getTransactions_noFilters_returnsPagedResultsSortedByCreatedAtDesc() {
    UUID userId = UUID.randomUUID();
    UUID walletId = UUID.randomUUID();
    Wallet wallet = Wallet.builder()
            .id(walletId)
            .userId(userId)
            .totalBalance(new BigDecimal("600.00"))
            .availableBalance(new BigDecimal("600.00"))
            .lockedBalance(BigDecimal.ZERO)
            .currency("USD")
            .status(WalletStatus.ACTIVE)
            .build();
    Transaction tx = Transaction.builder()
            .id(UUID.randomUUID())
            .walletId(walletId)
            .type(TransactionType.DEPOSIT)
            .amount(new BigDecimal("500.00"))
            .availableBalanceBefore(new BigDecimal("100.00"))
            .availableBalanceAfter(new BigDecimal("600.00"))
            .status(TransactionStatus.COMPLETED)
            .description("Mock deposit")
            .createdAt(LocalDateTime.now())
            .build();
    when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));
    when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(tx)));

    PageResponse<TransactionResponse> result =
            walletService.getTransactions(userId, null, null, null, 0, 10);

    assertThat(result.getData()).hasSize(1);
    TransactionResponse dto = result.getData().get(0);
    assertThat(dto.getType()).isEqualTo("DEPOSIT");
    assertThat(dto.getAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
    assertThat(dto.getAvailableBalanceBefore()).isEqualByComparingTo(new BigDecimal("100.00"));
    assertThat(dto.getAvailableBalanceAfter()).isEqualByComparingTo(new BigDecimal("600.00"));
    assertThat(dto.getStatus()).isEqualTo("COMPLETED");
    assertThat(dto.getDescription()).isEqualTo("Mock deposit");

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(transactionRepository).findAll(any(Specification.class), pageableCaptor.capture());
    assertThat(pageableCaptor.getValue().getSort()).isEqualTo(Sort.by("createdAt").descending());
}

@Test
void getTransactions_walletNotFound_throwsNotFoundException() {
    UUID userId = UUID.randomUUID();
    when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> walletService.getTransactions(userId, null, null, null, 0, 10))
            .isInstanceOf(NotFoundException.class);

    verify(transactionRepository, never()).findAll(any(Specification.class), any(Pageable.class));
}

@Test
void getTransactions_emptyWallet_returnsEmptyPage() {
    UUID userId = UUID.randomUUID();
    Wallet wallet = Wallet.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .totalBalance(BigDecimal.ZERO)
            .availableBalance(BigDecimal.ZERO)
            .lockedBalance(BigDecimal.ZERO)
            .currency("USD")
            .status(WalletStatus.ACTIVE)
            .build();
    when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));
    when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(Page.empty());

    PageResponse<TransactionResponse> result =
            walletService.getTransactions(userId, null, null, null, 0, 10);

    assertThat(result.getData()).isEmpty();
    assertThat(result.getPagination().getTotal()).isZero();
}
```

- [ ] **Step 2: Run to verify tests fail**

```bash
mvn test -pl wallet-service -Dtest=WalletServiceImplTest --no-transfer-progress
```
Expected: FAIL — `getTransactions_*` tests fail. All other 7 tests still PASS.

- [ ] **Step 3: Create TransactionResponse**

`wallet-service/src/main/java/com/bidnow/wallet/dto/response/TransactionResponse.java`:
```java
package com.bidnow.wallet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TransactionResponse {
    private UUID id;
    private String type;
    private BigDecimal amount;
    private BigDecimal availableBalanceBefore;
    private BigDecimal availableBalanceAfter;
    private String description;
    private String status;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 4: Update WalletService interface**

`wallet-service/src/main/java/com/bidnow/wallet/service/WalletService.java`:
```java
package com.bidnow.wallet.service;

import com.bidnow.common.dto.PageResponse;
import com.bidnow.wallet.domain.enums.TransactionType;
import com.bidnow.wallet.dto.request.DepositRequest;
import com.bidnow.wallet.dto.response.DepositResponse;
import com.bidnow.wallet.dto.response.TransactionResponse;
import com.bidnow.wallet.dto.response.WalletResponse;

import java.time.LocalDate;
import java.util.UUID;

public interface WalletService {
    void createWalletIfAbsent(UUID userId);
    WalletResponse getMyWallet(UUID userId);
    DepositResponse deposit(UUID userId, DepositRequest request);
    PageResponse<TransactionResponse> getTransactions(UUID userId, TransactionType type,
                                                      LocalDate startDate, LocalDate endDate,
                                                      int page, int size);
}
```

- [ ] **Step 5: Implement getTransactions() in WalletServiceImpl**

Add the following imports and method to `WalletServiceImpl`. The class header and existing methods are unchanged — add only the new imports and the two new methods shown:

```java
// New imports to add to WalletServiceImpl:
import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.specification.SearchOperator;
import com.bidnow.common.specification.SpecificationBuilder;
import com.bidnow.wallet.domain.enums.TransactionType;
import com.bidnow.wallet.dto.response.TransactionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import java.time.LocalDate;
import java.time.LocalDateTime;

// New methods to add to WalletServiceImpl:

@Override
@Transactional(readOnly = true)
public PageResponse<TransactionResponse> getTransactions(UUID userId, TransactionType type,
                                                          LocalDate startDate, LocalDate endDate,
                                                          int page, int size) {
    Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new NotFoundException(
                    "Wallet not found for userId: " + userId, ErrorCodes.NOT_FOUND));

    LocalDateTime from = startDate != null ? startDate.atStartOfDay() : null;
    LocalDateTime to = endDate != null ? endDate.plusDays(1).atStartOfDay() : null;

    Specification<Transaction> spec = SpecificationBuilder.<Transaction>forEntity()
            .with("walletId", SearchOperator.EQUAL, wallet.getId())
            .withIfPresent("type", SearchOperator.EQUAL, type)
            .withBetweenIfPresent("createdAt", from, to)
            .build();

    Page<Transaction> result = transactionRepository.findAll(spec,
            PageRequest.of(page, size, Sort.by("createdAt").descending()));

    return PageResponse.of(result.map(this::toTransactionResponse));
}

private TransactionResponse toTransactionResponse(Transaction tx) {
    return TransactionResponse.builder()
            .id(tx.getId())
            .type(tx.getType().name())
            .amount(tx.getAmount())
            .availableBalanceBefore(tx.getAvailableBalanceBefore())
            .availableBalanceAfter(tx.getAvailableBalanceAfter())
            .description(tx.getDescription())
            .status(tx.getStatus().name())
            .createdAt(tx.getCreatedAt())
            .build();
}
```

- [ ] **Step 6: Run service tests — expect all pass**

```bash
mvn test -pl wallet-service -Dtest=WalletServiceImplTest --no-transfer-progress
```
Expected: All 10 tests PASS.

- [ ] **Step 7: Add GET /transactions to WalletController**

Add the following imports and method to `WalletController` (existing methods unchanged):

```java
// New imports to add to WalletController:
import com.bidnow.common.dto.PageResponse;
import com.bidnow.wallet.domain.enums.TransactionType;
import com.bidnow.wallet.dto.response.TransactionResponse;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

// New method to add to WalletController:

@GetMapping("/transactions")
public ResponseEntity<BaseResponse<PageResponse<TransactionResponse>>> getTransactions(
        @AuthenticatedUserId UUID userId,
        @RequestParam(required = false) TransactionType type,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size) {
    return ResponseEntity.ok(BaseResponse.success(
            walletService.getTransactions(userId, type, startDate, endDate, page, size)));
}
```

- [ ] **Step 8: Add getTransactions controller tests**

Add the following to `WalletControllerTest` — append inside the class. Also add the new imports shown:

```java
// New imports to add to WalletControllerTest:
import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.dto.PaginationMeta;
import com.bidnow.wallet.domain.enums.TransactionType;
import com.bidnow.wallet.dto.response.TransactionResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// New test methods:

@Test
void getTransactions_noFilters_returns200WithPagedResults() {
    UUID userId = UUID.randomUUID();
    TransactionResponse tx = TransactionResponse.builder()
            .id(UUID.randomUUID())
            .type("DEPOSIT")
            .amount(new BigDecimal("500.00"))
            .availableBalanceBefore(new BigDecimal("100.00"))
            .availableBalanceAfter(new BigDecimal("600.00"))
            .status("COMPLETED")
            .createdAt(LocalDateTime.now())
            .build();
    PageResponse<TransactionResponse> pageResponse = PageResponse.<TransactionResponse>builder()
            .data(List.of(tx))
            .pagination(PaginationMeta.builder()
                    .page(0).limit(10).total(1L).totalPages(1).hasNext(false).hasPrev(false)
                    .build())
            .build();
    when(walletService.getTransactions(userId, null, null, null, 0, 10)).thenReturn(pageResponse);

    ResponseEntity<BaseResponse<PageResponse<TransactionResponse>>> response =
            walletController.getTransactions(userId, null, null, null, 0, 10);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getData().getData()).hasSize(1);
    assertThat(response.getBody().getData().getData().get(0).getType()).isEqualTo("DEPOSIT");
    assertThat(response.getBody().getData().getPagination().getTotal()).isEqualTo(1L);
}

@Test
void getTransactions_withTypeFilter_delegatesToServiceWithType() {
    UUID userId = UUID.randomUUID();
    PageResponse<TransactionResponse> empty = PageResponse.<TransactionResponse>builder()
            .data(List.of())
            .pagination(PaginationMeta.builder()
                    .page(0).limit(10).total(0L).totalPages(0).hasNext(false).hasPrev(false)
                    .build())
            .build();
    when(walletService.getTransactions(userId, TransactionType.DEPOSIT, null, null, 0, 10))
            .thenReturn(empty);

    ResponseEntity<BaseResponse<PageResponse<TransactionResponse>>> response =
            walletController.getTransactions(userId, TransactionType.DEPOSIT, null, null, 0, 10);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(walletService).getTransactions(userId, TransactionType.DEPOSIT, null, null, 0, 10);
}
```

- [ ] **Step 9: Run all wallet-service tests**

```bash
mvn test -pl wallet-service --no-transfer-progress
```
Expected: All 14 tests PASS.

- [ ] **Step 10: Commit**

```bash
git add wallet-service/src/main/java/com/bidnow/wallet/dto/response/TransactionResponse.java \
        wallet-service/src/main/java/com/bidnow/wallet/service/WalletService.java \
        wallet-service/src/main/java/com/bidnow/wallet/service/impl/WalletServiceImpl.java \
        wallet-service/src/main/java/com/bidnow/wallet/controller/WalletController.java \
        wallet-service/src/test/java/com/bidnow/wallet/service/impl/WalletServiceImplTest.java \
        wallet-service/src/test/java/com/bidnow/wallet/controller/WalletControllerTest.java
git commit -m "feat(wallet): implement transaction history endpoint [WALLET-302]"
```
