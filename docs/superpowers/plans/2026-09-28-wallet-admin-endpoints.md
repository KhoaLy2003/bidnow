# WALLET-307 Wallet Admin Endpoints Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give ADMIN users wallet visibility and controls under `/api/v1/admin/wallets`: platform stats, a platform-wide transaction list, a user wallet detail view, freeze and unfreeze, and a manual refund funded by the platform wallet. Every admin action is audited.

**Architecture:** A new `WalletAdminController` (class-level `@PreAuthorize("hasRole('ADMIN')")`) delegates to a new `WalletAdminService`. The service reads through new repository queries.
- **Freeze and unfreeze** toggle the existing `SUSPENDED`/`ACTIVE` status under the wallet row lock.
- **Manual refund** locks the user wallet and the platform wallet in ascending id order. It checks for an earlier refund under those locks, then moves the amount from the platform to the user, with a REFUND ledger row on each side that carries `{adminId, reason, originalTransactionId, direction}` metadata.
- **Audit:** `@Audit(ADMIN_ACTION)` produces an `AuditApplicationEvent`. A new `WalletAuditEventListener` forwards it to Kafka `audit-events`.
- **Gateway:** the api-gateway routes `/api/v1/admin/wallets/**` to wallet-service.

**Tech Stack:** Java 17, Spring Boot 3.2.4, Spring Security (method security), Spring Data JPA, Spring Kafka, Jackson, Lombok, JUnit 5, Mockito, AssertJ, standalone MockMvc.

**Spec:** `docs/superpowers/specs/2026-09-28-wallet-admin-endpoints-design.md`

## Global Constraints

- **Do not run `git add`, `git commit`, `git stash`, `git reset`, or any other git write.** Leave all changes uncommitted on branch `feature/wallet-deposit-lock`.
- Scope: wallet-service, plus one line in `backend/api-gateway/src/main/resources/application.yml`. Do not change the `common` module or any other service.
- Base path is `/api/v1/admin/wallets`. All responses are `ResponseEntity<BaseResponse<T>>`. Put `@PreAuthorize("hasRole('ADMIN')")` on the controller class and Swagger `@Operation` on each endpoint.
- Paging uses `page` (default `0`) and `size` (default `50`), sorted by `createdAt` descending.
- Freeze sets `WalletStatus.SUSPENDED` and unfreeze sets `WalletStatus.ACTIVE`. Both are idempotent (no save if already in the target status). Freezing the platform wallet is a 400 with `INVALID_INPUT`.
- A transaction is **refundable** only when its `type ∈ {PAYMENT, FORFEIT}`, its `walletId` is not the platform wallet's id, and `availableBalanceAfter.compareTo(availableBalanceBefore) <= 0`.
- The refund covers the full original amount, once per transaction. A duplicate means a `REFUND` row already exists with `referenceId` equal to the original transaction id; return 409 `TRANSACTION_ALREADY_REFUNDED`. Check this after both wallet row locks are taken.
- The refund is funded by the platform wallet (the wallet of `wallet.platform-user-id`). If its available balance is less than the amount, throw `InsufficientBalanceException(available, amount)`, which gives 400.
- Refund lock order: `findByIdForUpdate` on the user wallet and the platform wallet, lower `UUID` (`compareTo`) first.
- Refund ledger rows are written platform (DEBIT) first, then user (CREDIT). Both have `type=REFUND`, `status=COMPLETED`, `referenceId = original id`, description `"Manual refund of transaction <id>"`, and `metadata` JSON `{"adminId","reason","originalTransactionId","direction"}`.
- New error codes: `TRANSACTION_NOT_FOUND` (404), `TRANSACTION_NOT_REFUNDABLE` (400), `TRANSACTION_ALREADY_REFUNDED` (409). Reused: `WALLET_NOT_FOUND` (404), `INSUFFICIENT_BALANCE` (400), and `ErrorCodes.INVALID_INPUT` (400).
- Audit: `@Audit(action = AuditAction.ADMIN_ACTION, ...)` on freeze, unfreeze and refund. A listener sends `AuditLogEvent` to the Kafka topic `audit-events`, keyed by `correlationId` (falling back to `entityId`).
- Run Maven from `backend/`: `mvn -q -pl wallet-service -am test …`.

---

## File Map

Paths are relative to `backend/wallet-service/` unless they start with `backend/api-gateway/` or `docs/`. `…` means `src/main/java/com/bidnow/wallet`, and `T…` means `src/test/java/com/bidnow/wallet`.

| File | Action | Task |
|---|---|---|
| `…/constant/WalletErrorCodes.java` | Modify (+3 codes) | 1 |
| `…/repository/WalletRepository.java` | Modify (+2 queries) | 1 |
| `…/repository/TransactionRepository.java` | Modify (+2 queries) | 1 |
| `…/repository/DepositLockRepository.java` | Modify (+2 queries) | 1 |
| `…/dto/response/WalletStatsResponse.java` | Create | 1 |
| `…/dto/response/AdminWalletResponse.java` | Create | 1 |
| `…/dto/response/AdminTransactionResponse.java` | Create | 1 |
| `…/dto/response/AdminDepositLockResponse.java` | Create | 1 |
| `…/dto/response/AdminWalletDetailResponse.java` | Create | 1 |
| `…/service/WalletAdminService.java` | Create (read ops) | 1 (+2) |
| `…/service/impl/WalletAdminServiceImpl.java` | Create (read ops) | 1 (+2) |
| `T…/service/impl/WalletAdminServiceImplTest.java` | Create | 1 (+2) |
| `…/dto/request/ManualRefundRequest.java` | Create | 2 |
| `…/dto/response/ManualRefundResponse.java` | Create | 2 |
| `…/controller/WalletAdminController.java` | Create | 3 |
| `T…/controller/WalletAdminControllerTest.java` | Create | 3 |
| `backend/api-gateway/src/main/resources/application.yml` | Modify (1 line) | 3 |
| `…/kafka/WalletAuditEventListener.java` | Create | 4 |
| `T…/kafka/WalletAuditEventListenerTest.java` | Create | 4 |
| `docs/architecture.md`, `docs/epics/wallet/epic.md` | Modify | 5 |

---

### Task 1: Read operations — stats, wallet detail, transaction list

**Files:** see the File Map rows for Task 1.

**Interfaces:**
- Consumes (existing):
  - `Wallet`, `Transaction` (builder includes `id, walletId, type, amount, availableBalanceBefore, availableBalanceAfter, referenceId, description, status, metadata, createdAt`), `DepositLock`
  - enums: `WalletStatus`, `DepositLockStatus`, `TransactionType`
  - `SpecificationBuilder`/`SearchOperator` (common)
  - `PageResponse.of(Page<T>)`, whose content is `getData()`
  - `NotFoundException(String, String)`
  - `WalletRepository.findByUserId`
  - `TransactionRepository`, which extends `JpaSpecificationExecutor<Transaction>`
- Produces:
  - `WalletErrorCodes.TRANSACTION_NOT_FOUND`, `TRANSACTION_NOT_REFUNDABLE`, `TRANSACTION_ALREADY_REFUNDED`
  - `WalletRepository.countByStatusAndUserIdNot(WalletStatus, UUID): long`
  - `WalletRepository.sumLockedBalance(): BigDecimal`
  - `TransactionRepository.findTop20ByWalletIdOrderByCreatedAtDesc(UUID): List<Transaction>`
  - `TransactionRepository.existsByTypeAndReferenceId(TransactionType, UUID): boolean`
  - `DepositLockRepository.countByStatus(DepositLockStatus): long`
  - `DepositLockRepository.findByWalletIdAndStatus(UUID, DepositLockStatus): List<DepositLock>`
  - DTOs, all Lombok `@Data @Builder`:
    - `WalletStatsResponse { BigDecimal platformWalletBalance; long totalActiveWallets; BigDecimal totalLockedBalance; long totalActiveDepositLocks; }`
    - `AdminWalletResponse { UUID walletId; UUID userId; BigDecimal totalBalance, availableBalance, lockedBalance; String currency; String status; }`
    - `AdminTransactionResponse { UUID id; UUID walletId; UUID userId; String type; BigDecimal amount, availableBalanceBefore, availableBalanceAfter; UUID referenceId; String description; String status; String metadata; LocalDateTime createdAt; }`
    - `AdminDepositLockResponse { UUID id; UUID auctionId; BigDecimal amount; String status; LocalDateTime lockedAt; }`
    - `AdminWalletDetailResponse { AdminWalletResponse wallet; List<AdminTransactionResponse> recentTransactions; List<AdminDepositLockResponse> activeDepositLocks; }`
  - `WalletAdminService`: `getStats()`, `listTransactions(TransactionType type, int page, int size): PageResponse<AdminTransactionResponse>`, `getWalletDetail(UUID userId): AdminWalletDetailResponse`
  - `WalletAdminServiceImpl` (`@Service @RequiredArgsConstructor`), with the final fields **in this order**: `walletRepository, transactionRepository, depositLockRepository, objectMapper` (`com.fasterxml.jackson.databind.ObjectMapper`). Also the non-final `@Value("${wallet.platform-user-id}") private String platformUserId;` and the package-private helpers `toWalletResponse(Wallet)` and `toTransactionResponse(Transaction, UUID userId)`.

- [ ] **Step 1: Error codes, repository queries and DTOs**

In `…/constant/WalletErrorCodes.java`, add these after `SELLER_WALLET_NOT_FOUND`:

```java
    public static final String TRANSACTION_NOT_FOUND = "TRANSACTION_NOT_FOUND";
    public static final String TRANSACTION_NOT_REFUNDABLE = "TRANSACTION_NOT_REFUNDABLE";
    public static final String TRANSACTION_ALREADY_REFUNDED = "TRANSACTION_ALREADY_REFUNDED";
```

In `…/repository/WalletRepository.java`, add the imports `com.bidnow.wallet.domain.enums.WalletStatus` and `java.math.BigDecimal`, then add:

```java
    long countByStatusAndUserIdNot(WalletStatus status, UUID userId);

    @Query("SELECT COALESCE(SUM(w.lockedBalance), 0) FROM Wallet w")
    BigDecimal sumLockedBalance();
```

Replace the body of `…/repository/TransactionRepository.java` so the file reads:

```java
package com.bidnow.wallet.repository;

import com.bidnow.wallet.domain.entity.Transaction;
import com.bidnow.wallet.domain.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    List<Transaction> findTop20ByWalletIdOrderByCreatedAtDesc(UUID walletId);

    boolean existsByTypeAndReferenceId(TransactionType type, UUID referenceId);
}
```

In `…/repository/DepositLockRepository.java`, add:

```java
    long countByStatus(DepositLockStatus status);

    List<DepositLock> findByWalletIdAndStatus(UUID walletId, DepositLockStatus status);
```

Create these five DTOs in `…/dto/response/`. Each is `@Data @Builder` and has exactly the fields listed under Interfaces, as private fields. Here is `WalletStatsResponse.java` in full; write the other four the same way:

```java
package com.bidnow.wallet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class WalletStatsResponse {
    private BigDecimal platformWalletBalance;
    private long totalActiveWallets;
    private BigDecimal totalLockedBalance;
    private long totalActiveDepositLocks;
}
```

`AdminWalletResponse.java`:

```java
package com.bidnow.wallet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class AdminWalletResponse {
    private UUID walletId;
    private UUID userId;
    private BigDecimal totalBalance;
    private BigDecimal availableBalance;
    private BigDecimal lockedBalance;
    private String currency;
    private String status;
}
```

`AdminTransactionResponse.java`:

```java
package com.bidnow.wallet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class AdminTransactionResponse {
    private UUID id;
    private UUID walletId;
    private UUID userId;
    private String type;
    private BigDecimal amount;
    private BigDecimal availableBalanceBefore;
    private BigDecimal availableBalanceAfter;
    private UUID referenceId;
    private String description;
    private String status;
    private String metadata;
    private LocalDateTime createdAt;
}
```

`AdminDepositLockResponse.java`:

```java
package com.bidnow.wallet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class AdminDepositLockResponse {
    private UUID id;
    private UUID auctionId;
    private BigDecimal amount;
    private String status;
    private LocalDateTime lockedAt;
}
```

`AdminWalletDetailResponse.java`:

```java
package com.bidnow.wallet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdminWalletDetailResponse {
    private AdminWalletResponse wallet;
    private List<AdminTransactionResponse> recentTransactions;
    private List<AdminDepositLockResponse> activeDepositLocks;
}
```

`…/service/WalletAdminService.java`:

```java
package com.bidnow.wallet.service;

import com.bidnow.common.dto.PageResponse;
import com.bidnow.wallet.domain.enums.TransactionType;
import com.bidnow.wallet.dto.response.AdminTransactionResponse;
import com.bidnow.wallet.dto.response.AdminWalletDetailResponse;
import com.bidnow.wallet.dto.response.WalletStatsResponse;

import java.util.UUID;

public interface WalletAdminService {

    WalletStatsResponse getStats();

    PageResponse<AdminTransactionResponse> listTransactions(TransactionType type, int page, int size);

    AdminWalletDetailResponse getWalletDetail(UUID userId);
}
```

- [ ] **Step 2: Write the failing tests**

`T…/service/impl/WalletAdminServiceImplTest.java`:

```java
package com.bidnow.wallet.service.impl;

import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.common.util.AuditContextHolder;
import com.bidnow.wallet.domain.entity.DepositLock;
import com.bidnow.wallet.domain.entity.Transaction;
import com.bidnow.wallet.domain.entity.Wallet;
import com.bidnow.wallet.domain.enums.DepositLockStatus;
import com.bidnow.wallet.domain.enums.TransactionStatus;
import com.bidnow.wallet.domain.enums.TransactionType;
import com.bidnow.wallet.domain.enums.WalletStatus;
import com.bidnow.wallet.dto.response.AdminTransactionResponse;
import com.bidnow.wallet.dto.response.AdminWalletDetailResponse;
import com.bidnow.wallet.dto.response.WalletStatsResponse;
import com.bidnow.wallet.repository.DepositLockRepository;
import com.bidnow.wallet.repository.TransactionRepository;
import com.bidnow.wallet.repository.WalletRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletAdminServiceImplTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private DepositLockRepository depositLockRepository;

    private WalletAdminServiceImpl service;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID platformUserId = UUID.randomUUID();
    private UUID platformWalletId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private UUID userWalletId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new WalletAdminServiceImpl(walletRepository, transactionRepository, depositLockRepository, objectMapper);
        ReflectionTestUtils.setField(service, "platformUserId", platformUserId.toString());
    }

    @AfterEach
    void clearAuditContext() {
        AuditContextHolder.clear();
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private Wallet wallet(UUID id, UUID owner, String total, String available, String locked, WalletStatus status) {
        return Wallet.builder()
                .id(id).userId(owner)
                .totalBalance(bd(total)).availableBalance(bd(available)).lockedBalance(bd(locked))
                .currency("USD").status(status)
                .build();
    }

    private Transaction tx(TransactionType type, UUID walletId, String amount, String before, String after) {
        return Transaction.builder()
                .id(UUID.randomUUID()).walletId(walletId).type(type)
                .amount(bd(amount)).availableBalanceBefore(bd(before)).availableBalanceAfter(bd(after))
                .referenceId(UUID.randomUUID()).description("t").status(TransactionStatus.COMPLETED)
                .createdAt(LocalDateTime.of(2026, 9, 28, 10, 0))
                .build();
    }

    // ── getStats ──────────────────────────────────────────────────────────────

    @Test
    void getStats_aggregatesPlatformBalanceWalletsLockedAndLocks() {
        when(walletRepository.findByUserId(platformUserId))
                .thenReturn(Optional.of(wallet(platformWalletId, platformUserId, "950.00", "950.00", "0.00", WalletStatus.ACTIVE)));
        when(walletRepository.countByStatusAndUserIdNot(WalletStatus.ACTIVE, platformUserId)).thenReturn(12L);
        when(walletRepository.sumLockedBalance()).thenReturn(bd("300.00"));
        when(depositLockRepository.countByStatus(DepositLockStatus.LOCKED)).thenReturn(4L);

        WalletStatsResponse stats = service.getStats();

        assertThat(stats.getPlatformWalletBalance()).isEqualByComparingTo("950.00");
        assertThat(stats.getTotalActiveWallets()).isEqualTo(12L);
        assertThat(stats.getTotalLockedBalance()).isEqualByComparingTo("300.00");
        assertThat(stats.getTotalActiveDepositLocks()).isEqualTo(4L);
    }

    @Test
    void getStats_platformWalletMissing_reportsZeroBalance() {
        when(walletRepository.findByUserId(platformUserId)).thenReturn(Optional.empty());
        when(walletRepository.countByStatusAndUserIdNot(WalletStatus.ACTIVE, platformUserId)).thenReturn(0L);
        when(walletRepository.sumLockedBalance()).thenReturn(BigDecimal.ZERO);
        when(depositLockRepository.countByStatus(DepositLockStatus.LOCKED)).thenReturn(0L);

        assertThat(service.getStats().getPlatformWalletBalance()).isEqualByComparingTo("0");
    }

    // ── getWalletDetail ───────────────────────────────────────────────────────

    @Test
    void getWalletDetail_mapsWalletRecentTransactionsAndActiveLocks() {
        Wallet w = wallet(userWalletId, userId, "200.00", "150.00", "50.00", WalletStatus.SUSPENDED);
        Transaction t = tx(TransactionType.DEPOSIT, userWalletId, "200.00", "0.00", "200.00");
        DepositLock lock = DepositLock.builder()
                .id(UUID.randomUUID()).walletId(userWalletId).auctionId(UUID.randomUUID())
                .amount(bd("50.00")).status(DepositLockStatus.LOCKED)
                .lockedAt(LocalDateTime.of(2026, 9, 28, 9, 0))
                .build();
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(w));
        when(transactionRepository.findTop20ByWalletIdOrderByCreatedAtDesc(userWalletId)).thenReturn(List.of(t));
        when(depositLockRepository.findByWalletIdAndStatus(userWalletId, DepositLockStatus.LOCKED)).thenReturn(List.of(lock));

        AdminWalletDetailResponse detail = service.getWalletDetail(userId);

        assertThat(detail.getWallet().getWalletId()).isEqualTo(userWalletId);
        assertThat(detail.getWallet().getUserId()).isEqualTo(userId);
        assertThat(detail.getWallet().getAvailableBalance()).isEqualByComparingTo("150.00");
        assertThat(detail.getWallet().getStatus()).isEqualTo("SUSPENDED");
        assertThat(detail.getRecentTransactions()).hasSize(1);
        assertThat(detail.getRecentTransactions().get(0).getId()).isEqualTo(t.getId());
        assertThat(detail.getRecentTransactions().get(0).getUserId()).isEqualTo(userId);
        assertThat(detail.getRecentTransactions().get(0).getType()).isEqualTo("DEPOSIT");
        assertThat(detail.getActiveDepositLocks()).hasSize(1);
        assertThat(detail.getActiveDepositLocks().get(0).getAuctionId()).isEqualTo(lock.getAuctionId());
        assertThat(detail.getActiveDepositLocks().get(0).getStatus()).isEqualTo("LOCKED");
    }

    @Test
    void getWalletDetail_walletMissing_throwsNotFound() {
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getWalletDetail(userId))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo("WALLET_NOT_FOUND");
    }

    // ── listTransactions ──────────────────────────────────────────────────────

    @Test
    void listTransactions_pagesSortedNewestFirstAndResolvesUserIds() {
        UUID otherWalletId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Transaction a = tx(TransactionType.FORFEIT, userWalletId, "50.00", "0.00", "0.00");
        Transaction b = tx(TransactionType.FORFEIT, otherWalletId, "20.00", "0.00", "0.00");
        when(transactionRepository.findAll(ArgumentMatchers.<Specification<Transaction>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(a, b), PageRequest.of(1, 50), 52));
        when(walletRepository.findAllById(any())).thenReturn(List.of(
                wallet(userWalletId, userId, "0", "0", "0", WalletStatus.ACTIVE),
                wallet(otherWalletId, otherUserId, "0", "0", "0", WalletStatus.ACTIVE)));

        PageResponse<AdminTransactionResponse> result = service.listTransactions(TransactionType.FORFEIT, 1, 50);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepository).findAll(ArgumentMatchers.<Specification<Transaction>>any(), page.capture());
        assertThat(page.getValue()).isEqualTo(PageRequest.of(1, 50, Sort.by("createdAt").descending()));
        assertThat(result.getData()).hasSize(2);
        assertThat(result.getData().get(0).getUserId()).isEqualTo(userId);
        assertThat(result.getData().get(1).getUserId()).isEqualTo(otherUserId);
        assertThat(result.getPagination().getTotal()).isEqualTo(52);
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run (from `backend/`): `mvn -q -pl wallet-service -am test -Dtest=WalletAdminServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR, because `WalletAdminServiceImpl` doesn't exist yet.

- [ ] **Step 4: Implement**

`…/service/impl/WalletAdminServiceImpl.java`:

```java
package com.bidnow.wallet.service.impl;

import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.common.specification.SearchOperator;
import com.bidnow.common.specification.SpecificationBuilder;
import com.bidnow.wallet.constant.WalletErrorCodes;
import com.bidnow.wallet.domain.entity.DepositLock;
import com.bidnow.wallet.domain.entity.Transaction;
import com.bidnow.wallet.domain.entity.Wallet;
import com.bidnow.wallet.domain.enums.DepositLockStatus;
import com.bidnow.wallet.domain.enums.TransactionType;
import com.bidnow.wallet.domain.enums.WalletStatus;
import com.bidnow.wallet.dto.response.AdminDepositLockResponse;
import com.bidnow.wallet.dto.response.AdminTransactionResponse;
import com.bidnow.wallet.dto.response.AdminWalletDetailResponse;
import com.bidnow.wallet.dto.response.AdminWalletResponse;
import com.bidnow.wallet.dto.response.WalletStatsResponse;
import com.bidnow.wallet.repository.DepositLockRepository;
import com.bidnow.wallet.repository.TransactionRepository;
import com.bidnow.wallet.repository.WalletRepository;
import com.bidnow.wallet.service.WalletAdminService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletAdminServiceImpl implements WalletAdminService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final DepositLockRepository depositLockRepository;
    private final ObjectMapper objectMapper;

    @Value("${wallet.platform-user-id}")
    private String platformUserId;

    @Override
    @Transactional(readOnly = true)
    public WalletStatsResponse getStats() {
        UUID platformUser = UUID.fromString(platformUserId);
        BigDecimal platformBalance = walletRepository.findByUserId(platformUser)
                .map(Wallet::getTotalBalance)
                .orElse(BigDecimal.ZERO);
        return WalletStatsResponse.builder()
                .platformWalletBalance(platformBalance)
                .totalActiveWallets(walletRepository.countByStatusAndUserIdNot(WalletStatus.ACTIVE, platformUser))
                .totalLockedBalance(walletRepository.sumLockedBalance())
                .totalActiveDepositLocks(depositLockRepository.countByStatus(DepositLockStatus.LOCKED))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminTransactionResponse> listTransactions(TransactionType type, int page, int size) {
        Specification<Transaction> spec = SpecificationBuilder.<Transaction>forEntity()
                .withIfPresent("type", SearchOperator.EQUAL, type)
                .build();
        Page<Transaction> result = transactionRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        List<UUID> walletIds = result.getContent().stream().map(Transaction::getWalletId).distinct().toList();
        Map<UUID, UUID> userIdByWalletId = walletRepository.findAllById(walletIds).stream()
                .collect(Collectors.toMap(Wallet::getId, Wallet::getUserId));
        return PageResponse.of(result.map(tx -> toTransactionResponse(tx, userIdByWalletId.get(tx.getWalletId()))));
    }

    @Override
    @Transactional(readOnly = true)
    public AdminWalletDetailResponse getWalletDetail(UUID userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> walletNotFound(userId));
        List<AdminTransactionResponse> recent = transactionRepository
                .findTop20ByWalletIdOrderByCreatedAtDesc(wallet.getId()).stream()
                .map(tx -> toTransactionResponse(tx, wallet.getUserId()))
                .toList();
        List<AdminDepositLockResponse> locks = depositLockRepository
                .findByWalletIdAndStatus(wallet.getId(), DepositLockStatus.LOCKED).stream()
                .map(this::toDepositLockResponse)
                .toList();
        return AdminWalletDetailResponse.builder()
                .wallet(toWalletResponse(wallet))
                .recentTransactions(recent)
                .activeDepositLocks(locks)
                .build();
    }

    AdminWalletResponse toWalletResponse(Wallet wallet) {
        return AdminWalletResponse.builder()
                .walletId(wallet.getId())
                .userId(wallet.getUserId())
                .totalBalance(wallet.getTotalBalance())
                .availableBalance(wallet.getAvailableBalance())
                .lockedBalance(wallet.getLockedBalance())
                .currency(wallet.getCurrency())
                .status(wallet.getStatus().name())
                .build();
    }

    AdminTransactionResponse toTransactionResponse(Transaction tx, UUID userId) {
        return AdminTransactionResponse.builder()
                .id(tx.getId())
                .walletId(tx.getWalletId())
                .userId(userId)
                .type(tx.getType().name())
                .amount(tx.getAmount())
                .availableBalanceBefore(tx.getAvailableBalanceBefore())
                .availableBalanceAfter(tx.getAvailableBalanceAfter())
                .referenceId(tx.getReferenceId())
                .description(tx.getDescription())
                .status(tx.getStatus().name())
                .metadata(tx.getMetadata())
                .createdAt(tx.getCreatedAt())
                .build();
    }

    private AdminDepositLockResponse toDepositLockResponse(DepositLock lock) {
        return AdminDepositLockResponse.builder()
                .id(lock.getId())
                .auctionId(lock.getAuctionId())
                .amount(lock.getAmount())
                .status(lock.getStatus().name())
                .lockedAt(lock.getLockedAt())
                .build();
    }

    private NotFoundException walletNotFound(UUID userId) {
        return new NotFoundException("Wallet not found for userId: " + userId, WalletErrorCodes.WALLET_NOT_FOUND);
    }
}
```

The `objectMapper` field is unused until Task 2, which is expected.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn -q -pl wallet-service -am test -Dtest=WalletAdminServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (5 tests).

---

### Task 2: Freeze, unfreeze, and manual refund

**Files:** Create `…/dto/request/ManualRefundRequest.java` and `…/dto/response/ManualRefundResponse.java`. Modify `WalletAdminService`, `WalletAdminServiceImpl` and `WalletAdminServiceImplTest` (from Task 1).

**Interfaces:**
- Consumes:
  - From Task 1: `WalletAdminServiceImpl` and its fields and helpers, `WalletErrorCodes.TRANSACTION_*`, `TransactionRepository.existsByTypeAndReferenceId`, and the test class fixtures (`wallet(...)`, `tx(...)`, `bd`, `platformUserId`, `platformWalletId`, `userId`, `userWalletId`, `objectMapper`).
  - Existing: `WalletRepository.findByUserIdForUpdate(UUID)`, `findByIdForUpdate(UUID)` and `findIdByUserId(UUID)`; `InsufficientBalanceException(BigDecimal, BigDecimal)`; `ConflictException(String, String)`.
  - From common: `BadRequestException(String, String)`, `NotFoundException`, `ErrorCodes.INVALID_INPUT`, `@Audit` (`com.bidnow.common.annotation.Audit`), `AuditAction.ADMIN_ACTION` (`com.bidnow.common.enums.AuditAction`) and `AuditContextHolder` (`com.bidnow.common.util`).
- Produces:
  - `ManualRefundRequest` (`@Data @NoArgsConstructor @AllArgsConstructor`, with `@NotBlank(message = "reason is required") String reason`)
  - `ManualRefundResponse` (`@Data @Builder`, `UUID refundTransactionId; BigDecimal amount; UUID userId; BigDecimal availableBalance;`)
  - `WalletAdminService.freezeWallet(UUID userId): AdminWalletResponse`
  - `WalletAdminService.unfreezeWallet(UUID userId): AdminWalletResponse`
  - `WalletAdminService.refundTransaction(UUID adminId, UUID transactionId, String reason): ManualRefundResponse`

- [ ] **Step 1: DTOs and interface methods**

`…/dto/request/ManualRefundRequest.java`:

```java
package com.bidnow.wallet.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ManualRefundRequest {

    @NotBlank(message = "reason is required")
    private String reason;
}
```

`…/dto/response/ManualRefundResponse.java`:

```java
package com.bidnow.wallet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class ManualRefundResponse {
    private UUID refundTransactionId;
    private BigDecimal amount;
    private UUID userId;
    private BigDecimal availableBalance;
}
```

In `…/service/WalletAdminService.java`, add the imports `com.bidnow.wallet.dto.response.AdminWalletResponse` and `com.bidnow.wallet.dto.response.ManualRefundResponse`, then add:

```java
    AdminWalletResponse freezeWallet(UUID userId);

    AdminWalletResponse unfreezeWallet(UUID userId);

    ManualRefundResponse refundTransaction(UUID adminId, UUID transactionId, String reason);
```

- [ ] **Step 2: Write the failing tests**

In `T…/service/impl/WalletAdminServiceImplTest.java`, add these imports:

```java
import com.bidnow.common.exception.BadRequestException;
import com.bidnow.wallet.dto.response.AdminWalletResponse;
import com.bidnow.wallet.dto.response.ManualRefundResponse;
import com.bidnow.wallet.exception.ConflictException;
import com.bidnow.wallet.exception.InsufficientBalanceException;
import com.fasterxml.jackson.databind.JsonNode;
import org.mockito.InOrder;
```

Also add these static imports: `org.mockito.Mockito.inOrder`, `org.mockito.Mockito.never` and `org.mockito.Mockito.times`. Then append the following before the final closing brace:

```java
    // ── freeze / unfreeze ─────────────────────────────────────────────────────

    @Test
    void freezeWallet_active_setsSuspendedAndRecordsAuditStates() {
        Wallet w = wallet(userWalletId, userId, "100.00", "100.00", "0.00", WalletStatus.ACTIVE);
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(w));

        AdminWalletResponse result = service.freezeWallet(userId);

        assertThat(w.getStatus()).isEqualTo(WalletStatus.SUSPENDED);
        verify(walletRepository).save(w);
        assertThat(result.getStatus()).isEqualTo("SUSPENDED");
        assertThat(((Wallet) AuditContextHolder.getOldState()).getStatus()).isEqualTo(WalletStatus.ACTIVE);
        assertThat(((Wallet) AuditContextHolder.getNewState()).getStatus()).isEqualTo(WalletStatus.SUSPENDED);
    }

    @Test
    void freezeWallet_alreadySuspended_isIdempotentWithoutSave() {
        Wallet w = wallet(userWalletId, userId, "100.00", "100.00", "0.00", WalletStatus.SUSPENDED);
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(w));

        AdminWalletResponse result = service.freezeWallet(userId);

        assertThat(result.getStatus()).isEqualTo("SUSPENDED");
        verify(walletRepository, never()).save(any());
    }

    @Test
    void freezeWallet_platformWallet_rejected() {
        assertThatThrownBy(() -> service.freezeWallet(platformUserId))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode").isEqualTo("INVALID_INPUT");
        verify(walletRepository, never()).findByUserIdForUpdate(any());
    }

    @Test
    void freezeWallet_walletMissing_throwsNotFound() {
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.freezeWallet(userId))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo("WALLET_NOT_FOUND");
    }

    @Test
    void unfreezeWallet_suspended_setsActive() {
        Wallet w = wallet(userWalletId, userId, "100.00", "100.00", "0.00", WalletStatus.SUSPENDED);
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(w));

        AdminWalletResponse result = service.unfreezeWallet(userId);

        assertThat(w.getStatus()).isEqualTo(WalletStatus.ACTIVE);
        verify(walletRepository).save(w);
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
    }

    // ── refundTransaction ─────────────────────────────────────────────────────

    private void stubRefund(Transaction original, Wallet user, Wallet platform) {
        when(transactionRepository.findById(original.getId())).thenReturn(Optional.of(original));
        when(walletRepository.findIdByUserId(platformUserId)).thenReturn(Optional.of(platformWalletId));
        when(walletRepository.findByIdForUpdate(userWalletId)).thenReturn(Optional.of(user));
        when(walletRepository.findByIdForUpdate(platformWalletId)).thenReturn(Optional.of(platform));
    }

    @Test
    void refundTransaction_forfeit_movesAmountFromPlatformToUserWithAuditedLedgerRows() throws Exception {
        UUID adminId = UUID.randomUUID();
        Transaction original = tx(TransactionType.FORFEIT, userWalletId, "50.00", "600.00", "600.00");
        Wallet user = wallet(userWalletId, userId, "100.00", "100.00", "0.00", WalletStatus.SUSPENDED);
        Wallet platform = wallet(platformWalletId, platformUserId, "1000.00", "1000.00", "0.00", WalletStatus.ACTIVE);
        stubRefund(original, user, platform);
        when(transactionRepository.existsByTypeAndReferenceId(TransactionType.REFUND, original.getId())).thenReturn(false);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        ManualRefundResponse result = service.refundTransaction(adminId, original.getId(), "Customer dispute");

        assertThat(platform.getAvailableBalance()).isEqualByComparingTo("950.00");
        assertThat(platform.getTotalBalance()).isEqualByComparingTo("950.00");
        assertThat(user.getAvailableBalance()).isEqualByComparingTo("150.00");
        assertThat(user.getTotalBalance()).isEqualByComparingTo("150.00");
        verify(walletRepository).save(platform);
        verify(walletRepository).save(user);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).save(captor.capture());
        Transaction debit = captor.getAllValues().get(0);
        Transaction credit = captor.getAllValues().get(1);
        assertThat(debit.getWalletId()).isEqualTo(platformWalletId);
        assertThat(debit.getType()).isEqualTo(TransactionType.REFUND);
        assertThat(debit.getAmount()).isEqualByComparingTo("50.00");
        assertThat(debit.getAvailableBalanceBefore()).isEqualByComparingTo("1000.00");
        assertThat(debit.getAvailableBalanceAfter()).isEqualByComparingTo("950.00");
        assertThat(debit.getReferenceId()).isEqualTo(original.getId());
        assertThat(credit.getWalletId()).isEqualTo(userWalletId);
        assertThat(credit.getAvailableBalanceBefore()).isEqualByComparingTo("100.00");
        assertThat(credit.getAvailableBalanceAfter()).isEqualByComparingTo("150.00");
        assertThat(credit.getReferenceId()).isEqualTo(original.getId());
        JsonNode meta = objectMapper.readTree(credit.getMetadata());
        assertThat(meta.get("adminId").asText()).isEqualTo(adminId.toString());
        assertThat(meta.get("reason").asText()).isEqualTo("Customer dispute");
        assertThat(meta.get("originalTransactionId").asText()).isEqualTo(original.getId().toString());
        assertThat(meta.get("direction").asText()).isEqualTo("CREDIT");
        assertThat(objectMapper.readTree(debit.getMetadata()).get("direction").asText()).isEqualTo("DEBIT");

        assertThat(result.getRefundTransactionId()).isEqualTo(credit.getId());
        assertThat(result.getAmount()).isEqualByComparingTo("50.00");
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getAvailableBalance()).isEqualByComparingTo("150.00");
    }

    @Test
    void refundTransaction_notFound_throws404() {
        UUID id = UUID.randomUUID();
        when(transactionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refundTransaction(UUID.randomUUID(), id, "r"))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo("TRANSACTION_NOT_FOUND");
    }

    @Test
    void refundTransaction_nonRefundableKinds_rejectedWithoutLocking() {
        when(walletRepository.findIdByUserId(platformUserId)).thenReturn(Optional.of(platformWalletId));
        List<Transaction> nonRefundable = List.of(
                tx(TransactionType.DEPOSIT, userWalletId, "100.00", "0.00", "100.00"),
                tx(TransactionType.HOLD, userWalletId, "50.00", "100.00", "50.00"),
                tx(TransactionType.REFUND, userWalletId, "50.00", "0.00", "50.00"),
                tx(TransactionType.PAYMENT, userWalletId, "500.00", "0.00", "500.00"),      // seller proceeds
                tx(TransactionType.FORFEIT, platformWalletId, "50.00", "0.00", "50.00"));   // platform credit
        for (Transaction t : nonRefundable) {
            when(transactionRepository.findById(t.getId())).thenReturn(Optional.of(t));
            assertThatThrownBy(() -> service.refundTransaction(UUID.randomUUID(), t.getId(), "r"))
                    .isInstanceOf(BadRequestException.class)
                    .extracting("errorCode").isEqualTo("TRANSACTION_NOT_REFUNDABLE");
        }
        verify(walletRepository, never()).findByIdForUpdate(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void refundTransaction_alreadyRefunded_throws409AndChangesNothing() {
        Transaction original = tx(TransactionType.PAYMENT, userWalletId, "500.00", "600.00", "100.00");
        Wallet user = wallet(userWalletId, userId, "100.00", "100.00", "0.00", WalletStatus.ACTIVE);
        Wallet platform = wallet(platformWalletId, platformUserId, "1000.00", "1000.00", "0.00", WalletStatus.ACTIVE);
        stubRefund(original, user, platform);
        when(transactionRepository.existsByTypeAndReferenceId(TransactionType.REFUND, original.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.refundTransaction(UUID.randomUUID(), original.getId(), "r"))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("TRANSACTION_ALREADY_REFUNDED");
        assertThat(platform.getAvailableBalance()).isEqualByComparingTo("1000.00");
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void refundTransaction_platformCannotCover_throwsInsufficientBalance() {
        Transaction original = tx(TransactionType.PAYMENT, userWalletId, "500.00", "600.00", "100.00");
        Wallet user = wallet(userWalletId, userId, "100.00", "100.00", "0.00", WalletStatus.ACTIVE);
        Wallet platform = wallet(platformWalletId, platformUserId, "10.00", "10.00", "0.00", WalletStatus.ACTIVE);
        stubRefund(original, user, platform);
        when(transactionRepository.existsByTypeAndReferenceId(TransactionType.REFUND, original.getId())).thenReturn(false);

        assertThatThrownBy(() -> service.refundTransaction(UUID.randomUUID(), original.getId(), "r"))
                .isInstanceOfSatisfying(InsufficientBalanceException.class, ex -> {
                    assertThat(ex.getAvailableBalance()).isEqualByComparingTo("10.00");
                    assertThat(ex.getRequired()).isEqualByComparingTo("500.00");
                });
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void refundTransaction_locksPlatformFirstWhenPlatformIdIsLower() {
        platformWalletId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        userWalletId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Transaction original = tx(TransactionType.FORFEIT, userWalletId, "50.00", "0.00", "0.00");
        stubRefund(original,
                wallet(userWalletId, userId, "0.00", "0.00", "0.00", WalletStatus.ACTIVE),
                wallet(platformWalletId, platformUserId, "100.00", "100.00", "0.00", WalletStatus.ACTIVE));
        when(transactionRepository.existsByTypeAndReferenceId(TransactionType.REFUND, original.getId())).thenReturn(false);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        service.refundTransaction(UUID.randomUUID(), original.getId(), "r");

        InOrder order = inOrder(walletRepository, transactionRepository);
        order.verify(walletRepository).findByIdForUpdate(platformWalletId);
        order.verify(walletRepository).findByIdForUpdate(userWalletId);
        order.verify(transactionRepository).existsByTypeAndReferenceId(TransactionType.REFUND, original.getId());
    }

    @Test
    void refundTransaction_locksUserFirstWhenUserIdIsLower() {
        userWalletId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        platformWalletId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Transaction original = tx(TransactionType.FORFEIT, userWalletId, "50.00", "0.00", "0.00");
        stubRefund(original,
                wallet(userWalletId, userId, "0.00", "0.00", "0.00", WalletStatus.ACTIVE),
                wallet(platformWalletId, platformUserId, "100.00", "100.00", "0.00", WalletStatus.ACTIVE));
        when(transactionRepository.existsByTypeAndReferenceId(TransactionType.REFUND, original.getId())).thenReturn(false);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        service.refundTransaction(UUID.randomUUID(), original.getId(), "r");

        InOrder order = inOrder(walletRepository);
        order.verify(walletRepository).findByIdForUpdate(userWalletId);
        order.verify(walletRepository).findByIdForUpdate(platformWalletId);
    }
```

- [ ] **Step 3: Run the tests to verify they fail**

Run (from `backend/`): `mvn -q -pl wallet-service -am test -Dtest=WalletAdminServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR, because `WalletAdminServiceImpl` doesn't implement the new methods.

- [ ] **Step 4: Implement**

In `…/service/impl/WalletAdminServiceImpl.java`, add these imports:

```java
import com.bidnow.common.annotation.Audit;
import com.bidnow.common.constant.ErrorCodes;
import com.bidnow.common.enums.AuditAction;
import com.bidnow.common.exception.BadRequestException;
import com.bidnow.common.util.AuditContextHolder;
import com.bidnow.wallet.domain.enums.TransactionStatus;
import com.bidnow.wallet.dto.response.ManualRefundResponse;
import com.bidnow.wallet.exception.ConflictException;
import com.bidnow.wallet.exception.InsufficientBalanceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.LinkedHashMap;
```

Then add these methods after `getWalletDetail`:

```java
    @Override
    @Transactional
    @Audit(action = AuditAction.ADMIN_ACTION, entityType = "Wallet", reason = "Admin froze wallet")
    public AdminWalletResponse freezeWallet(UUID userId) {
        if (UUID.fromString(platformUserId).equals(userId)) {
            throw new BadRequestException("The platform wallet cannot be frozen", ErrorCodes.INVALID_INPUT);
        }
        return changeStatus(userId, WalletStatus.SUSPENDED);
    }

    @Override
    @Transactional
    @Audit(action = AuditAction.ADMIN_ACTION, entityType = "Wallet", reason = "Admin unfroze wallet")
    public AdminWalletResponse unfreezeWallet(UUID userId) {
        return changeStatus(userId, WalletStatus.ACTIVE);
    }

    private AdminWalletResponse changeStatus(UUID userId, WalletStatus target) {
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> walletNotFound(userId));
        AuditContextHolder.setOldState(Wallet.builder().id(wallet.getId()).status(wallet.getStatus()).build());
        if (wallet.getStatus() != target) {
            wallet.setStatus(target);
            walletRepository.save(wallet);
            log.info("Wallet {} of user {} set to {}", wallet.getId(), userId, target);
        }
        AuditContextHolder.setNewState(Wallet.builder().id(wallet.getId()).status(wallet.getStatus()).build());
        return toWalletResponse(wallet);
    }

    @Override
    @Transactional
    @Audit(action = AuditAction.ADMIN_ACTION, entityType = "Transaction", reason = "Admin manual refund")
    public ManualRefundResponse refundTransaction(UUID adminId, UUID transactionId, String reason) {
        Transaction original = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new NotFoundException("Transaction not found: " + transactionId,
                        WalletErrorCodes.TRANSACTION_NOT_FOUND));
        UUID platformWalletId = walletRepository.findIdByUserId(UUID.fromString(platformUserId))
                .orElseThrow(() -> new NotFoundException("Platform wallet not found for userId: " + platformUserId,
                        WalletErrorCodes.WALLET_NOT_FOUND));
        if (!isRefundable(original, platformWalletId)) {
            throw new BadRequestException("Transaction " + transactionId + " is not refundable",
                    WalletErrorCodes.TRANSACTION_NOT_REFUNDABLE);
        }

        // Lock both wallets in ascending id order; the duplicate check runs under the locks.
        Wallet user;
        Wallet platform;
        if (original.getWalletId().compareTo(platformWalletId) < 0) {
            user = lockWallet(original.getWalletId());
            platform = lockWallet(platformWalletId);
        } else {
            platform = lockWallet(platformWalletId);
            user = lockWallet(original.getWalletId());
        }
        if (transactionRepository.existsByTypeAndReferenceId(TransactionType.REFUND, original.getId())) {
            throw new ConflictException("Transaction " + transactionId + " was already refunded",
                    WalletErrorCodes.TRANSACTION_ALREADY_REFUNDED);
        }
        BigDecimal amount = original.getAmount();
        if (platform.getAvailableBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(platform.getAvailableBalance(), amount);
        }

        BigDecimal platformBefore = platform.getAvailableBalance();
        platform.setAvailableBalance(platformBefore.subtract(amount));
        platform.setTotalBalance(platform.getTotalBalance().subtract(amount));
        walletRepository.save(platform);
        transactionRepository.save(refundRow(platform, platformBefore, amount, original, adminId, reason, "DEBIT"));

        BigDecimal userBefore = user.getAvailableBalance();
        user.setAvailableBalance(userBefore.add(amount));
        user.setTotalBalance(user.getTotalBalance().add(amount));
        walletRepository.save(user);
        Transaction credit = transactionRepository.save(
                refundRow(user, userBefore, amount, original, adminId, reason, "CREDIT"));

        AuditContextHolder.setNewState(credit);
        log.info("Admin {} refunded transaction {} ({}) to user {}", adminId, transactionId, amount, user.getUserId());
        return ManualRefundResponse.builder()
                .refundTransactionId(credit.getId())
                .amount(amount)
                .userId(user.getUserId())
                .availableBalance(user.getAvailableBalance())
                .build();
    }

    private boolean isRefundable(Transaction tx, UUID platformWalletId) {
        return (tx.getType() == TransactionType.PAYMENT || tx.getType() == TransactionType.FORFEIT)
                && !tx.getWalletId().equals(platformWalletId)
                && tx.getAvailableBalanceAfter().compareTo(tx.getAvailableBalanceBefore()) <= 0;
    }

    private Transaction refundRow(Wallet wallet, BigDecimal before, BigDecimal amount, Transaction original,
                                  UUID adminId, String reason, String direction) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("adminId", String.valueOf(adminId));
        meta.put("reason", reason);
        meta.put("originalTransactionId", original.getId().toString());
        meta.put("direction", direction);
        String metadata;
        try {
            metadata = objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize refund metadata", e);
        }
        return Transaction.builder()
                .walletId(wallet.getId())
                .type(TransactionType.REFUND)
                .amount(amount)
                .availableBalanceBefore(before)
                .availableBalanceAfter(wallet.getAvailableBalance())
                .referenceId(original.getId())
                .status(TransactionStatus.COMPLETED)
                .description("Manual refund of transaction " + original.getId())
                .metadata(metadata)
                .build();
    }

    private Wallet lockWallet(UUID walletId) {
        return walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new NotFoundException("Wallet not found: " + walletId,
                        WalletErrorCodes.WALLET_NOT_FOUND));
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn -q -pl wallet-service -am test -Dtest=WalletAdminServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (17 tests: 5 from Task 1 and 12 new).

---

### Task 3: `WalletAdminController` and the gateway route

**Files:** Create `…/controller/WalletAdminController.java` and `T…/controller/WalletAdminControllerTest.java`. Modify `backend/api-gateway/src/main/resources/application.yml`.

**Interfaces:**
- Consumes:
  - From Tasks 1–2: all six `WalletAdminService` methods, `ManualRefundRequest` and the response DTOs.
  - Existing: `@AuthenticatedUserId`, `UserIdArgumentResolver` (which reads `X-User-Id`), `WalletExceptionHandler`, `GlobalExceptionHandler`, `BaseResponse` and `PageResponse`.
- Produces: the six endpoints under `/api/v1/admin/wallets`.

- [ ] **Step 1: Write the failing tests**

`T…/controller/WalletAdminControllerTest.java`:

```java
package com.bidnow.wallet.controller;

import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.dto.PaginationMeta;
import com.bidnow.common.exception.GlobalExceptionHandler;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.common.resolver.UserIdArgumentResolver;
import com.bidnow.wallet.domain.enums.TransactionType;
import com.bidnow.wallet.dto.response.AdminWalletDetailResponse;
import com.bidnow.wallet.dto.response.AdminWalletResponse;
import com.bidnow.wallet.dto.response.ManualRefundResponse;
import com.bidnow.wallet.dto.response.WalletStatsResponse;
import com.bidnow.wallet.exception.ConflictException;
import com.bidnow.wallet.exception.WalletExceptionHandler;
import com.bidnow.wallet.service.WalletAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WalletAdminControllerTest {

    private static final String BASE = "/api/v1/admin/wallets";

    @Mock
    private WalletAdminService walletAdminService;

    private MockMvc mockMvc;

    private final UUID adminId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WalletAdminController(walletAdminService))
                .setControllerAdvice(new WalletExceptionHandler(), new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new UserIdArgumentResolver())
                .build();
    }

    private AdminWalletResponse walletResponse(String status) {
        return AdminWalletResponse.builder()
                .walletId(UUID.randomUUID()).userId(userId)
                .totalBalance(new BigDecimal("100.00")).availableBalance(new BigDecimal("100.00"))
                .lockedBalance(BigDecimal.ZERO).currency("USD").status(status)
                .build();
    }

    @Test
    void getStats_returns200() throws Exception {
        when(walletAdminService.getStats()).thenReturn(WalletStatsResponse.builder()
                .platformWalletBalance(new BigDecimal("950.00")).totalActiveWallets(12)
                .totalLockedBalance(new BigDecimal("300.00")).totalActiveDepositLocks(4)
                .build());

        mockMvc.perform(get(BASE + "/stats").header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.platformWalletBalance").value(950.00))
                .andExpect(jsonPath("$.data.totalActiveWallets").value(12))
                .andExpect(jsonPath("$.data.totalActiveDepositLocks").value(4));
    }

    @Test
    void listTransactions_defaultsToPageZeroSizeFifty() throws Exception {
        when(walletAdminService.listTransactions(null, 0, 50)).thenReturn(PageResponse.<com.bidnow.wallet.dto.response.AdminTransactionResponse>builder()
                .data(List.of()).pagination(PaginationMeta.builder().page(0).limit(50).build()).build());

        mockMvc.perform(get(BASE + "/transactions"))
                .andExpect(status().isOk());
        verify(walletAdminService).listTransactions(null, 0, 50);
    }

    @Test
    void listTransactions_passesTypeAndPaging() throws Exception {
        when(walletAdminService.listTransactions(TransactionType.FORFEIT, 2, 10)).thenReturn(PageResponse.<com.bidnow.wallet.dto.response.AdminTransactionResponse>builder()
                .data(List.of()).pagination(PaginationMeta.builder().page(2).limit(10).build()).build());

        mockMvc.perform(get(BASE + "/transactions").param("type", "FORFEIT").param("page", "2").param("size", "10"))
                .andExpect(status().isOk());
        verify(walletAdminService).listTransactions(TransactionType.FORFEIT, 2, 10);
    }

    @Test
    void getWalletDetail_returns200() throws Exception {
        when(walletAdminService.getWalletDetail(userId)).thenReturn(AdminWalletDetailResponse.builder()
                .wallet(walletResponse("ACTIVE")).recentTransactions(List.of()).activeDepositLocks(List.of())
                .build());

        mockMvc.perform(get(BASE + "/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.wallet.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.wallet.status").value("ACTIVE"));
    }

    @Test
    void getWalletDetail_missing_returns404() throws Exception {
        when(walletAdminService.getWalletDetail(userId))
                .thenThrow(new NotFoundException("none", "WALLET_NOT_FOUND"));

        mockMvc.perform(get(BASE + "/{userId}", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WALLET_NOT_FOUND"));
    }

    @Test
    void freeze_returns200WithSuspended() throws Exception {
        when(walletAdminService.freezeWallet(userId)).thenReturn(walletResponse("SUSPENDED"));

        mockMvc.perform(post(BASE + "/{userId}/freeze", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));
    }

    @Test
    void unfreeze_returns200WithActive() throws Exception {
        when(walletAdminService.unfreezeWallet(userId)).thenReturn(walletResponse("ACTIVE"));

        mockMvc.perform(post(BASE + "/{userId}/unfreeze", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void refund_passesAdminIdAndReason() throws Exception {
        UUID txId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        when(walletAdminService.refundTransaction(adminId, txId, "Customer dispute")).thenReturn(ManualRefundResponse.builder()
                .refundTransactionId(refundId).amount(new BigDecimal("50.00")).userId(userId)
                .availableBalance(new BigDecimal("150.00")).build());

        mockMvc.perform(post(BASE + "/transactions/{id}/refund", txId).header("X-User-Id", adminId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Customer dispute\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundTransactionId").value(refundId.toString()))
                .andExpect(jsonPath("$.data.amount").value(50.00));
        verify(walletAdminService).refundTransaction(adminId, txId, "Customer dispute");
    }

    @Test
    void refund_blankReason_returns400AndSkipsService() throws Exception {
        mockMvc.perform(post(BASE + "/transactions/{id}/refund", UUID.randomUUID()).header("X-User-Id", adminId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
        verify(walletAdminService, never()).refundTransaction(any(), any(), any());
    }

    @Test
    void refund_alreadyRefunded_returns409() throws Exception {
        UUID txId = UUID.randomUUID();
        when(walletAdminService.refundTransaction(adminId, txId, "again"))
                .thenThrow(new ConflictException("done", "TRANSACTION_ALREADY_REFUNDED"));

        mockMvc.perform(post(BASE + "/transactions/{id}/refund", txId).header("X-User-Id", adminId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"again\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TRANSACTION_ALREADY_REFUNDED"));
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run (from `backend/`): `mvn -q -pl wallet-service -am test -Dtest=WalletAdminControllerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR, because `WalletAdminController` doesn't exist yet.

- [ ] **Step 3: Implement the controller**

`…/controller/WalletAdminController.java`:

```java
package com.bidnow.wallet.controller;

import com.bidnow.common.annotation.AuthenticatedUserId;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.dto.PageResponse;
import com.bidnow.wallet.domain.enums.TransactionType;
import com.bidnow.wallet.dto.request.ManualRefundRequest;
import com.bidnow.wallet.dto.response.AdminTransactionResponse;
import com.bidnow.wallet.dto.response.AdminWalletDetailResponse;
import com.bidnow.wallet.dto.response.AdminWalletResponse;
import com.bidnow.wallet.dto.response.ManualRefundResponse;
import com.bidnow.wallet.dto.response.WalletStatsResponse;
import com.bidnow.wallet.service.WalletAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/wallets")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Wallet Admin", description = "Admin visibility and controls for wallets and transactions")
public class WalletAdminController {

    private final WalletAdminService walletAdminService;

    @Operation(summary = "Platform wallet stats")
    @GetMapping("/stats")
    public ResponseEntity<BaseResponse<WalletStatsResponse>> getStats() {
        return ResponseEntity.ok(BaseResponse.success(walletAdminService.getStats()));
    }

    @Operation(summary = "All transactions", description = "Paginated across all wallets, newest first; optional type filter.")
    @GetMapping("/transactions")
    public ResponseEntity<BaseResponse<PageResponse<AdminTransactionResponse>>> listTransactions(
            @RequestParam(required = false) TransactionType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(BaseResponse.success(walletAdminService.listTransactions(type, page, size)));
    }

    @Operation(summary = "User wallet detail", description = "Wallet, last 20 transactions and active deposit locks.")
    @GetMapping("/{userId}")
    public ResponseEntity<BaseResponse<AdminWalletDetailResponse>> getWalletDetail(@PathVariable UUID userId) {
        return ResponseEntity.ok(BaseResponse.success(walletAdminService.getWalletDetail(userId)));
    }

    @Operation(summary = "Freeze wallet", description = "Sets status SUSPENDED: blocks deposits and new deposit locks.")
    @PostMapping("/{userId}/freeze")
    public ResponseEntity<BaseResponse<AdminWalletResponse>> freeze(@PathVariable UUID userId) {
        return ResponseEntity.ok(BaseResponse.success(walletAdminService.freezeWallet(userId)));
    }

    @Operation(summary = "Unfreeze wallet", description = "Sets status ACTIVE.")
    @PostMapping("/{userId}/unfreeze")
    public ResponseEntity<BaseResponse<AdminWalletResponse>> unfreeze(@PathVariable UUID userId) {
        return ResponseEntity.ok(BaseResponse.success(walletAdminService.unfreezeWallet(userId)));
    }

    @Operation(summary = "Manual refund", description = "Refunds a user-side PAYMENT/FORFEIT debit once, funded by the platform wallet.")
    @PostMapping("/transactions/{transactionId}/refund")
    public ResponseEntity<BaseResponse<ManualRefundResponse>> refund(@AuthenticatedUserId UUID adminId,
                                                                     @PathVariable UUID transactionId,
                                                                     @Valid @RequestBody ManualRefundRequest request) {
        return ResponseEntity.ok(BaseResponse.success(
                walletAdminService.refundTransaction(adminId, transactionId, request.getReason())));
    }
}
```

- [ ] **Step 4: Gateway route**

In `backend/api-gateway/src/main/resources/application.yml`, change the wallet-service route line:

```yaml
            - Path=/api/v1/wallets/**
```

to:

```yaml
            - Path=/api/v1/wallets/**, /api/v1/admin/wallets/**
```

Keep the indentation exactly as it is.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn -q -pl wallet-service -am test -Dtest=WalletAdminControllerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (10 tests).

---

### Task 4: `WalletAuditEventListener` — forward audit events to Kafka

**Files:** Create `…/kafka/WalletAuditEventListener.java` and `T…/kafka/WalletAuditEventListenerTest.java`.

**Interfaces:**
- Consumes (common): `AuditApplicationEvent(Object, AuditLogEvent)` with `getAuditLogEvent()`, and `AuditLogEvent` (Lombok builder: `correlationId` UUID, `entityId` String, `action`, and more). Also Spring Boot's `KafkaTemplate<String, Object>`.
- Produces: `WalletAuditEventListener.onAudit(AuditApplicationEvent)`, which sends to `audit-events`.

- [ ] **Step 1: Write the failing test**

`T…/kafka/WalletAuditEventListenerTest.java`:

```java
package com.bidnow.wallet.kafka;

import com.bidnow.common.dto.event.AuditApplicationEvent;
import com.bidnow.common.dto.event.AuditLogEvent;
import com.bidnow.common.enums.AuditAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletAuditEventListenerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private WalletAuditEventListener listener;

    @Test
    void onAudit_sendsToAuditEventsKeyedByCorrelationId() {
        UUID correlationId = UUID.randomUUID();
        AuditLogEvent audit = AuditLogEvent.builder()
                .correlationId(correlationId).entityType("Wallet").entityId("w-1").action(AuditAction.ADMIN_ACTION)
                .build();
        CompletableFuture<SendResult<String, Object>> sent = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(sent);

        listener.onAudit(new AuditApplicationEvent(this, audit));

        verify(kafkaTemplate).send(eq("audit-events"), eq(correlationId.toString()), eq(audit));
    }

    @Test
    void onAudit_withoutCorrelationId_keysByEntityId() {
        AuditLogEvent audit = AuditLogEvent.builder()
                .entityType("Wallet").entityId("w-2").action(AuditAction.ADMIN_ACTION)
                .build();
        CompletableFuture<SendResult<String, Object>> sent = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(sent);

        listener.onAudit(new AuditApplicationEvent(this, audit));

        verify(kafkaTemplate).send(eq("audit-events"), eq("w-2"), eq(audit));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run (from `backend/`): `mvn -q -pl wallet-service -am test -Dtest=WalletAuditEventListenerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR, because `WalletAuditEventListener` doesn't exist yet.

- [ ] **Step 3: Implement**

`…/kafka/WalletAuditEventListener.java`:

```java
package com.bidnow.wallet.kafka;

import com.bidnow.common.dto.event.AuditApplicationEvent;
import com.bidnow.common.dto.event.AuditLogEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Forwards @Audit events to the shared "audit-events" topic (stored by media-service, visible at
 * /api/v1/admin/audit-logs). Runs after commit; fallbackExecution covers an event published outside a transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletAuditEventListener {

    private static final String AUDIT_EVENTS_TOPIC = "audit-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAudit(AuditApplicationEvent event) {
        AuditLogEvent audit = event.getAuditLogEvent();
        String key = audit.getCorrelationId() != null ? audit.getCorrelationId().toString() : audit.getEntityId();
        kafkaTemplate.send(AUDIT_EVENTS_TOPIC, key, audit)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish audit event {} for {} {}",
                                audit.getAction(), audit.getEntityType(), audit.getEntityId(), ex);
                    } else {
                        log.info("Published audit event {} for {} {}",
                                audit.getAction(), audit.getEntityType(), audit.getEntityId());
                    }
                });
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -pl wallet-service -am test -Dtest=WalletAuditEventListenerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (2 tests).

---

### Task 5: Documentation

**Files:** `docs/architecture.md`, `docs/epics/wallet/epic.md`

**Interfaces:** none. This task is docs only.

- [ ] **Step 1: Architecture**

In `docs/architecture.md`, append this at the end of the `### Wallet Events (Kafka)` section, after its last paragraph and before the next `---` or heading:

```markdown

**Wallet admin API (ADMIN role, via gateway `/api/v1/admin/wallets/**`):**

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/admin/wallets/stats` | Platform wallet balance, active wallets, total locked, active deposit locks |
| GET | `/api/v1/admin/wallets/transactions?type&page=0&size=50` | All transactions, newest first |
| GET | `/api/v1/admin/wallets/{userId}` | Wallet, last 20 transactions, active deposit locks |
| POST | `/api/v1/admin/wallets/{userId}/freeze` · `/unfreeze` | Status SUSPENDED / ACTIVE |
| POST | `/api/v1/admin/wallets/transactions/{id}/refund` `{ reason }` | Refund a user-side PAYMENT/FORFEIT once, funded by the platform wallet |

Admin actions are audited (`@Audit` → `audit-events` topic).
```

- [ ] **Step 2: Wallet epic**

In `docs/epics/wallet/epic.md`, find the `### Admin Endpoints` heading in the API Endpoints section. Replace everything from that heading up to, but not including, the next `##` or `###` heading with:

```markdown
### Admin Endpoints (WALLET-307 — ADMIN role, `/api/v1/admin/wallets`)

- `GET /stats` — `{ platformWalletBalance, totalActiveWallets, totalLockedBalance, totalActiveDepositLocks }`
- `GET /transactions?type&page=0&size=50` — all transactions across wallets, newest first
- `GET /{userId}` — wallet, last 20 transactions, active deposit locks
- `POST /{userId}/freeze` / `POST /{userId}/unfreeze` — status `SUSPENDED` / `ACTIVE` (freeze blocks mock deposits and new deposit locks; incoming refunds/proceeds still allowed; platform wallet cannot be frozen)
- `POST /transactions/{transactionId}/refund { reason }` — refunds a user-side `PAYMENT`/`FORFEIT` debit in full, once (409 on repeat); funded by the platform wallet (400 if it can't cover); REFUND rows on both wallets with `{adminId, reason, originalTransactionId, direction}` metadata
- All actions audited via `@Audit` → `audit-events`

```

If there is no `### Admin Endpoints` heading, insert the block directly before `## Open Questions to Resolve Before Detailed Design`, and note that in your report.

---

### Task 6: Full verification

**Files:** none modified.

- [ ] **Step 1: Run the full wallet-service suite**

Run (from `backend/`): `mvn -pl wallet-service -am clean test`
Expected: BUILD SUCCESS with 0 failures. The new tests are `WalletAdminServiceImplTest` (17), `WalletAdminControllerTest` (10) and `WalletAuditEventListenerTest` (2).

- [ ] **Step 2: Build the gateway**

Run (from `backend/`): `mvn -q -pl api-gateway -am compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Manual smoke test (only if the stack is running)**

Through the gateway:
1. With a non-admin JWT, call `GET /api/v1/admin/wallets/stats`. Expected: 403.
2. With an admin JWT:
   - `GET /stats` → 200
   - `GET /transactions?type=FORFEIT` → only FORFEIT rows
   - `POST /{userId}/freeze` → a mock deposit for that user now returns 400
   - `POST /{userId}/unfreeze`
   - `POST /transactions/{id}/refund {"reason":"test"}` on a FORFEIT → 200; repeat → 409
3. `GET /api/v1/admin/audit-logs` shows the ADMIN_ACTION entries.

If no stack is available, skip this step and report it as not run.
