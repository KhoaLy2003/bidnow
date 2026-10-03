# NOTIF-106: Payment Reminder #2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A winner who has not paid 24 hours before their payment deadline receives reminder #2 (in-app + the seeded `PAYMENT_REMINDER_2` email), exactly once.

**Architecture:**
- **wallet-service** owns the payment hold, so it decides when a reminder is due.
  - A `@Scheduled` `PaymentReminderScheduler` mirrors the existing `ForfeitScheduler`. Every 5 minutes it finds unpaid holds whose deadline is within the next 24 h and has not passed, and that have not been reminded.
  - For each one it calls `PaymentService.sendPaymentReminder`. That method locks the hold (`SKIP LOCKED`), re-checks it, stamps the new `reminder_sent_at` column, and publishes `PaymentEvent{paymentType="REMINDER_24H"}` after commit through the existing `PaymentApplicationEvent` → `WalletEventPublisher` path.
- **media-service** maps `REMINDER_24H` to a transactional `PAYMENT_REMINDER` notification with the already-seeded email template.

**Tech Stack:** Java 17, Spring Boot 3.2.4, Spring Data JPA, PostgreSQL + Liquibase, Spring `@Scheduled`, Kafka, JUnit 5 + Mockito + AssertJ, Testcontainers PostgreSQL (`bdd-support` `PostgresContainerSupport`).

**Spec:** `docs/superpowers/plans/2026-09-30-notification-epic-roadmap.md`, Story 6, plus Decisions 3 and 5 (payment emails are transactional). Sources: `docs/epics/notification/notification-service-mvp.md` §3 "Payment Reminder - 2 Attempts" ("T0 + 24h: If not paid → Send email #2 'Reminder: 24 hours left to complete payment'"), and `docs/epics/notification/issue-13.md` §3.

## Global Constraints

- wallet-service keeps its own DB. There are no cross-DB queries. Cross-service data travels only as Kafka events.
- Kafka publishes happen after commit. wallet-service does this through `eventPublisher.publishEvent(new PaymentApplicationEvent(this, …))`, which `WalletEventPublisher.onPaymentEvent` (`@TransactionalEventListener(AFTER_COMMIT)`) sends to `payment-event-topic`. Never call `KafkaTemplate` from the service.
- Every tunable is config, never hard-coded: `wallet.payment.reminder-before-deadline-hours: 24`, `reminder-interval-ms: 300000`, `reminder-batch-size: 100`, `reminder-initial-delay-ms: 60000`.
- Multi-instance safety: the scheduler claims work with the hold's row lock (`findByAuctionIdForUpdateSkipLocked`). There is no ShedLock.
- Exactly once: `payment_holds.reminder_sent_at` is set in the same transaction that publishes the event. media-service deduplicates on `DedupKeys.payment("REMINDER_24H", auctionId)` (existing format: `PAYMENT_REMINDER_24H:{auctionId}`).
- Payment emails are transactional (Decision 5). `EmailSpec.transactional = true`, so the user's email opt-out is ignored.
- `PaymentEvent` fields are only added, never renamed or removed. This story adds none: `paymentType="REMINDER_24H"` is a new value of an existing field (already listed in the field's comment).
- Money is formatted `$#,##0.00` and deadlines `yyyy-MM-dd HH:mm 'UTC'`, using the existing `NotificationFormats`.
- No destructive migration. Only `ALTER TABLE … ADD COLUMN` (nullable).
- **Agents do not commit.** Skip every commit step. The user commits.
- Maven runs from `backend/`. The default unit suites must not need Docker; `*PostgresIT` runs only when named explicitly.
- Conventional commit for the story (the user runs it): `feat(notification): send payment reminder #2 (NOTIF-106)`.

## Rulings (decisions this plan makes beyond the roadmap)

1. **Due-ness is measured from the deadline, not `created_at`.** The roadmap writes the due condition as `created_at <= now - 24h`, with config `reminder-after-hours`. The plan uses `deadline <= now + 24h AND deadline > now`, with config `wallet.payment.reminder-before-deadline-hours: 24`.
   - The two are identical with today's 48 h deadline (`deadline = created + 48h`).
   - The deadline version keeps the email's "24 hours left" true if `deadline-hours` ever changes.
   - It uses the existing `(status, deadline)` index.
   - Its `deadline > now` half never reminds about an already-expired hold; that is the forfeit job's work.
2. **The service re-checks everything under the row lock:** `PENDING_PAYMENT`, not yet reminded, deadline still in the future, and deadline within the reminder window. Same pattern as `forfeitExpiredHold`. This handles a winner who pays between the scheduler's query and the lock, and a second instance racing (`SKIP LOCKED` → no-op).
3. **The email's `{bidAmount}` is the amount still owed.** The `PAYMENT_REMINDER_2` copy reads "Your payment of {bidAmount} … is almost overdue", so it gets `remaining`. When `remaining` is zero (the deposit covered everything and the winner only has to confirm), it gets the total.
4. **One shared "what to do" sentence.** `paymentRequired` and the new `paymentReminder` share a single `payInstruction(event)` helper: confirm / top up / pay the remainder, plus "by {deadline}". The REQUIRED copy is unchanged character for character.
5. **The wallet repository query gets a real-Postgres IT.** It uses `@DataJpaTest` with a test-local `@SpringBootConfiguration`, so wallet's Kafka and security `@Configuration` classes are not loaded. This needs `bdd-support` as a test dependency, as media-service and bidding-service already have.

## File Structure

| File | Responsibility |
|---|---|
| `backend/wallet-service/src/main/resources/db/changelog/migrations/07-payment-hold-reminder.sql` (create) + `db.changelog-master.xml` (modify) | `payment_holds.reminder_sent_at` |
| `backend/wallet-service/src/main/java/com/bidnow/wallet/domain/entity/PaymentHold.java` (modify) | `reminderSentAt` field |
| `backend/wallet-service/src/main/java/com/bidnow/wallet/repository/PaymentHoldRepository.java` (modify) | `findDueForReminderAuctionIds` |
| `backend/wallet-service/pom.xml` (modify) | `bdd-support` test dependency |
| `backend/wallet-service/src/main/java/com/bidnow/wallet/service/PaymentService.java` + `service/impl/PaymentServiceImpl.java` (modify) | `sendPaymentReminder(UUID)` |
| `backend/wallet-service/src/main/java/com/bidnow/wallet/scheduler/PaymentReminderScheduler.java` (create) | Every 5 min: due holds → `sendPaymentReminder` |
| `backend/wallet-service/src/main/resources/application.yml` (modify) | `wallet.payment.reminder-*` |
| `backend/media-service/src/main/java/com/bidnow/media/notification/handler/PaymentNotificationHandler.java` (modify) | `REMINDER_24H` → `PAYMENT_REMINDER` |
| `docs/architecture.md`, roadmap (modify) | Event contract and progress |

Paths below use `WM = backend/wallet-service/src/main/java/com/bidnow/wallet`, `WT = backend/wallet-service/src/test/java/com/bidnow/wallet`, `MM = backend/media-service/src/main/java/com/bidnow/media` and `MT = backend/media-service/src/test/java/com/bidnow/media`.

---

### Task 1: Reminder column and due-hold query (wallet-service)

**Files:**
- Create: `backend/wallet-service/src/main/resources/db/changelog/migrations/07-payment-hold-reminder.sql`
- Modify: `backend/wallet-service/src/main/resources/db/changelog/db.changelog-master.xml`, `WM/domain/entity/PaymentHold.java`, `WM/repository/PaymentHoldRepository.java`, `backend/wallet-service/pom.xml`
- Test: `WT/repository/PaymentHoldReminderQueryPostgresIT.java` (create; needs Docker)

**Interfaces:**
- Consumes: the `PaymentHold`, `Wallet`, `PaymentHoldStatus` and `WalletStatus` entities and enums (existing); `com.bidnow.bdd.container.PostgresContainerSupport` (bdd-support).
- Produces:
  - `PaymentHold.reminderSentAt: LocalDateTime` (Lombok getter, setter and builder)
  - `List<UUID> PaymentHoldRepository.findDueForReminderAuctionIds(PaymentHoldStatus status, LocalDateTime now, LocalDateTime remindBefore, Pageable page)`: the auction IDs of holds in `status` that are not reminded and have `now < deadline <= remindBefore`, earliest deadline first

- [ ] **Step 1: Add the test dependency**

In `backend/wallet-service/pom.xml`, inside `<dependencies>` after the `common` dependency, add (this is the same block media-service uses):

```xml
        <dependency>
            <groupId>com.bidnow</groupId>
            <artifactId>bdd-support</artifactId>
            <version>${project.version}</version>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: Write the failing IT**

Create `WT/repository/PaymentHoldReminderQueryPostgresIT.java`:

```java
package com.bidnow.wallet.repository;

import com.bidnow.bdd.container.PostgresContainerSupport;
import com.bidnow.wallet.domain.entity.PaymentHold;
import com.bidnow.wallet.domain.entity.Wallet;
import com.bidnow.wallet.domain.enums.PaymentHoldStatus;
import com.bidnow.wallet.domain.enums.WalletStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reminder query against real Postgres + the Liquibase schema. Needs Docker; run explicitly:
 * mvn -q -pl wallet-service -am test -Dtest=PaymentHoldReminderQueryPostgresIT -Dsurefire.failIfNoSpecifiedTests=false
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PaymentHoldReminderQueryPostgresIT {

    /** JPA-only context: keeps WalletApplication's component scan (Kafka, security config) out of the slice. */
    @SpringBootConfiguration
    @EntityScan(basePackageClasses = PaymentHold.class)
    @EnableJpaRepositories(basePackageClasses = PaymentHoldRepository.class)
    static class JpaOnly {
    }

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        PostgresContainerSupport.properties().forEach((key, value) -> registry.add(key, () -> value));
    }

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 12, 0);
    private static final LocalDateTime REMIND_BEFORE = NOW.plusHours(24);

    @Autowired
    private TestEntityManager em;
    @Autowired
    private PaymentHoldRepository repository;

    private UUID walletId;

    @BeforeEach
    void winnerWallet() {
        walletId = em.persistAndFlush(Wallet.builder().userId(UUID.randomUUID())
                .totalBalance(BigDecimal.ZERO).availableBalance(BigDecimal.ZERO).lockedBalance(BigDecimal.ZERO)
                .currency("USD").status(WalletStatus.ACTIVE).build()).getId();
    }

    private UUID hold(LocalDateTime deadline, PaymentHoldStatus status, LocalDateTime reminderSentAt) {
        UUID auctionId = UUID.randomUUID();
        em.persistAndFlush(PaymentHold.builder()
                .auctionId(auctionId).winnerWalletId(walletId).winnerUserId(UUID.randomUUID())
                .sellerUserId(UUID.randomUUID()).totalAmount(new BigDecimal("100.00"))
                .depositApplied(new BigDecimal("10.00")).remainingAmount(new BigDecimal("90.00"))
                .fundsHeld(true).status(status).deadline(deadline).reminderSentAt(reminderSentAt)
                .build());
        return auctionId;
    }

    private List<UUID> due(int limit) {
        return repository.findDueForReminderAuctionIds(PaymentHoldStatus.PENDING_PAYMENT, NOW, REMIND_BEFORE,
                PageRequest.of(0, limit));
    }

    @Test
    void due_isDeadlineWithinTheNext24Hours_earliestFirst() {
        UUID atBoundary = hold(REMIND_BEFORE, PaymentHoldStatus.PENDING_PAYMENT, null);
        UUID soon = hold(NOW.plusHours(1), PaymentHoldStatus.PENDING_PAYMENT, null);
        hold(REMIND_BEFORE.plusMinutes(1), PaymentHoldStatus.PENDING_PAYMENT, null); // 24h01m left: not yet
        hold(NOW.minusMinutes(1), PaymentHoldStatus.PENDING_PAYMENT, null);          // expired: forfeit's job

        assertThat(due(100)).containsExactly(soon, atBoundary);
    }

    @Test
    void remindedOrClosedHolds_areNotDue() {
        hold(NOW.plusHours(1), PaymentHoldStatus.PENDING_PAYMENT, NOW.minusHours(1));
        hold(NOW.plusHours(1), PaymentHoldStatus.COMPLETED, null);
        hold(NOW.plusHours(1), PaymentHoldStatus.FORFEITED, null);
        hold(NOW.plusHours(1), PaymentHoldStatus.CANCELLED, null);

        assertThat(due(100)).isEmpty();
    }

    @Test
    void pageSize_limitsTheBatch() {
        hold(NOW.plusHours(1), PaymentHoldStatus.PENDING_PAYMENT, null);
        hold(NOW.plusHours(2), PaymentHoldStatus.PENDING_PAYMENT, null);
        hold(NOW.plusHours(3), PaymentHoldStatus.PENDING_PAYMENT, null);

        assertThat(due(2)).hasSize(2);
    }
}
```

- [ ] **Step 3: Run the IT to verify it fails**

Run (from `backend/`, Docker running): `mvn -q -pl wallet-service -am test -Dtest=PaymentHoldReminderQueryPostgresIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure. `reminderSentAt` is not a builder method, and `findDueForReminderAuctionIds` is not defined.

- [ ] **Step 4: Implement the migration, entity field and query**

Create `backend/wallet-service/src/main/resources/db/changelog/migrations/07-payment-hold-reminder.sql`:

```sql
-- liquibase formatted sql

-- changeset bidnow:wallet_007
-- comment: Payment reminder #2 is sent once per hold (NOTIF-106)
ALTER TABLE payment_holds ADD COLUMN reminder_sent_at TIMESTAMP;
```

In `db.changelog-master.xml`, add after the `06-index-refund-reference.sql` include:

```xml
    <include file="db/changelog/migrations/07-payment-hold-reminder.sql"/>
```

In `WM/domain/entity/PaymentHold.java`, add after `completedAt`:

```java

    @Column(name = "reminder_sent_at")
    private LocalDateTime reminderSentAt;
```

In `WM/repository/PaymentHoldRepository.java`, add after `findExpiredAuctionIds`:

```java

    /**
     * Auction ids of holds in {@code status}, not yet reminded, whose deadline is after {@code now} and at or before
     * {@code remindBefore} — i.e. inside the reminder window and not yet expired. Earliest deadline first.
     */
    @Query("SELECT h.auctionId FROM PaymentHold h WHERE h.status = :status AND h.reminderSentAt IS NULL"
            + " AND h.deadline > :now AND h.deadline <= :remindBefore ORDER BY h.deadline ASC")
    List<UUID> findDueForReminderAuctionIds(@Param("status") PaymentHoldStatus status,
                                            @Param("now") LocalDateTime now,
                                            @Param("remindBefore") LocalDateTime remindBefore, Pageable page);
```

- [ ] **Step 5: Run the IT to verify it passes, then the unit suite**

Run: `mvn -q -pl wallet-service -am test -Dtest=PaymentHoldReminderQueryPostgresIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (3 tests).

If the `@DataJpaTest` context fails to start because something outside JPA is pulled in, adjust only the test's context setup and record the change in the report. For example, add `@ImportAutoConfiguration(LiquibaseAutoConfiguration.class)` if Liquibase did not run, or exclude an auto-configuration. Keep the three test cases unchanged.

Run: `mvn -q -pl wallet-service -am test`
Expected: BUILD SUCCESS. The IT is not in the default suite because surefire only picks up `*Test`.

- [ ] **Step 6: Commit**: skipped (the user commits).

---

### Task 2: `PaymentService.sendPaymentReminder` (wallet-service)

**Files:**
- Modify: `WM/service/PaymentService.java`, `WM/service/impl/PaymentServiceImpl.java`
- Test: `WT/service/impl/PaymentServiceImplTest.java` (add a section)

**Interfaces:**
- Consumes: `PaymentHold.reminderSentAt` (Task 1); `PaymentHoldRepository.findByAuctionIdForUpdateSkipLocked(UUID)` (existing); `PaymentApplicationEvent(Object, PaymentEvent)` (existing).
- Produces: `void PaymentService.sendPaymentReminder(UUID auctionId)`, `@Transactional`. Under the row lock it either stamps `reminderSentAt` and publishes `PaymentEvent{paymentType="REMINDER_24H"}`, or does nothing.

- [ ] **Step 1: Write the failing tests**

Append this section to `WT/service/impl/PaymentServiceImplTest.java`, before the final closing brace. It reuses the class's existing fields (`auctionId`, `winnerUserId`, `sellerUserId`, `winnerWalletId`) and helpers (`hold(...)`, `capturedEvent()`).

```java
    // ── sendPaymentReminder ───────────────────────────────────────────────────

    private PaymentHold stubReminderHold(LocalDateTime deadline, PaymentHoldStatus status, boolean fundsHeld) {
        PaymentHold h = hold("500.00", "50.00", "450.00", fundsHeld, null, deadline, status);
        when(paymentHoldRepository.findByAuctionIdForUpdateSkipLocked(auctionId)).thenReturn(Optional.of(h));
        return h;
    }

    private void assertNoReminder(PaymentHold h) {
        assertThat(h.getReminderSentAt()).isNull();
        verify(paymentHoldRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void sendPaymentReminder_dueHold_marksRemindedAndPublishesReminderAfterCommit() {
        LocalDateTime deadline = LocalDateTime.now().plusHours(23);
        PaymentHold h = stubReminderHold(deadline, PaymentHoldStatus.PENDING_PAYMENT, true);
        LocalDateTime before = LocalDateTime.now();

        paymentService.sendPaymentReminder(auctionId);

        assertThat(h.getReminderSentAt()).isBetween(before, LocalDateTime.now());
        verify(paymentHoldRepository).save(h);
        // Published as an application event: WalletEventPublisher sends it to Kafka only after commit
        PaymentEvent event = capturedEvent();
        assertThat(event.getPaymentType()).isEqualTo("REMINDER_24H");
        assertThat(event.getAuctionId()).isEqualTo(auctionId);
        assertThat(event.getUserId()).isEqualTo(winnerUserId);
        assertThat(event.getSellerId()).isEqualTo(sellerUserId);
        assertThat(event.getAmount()).isEqualByComparingTo("500.00");
        assertThat(event.getDepositAmount()).isEqualByComparingTo("50.00");
        assertThat(event.getRemaining()).isEqualByComparingTo("450.00");
        assertThat(event.getDeadline()).isEqualTo(deadline.atZone(java.time.ZoneId.systemDefault()).toInstant());
        assertThat(event.getInsufficientFunds()).isFalse();
    }

    @Test
    void sendPaymentReminder_fundsNotHeld_flagsInsufficientFunds() {
        stubReminderHold(LocalDateTime.now().plusHours(23), PaymentHoldStatus.PENDING_PAYMENT, false);

        paymentService.sendPaymentReminder(auctionId);

        assertThat(capturedEvent().getInsufficientFunds()).isTrue();
    }

    @Test
    void sendPaymentReminder_alreadyReminded_isNoOp() {
        PaymentHold h = stubReminderHold(LocalDateTime.now().plusHours(23), PaymentHoldStatus.PENDING_PAYMENT, true);
        LocalDateTime earlier = LocalDateTime.now().minusHours(1);
        h.setReminderSentAt(earlier);

        paymentService.sendPaymentReminder(auctionId);

        assertThat(h.getReminderSentAt()).isEqualTo(earlier);
        verify(paymentHoldRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void sendPaymentReminder_paidBetweenQueryAndLock_isNoOp() {
        PaymentHold h = stubReminderHold(LocalDateTime.now().plusHours(23), PaymentHoldStatus.COMPLETED, true);

        paymentService.sendPaymentReminder(auctionId);

        assertNoReminder(h);
    }

    @Test
    void sendPaymentReminder_notYetInsideTheWindow_isNoOp() {
        PaymentHold h = stubReminderHold(LocalDateTime.now().plusHours(25), PaymentHoldStatus.PENDING_PAYMENT, true);

        paymentService.sendPaymentReminder(auctionId);

        assertNoReminder(h);
    }

    @Test
    void sendPaymentReminder_deadlinePassed_isNoOp() {
        PaymentHold h = stubReminderHold(LocalDateTime.now().minusMinutes(1), PaymentHoldStatus.PENDING_PAYMENT, true);

        paymentService.sendPaymentReminder(auctionId);

        assertNoReminder(h);
    }

    @Test
    void sendPaymentReminder_lockedByAnotherInstance_isNoOp() {
        when(paymentHoldRepository.findByAuctionIdForUpdateSkipLocked(auctionId)).thenReturn(Optional.empty());

        paymentService.sendPaymentReminder(auctionId);

        verify(paymentHoldRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run (from `backend/`): `mvn -q -pl wallet-service -am test -Dtest=PaymentServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure (`sendPaymentReminder` is not defined).

- [ ] **Step 3: Implement**

In `WM/service/PaymentService.java`, add after `forfeitExpiredHold`:

```java

    /** Sends payment reminder #2 for an unpaid hold inside the reminder window; a no-op otherwise. */
    void sendPaymentReminder(UUID auctionId);
```

In `WM/service/impl/PaymentServiceImpl.java`:
- Add a field after `deadlineHours`:

```java

    @Value("${wallet.payment.reminder-before-deadline-hours:24}")
    private long reminderBeforeDeadlineHours = 24;
```

- Add this method after `forfeitExpiredHold` (before `lockWallet`):

```java
    @Override
    @Transactional
    public void sendPaymentReminder(UUID auctionId) {
        // Skipped if another instance (or a payment confirmation) holds the row; the next run retries.
        Optional<PaymentHold> maybeHold = paymentHoldRepository.findByAuctionIdForUpdateSkipLocked(auctionId);
        if (maybeHold.isEmpty()) {
            return;
        }
        PaymentHold hold = maybeHold.get();
        LocalDateTime now = LocalDateTime.now();
        boolean due = hold.getStatus() == PaymentHoldStatus.PENDING_PAYMENT
                && hold.getReminderSentAt() == null
                && now.isBefore(hold.getDeadline())
                && !hold.getDeadline().isAfter(now.plusHours(reminderBeforeDeadlineHours));
        if (!due) {
            return;
        }

        hold.setReminderSentAt(now);
        paymentHoldRepository.save(hold);

        eventPublisher.publishEvent(new PaymentApplicationEvent(this, PaymentEvent.builder()
                .auctionId(auctionId)
                .userId(hold.getWinnerUserId())
                .sellerId(hold.getSellerUserId())
                .amount(hold.getTotalAmount())
                .depositAmount(hold.getDepositApplied())
                .remaining(hold.getRemainingAmount())
                .deadline(hold.getDeadline().atZone(ZoneId.systemDefault()).toInstant())
                .insufficientFunds(!hold.isFundsHeld())
                .paymentType("REMINDER_24H")
                .build()));

        log.info("Payment reminder sent for auctionId={}, winner={}, deadline={}",
                auctionId, hold.getWinnerUserId(), hold.getDeadline());
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q -pl wallet-service -am test -Dtest=PaymentServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS. All existing tests plus the 7 new ones.

- [ ] **Step 5: Commit**: skipped (the user commits).

---

### Task 3: `PaymentReminderScheduler` (wallet-service)

**Files:**
- Create: `WM/scheduler/PaymentReminderScheduler.java`
- Modify: `backend/wallet-service/src/main/resources/application.yml`
- Test: `WT/scheduler/PaymentReminderSchedulerTest.java` (create)

**Interfaces:**
- Consumes: `PaymentHoldRepository.findDueForReminderAuctionIds(PaymentHoldStatus, LocalDateTime now, LocalDateTime remindBefore, Pageable)` (Task 1); `PaymentService.sendPaymentReminder(UUID)` (Task 2). `@EnableScheduling` already exists in `WM/config/SchedulingConfig.java`.
- Produces: `@Component PaymentReminderScheduler(PaymentHoldRepository, PaymentService, @Value("${wallet.payment.reminder-batch-size:100}") int batchSize, @Value("${wallet.payment.reminder-before-deadline-hours:24}") long reminderBeforeDeadlineHours)` with `void sendDueReminders()`.

- [ ] **Step 1: Write the failing test**

Create `WT/scheduler/PaymentReminderSchedulerTest.java`:

```java
package com.bidnow.wallet.scheduler;

import com.bidnow.wallet.domain.enums.PaymentHoldStatus;
import com.bidnow.wallet.repository.PaymentHoldRepository;
import com.bidnow.wallet.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentReminderSchedulerTest {

    @Mock
    private PaymentHoldRepository paymentHoldRepository;

    @Mock
    private PaymentService paymentService;

    private PaymentReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PaymentReminderScheduler(paymentHoldRepository, paymentService, 25, 24);
    }

    @Test
    void sendDueReminders_queriesTheReminderWindowWithBatchSize() {
        when(paymentHoldRepository.findDueForReminderAuctionIds(any(), any(), any(), any())).thenReturn(List.of());
        LocalDateTime before = LocalDateTime.now();

        scheduler.sendDueReminders();

        ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> remindBefore = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentHoldRepository).findDueForReminderAuctionIds(eq(PaymentHoldStatus.PENDING_PAYMENT),
                now.capture(), remindBefore.capture(), page.capture());
        assertThat(now.getValue()).isBetween(before, LocalDateTime.now());
        assertThat(Duration.between(now.getValue(), remindBefore.getValue())).isEqualTo(Duration.ofHours(24));
        assertThat(page.getValue()).isEqualTo(PageRequest.of(0, 25));
        verifyNoInteractions(paymentService);
    }

    @Test
    void sendDueReminders_remindsEveryDueHold() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(paymentHoldRepository.findDueForReminderAuctionIds(any(), any(), any(), any())).thenReturn(List.of(a, b));

        scheduler.sendDueReminders();

        verify(paymentService).sendPaymentReminder(a);
        verify(paymentService).sendPaymentReminder(b);
    }

    @Test
    void sendDueReminders_continuesAfterOneFailure() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(paymentHoldRepository.findDueForReminderAuctionIds(any(), any(), any(), any())).thenReturn(List.of(a, b));
        doThrow(new RuntimeException("db down")).when(paymentService).sendPaymentReminder(a);

        scheduler.sendDueReminders();

        verify(paymentService).sendPaymentReminder(b);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -pl wallet-service -am test -Dtest=PaymentReminderSchedulerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure (`PaymentReminderScheduler` is not defined).

- [ ] **Step 3: Implement**

Create `WM/scheduler/PaymentReminderScheduler.java`:

```java
package com.bidnow.wallet.scheduler;

import com.bidnow.wallet.domain.enums.PaymentHoldStatus;
import com.bidnow.wallet.repository.PaymentHoldRepository;
import com.bidnow.wallet.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Sends payment reminder #2 to winners who have not paid when their deadline is within the reminder window
 * (default 24h). Not transactional: each reminder runs in its own transaction; a failure is logged and the hold is
 * retried on the next run. Safe on several instances — {@code sendPaymentReminder} locks the hold with SKIP LOCKED
 * and stamps {@code reminder_sent_at}, so a hold is reminded once.
 */
@Slf4j
@Component
public class PaymentReminderScheduler {

    private final PaymentHoldRepository paymentHoldRepository;
    private final PaymentService paymentService;
    private final int batchSize;
    private final long reminderBeforeDeadlineHours;

    public PaymentReminderScheduler(PaymentHoldRepository paymentHoldRepository,
                                    PaymentService paymentService,
                                    @Value("${wallet.payment.reminder-batch-size:100}") int batchSize,
                                    @Value("${wallet.payment.reminder-before-deadline-hours:24}")
                                    long reminderBeforeDeadlineHours) {
        this.paymentHoldRepository = paymentHoldRepository;
        this.paymentService = paymentService;
        this.batchSize = batchSize;
        this.reminderBeforeDeadlineHours = reminderBeforeDeadlineHours;
    }

    @Scheduled(initialDelayString = "${wallet.payment.reminder-initial-delay-ms:60000}",
               fixedDelayString = "${wallet.payment.reminder-interval-ms:300000}")
    public void sendDueReminders() {
        LocalDateTime now = LocalDateTime.now();
        List<UUID> auctionIds = paymentHoldRepository.findDueForReminderAuctionIds(PaymentHoldStatus.PENDING_PAYMENT,
                now, now.plusHours(reminderBeforeDeadlineHours), PageRequest.of(0, batchSize));
        int succeeded = 0;
        for (UUID auctionId : auctionIds) {
            try {
                paymentService.sendPaymentReminder(auctionId);
                succeeded++;
            } catch (RuntimeException ex) {
                log.error("Payment reminder failed for auctionId={}", auctionId, ex);
            }
        }
        if (!auctionIds.isEmpty()) {
            log.info("Payment reminder run: {} due holds found, {} processed without error", auctionIds.size(), succeeded);
        }
    }
}
```

In `backend/wallet-service/src/main/resources/application.yml`, extend `wallet.payment` (after `forfeit-initial-delay-ms: 60000`, same indentation):

```yaml
    reminder-before-deadline-hours: 24   # payment reminder #2 is sent when this much time is left (NOTIF-106)
    reminder-interval-ms: 300000
    reminder-batch-size: 100
    reminder-initial-delay-ms: 60000
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q -pl wallet-service -am test -Dtest=PaymentReminderSchedulerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (3 tests).

Run: `mvn -q -pl wallet-service -am test`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**: skipped (the user commits).

---

### Task 4: media-service maps `REMINDER_24H` to the reminder #2 notification

**Files:**
- Modify: `MM/notification/handler/PaymentNotificationHandler.java`
- Test: `MT/notification/handler/PaymentNotificationHandlerTest.java` (modify), `MT/notification/NotificationTemplatesPostgresIT.java` (modify)

**Interfaces:**
- Consumes: `PaymentEvent` with `paymentType="REMINDER_24H"` and the fields `auctionId, userId, sellerId, amount, depositAmount, remaining, deadline, insufficientFunds` (Task 2's contract); `NotificationType.PAYMENT_REMINDER` (existing enum value); `DedupKeys.payment(String, UUID)`; the seeded `PAYMENT_REMINDER_2_{EN,VI}` templates (variables `userName, auctionTitle, bidAmount, paymentDeadline, actionUrl`).
- Produces: `PaymentNotificationHandler.paymentEvent` handles `REMINDER_24H`. The public API is otherwise unchanged.

- [ ] **Step 1: Write the failing tests**

In `MT/notification/handler/PaymentNotificationHandlerTest.java`:
- In `unknownPaymentType_dispatchesNothing`, change `payment("REMINDER_24H")` to `payment("REFUNDED")`. `REMINDER_24H` is now a known type.
- Add:

```java
    @Test
    void reminder_sendsTransactionalFinalNoticeEmail() {
        when(auctions.title(AUCTION, null)).thenReturn("Vintage Watch");

        handler.paymentEvent(payment("REMINDER_24H").insufficientFunds(false).build());

        NotificationIntent intent = dispatchedSingle();
        assertThat(intent.userId()).isEqualTo(WINNER);
        assertThat(intent.type()).isEqualTo(NotificationType.PAYMENT_REMINDER);
        assertThat(intent.dedupKey()).isEqualTo(DedupKeys.payment("REMINDER_24H", AUCTION));
        assertThat(intent.title()).isEqualTo("Payment reminder");
        assertThat(intent.actionUrl()).isEqualTo("/wallet");
        assertThat(intent.message()).isEqualTo("You still need to pay for \"Vintage Watch\". Please complete your "
                + "payment of $1,350.00 by 2026-10-03 10:00 UTC. Unpaid wins are cancelled after the deadline.");
        assertThat(intent.email().templateBaseName()).isEqualTo("PAYMENT_REMINDER_2");
        assertThat(intent.email().transactional()).isTrue();
        assertThat(intent.email().variables())
                .containsEntry("auctionTitle", "Vintage Watch")
                .containsEntry("bidAmount", "$1,350.00")
                .containsEntry("paymentDeadline", "2026-10-03 10:00 UTC")
                .containsEntry("actionUrl", "http://localhost:3000/wallet");
    }

    @Test
    void reminder_withZeroRemaining_billsTheTotalAndAsksToConfirm() {
        when(auctions.title(AUCTION, null)).thenReturn("Vintage Watch");

        handler.paymentEvent(payment("REMINDER_24H").remaining(BigDecimal.ZERO).insufficientFunds(false).build());

        NotificationIntent intent = dispatchedSingle();
        assertThat(intent.message()).contains("Please confirm your payment of $1,500.00 by 2026-10-03 10:00 UTC.");
        assertThat(intent.email().variables()).containsEntry("bidAmount", "$1,500.00");
    }

    @Test
    void reminder_withInsufficientFunds_asksToTopUp() {
        when(auctions.title(AUCTION, null)).thenReturn("Vintage Watch");

        handler.paymentEvent(payment("REMINDER_24H").insufficientFunds(true).build());

        assertThat(dispatchedSingle().message()).contains("Please top up your wallet and pay $1,350.00");
    }
```

In `MT/notification/NotificationTemplatesPostgresIT.java`:
- Change the payment loop list to `List.of("REQUIRED", "REMINDER_24H", "COMPLETED", "FAILED")`.
- Add `"PAYMENT_REMINDER_2"` to the `.contains(...)` list of expected template base names.

- [ ] **Step 2: Run the tests to verify they fail**

Run (from `backend/`): `mvn -q -pl media-service -am test -Dtest=PaymentNotificationHandlerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL. The three `reminder_*` tests fail because the `REMINDER_24H` branch dispatches nothing yet (`Wanted but not invoked: dispatcher.dispatch`).

- [ ] **Step 3: Implement**

In `MM/notification/handler/PaymentNotificationHandler.java`:

1. In the `paymentEvent` switch, add a case after `"REQUIRED"`:

```java
            case "REMINDER_24H" -> paymentReminder(event);
```

2. Replace `paymentRequired` with the version below. It produces the same copy as before, now built from the shared helper. Then add `paymentReminder` and `payInstruction`:

```java
    private void paymentRequired(PaymentEvent event) {
        UUID auctionId = event.getAuctionId();
        String title = auctions.title(auctionId, event.getAuctionTitle());
        dispatcher.dispatch(new NotificationIntent(event.getUserId(), NotificationType.PAYMENT_REQUIRED,
                DedupKeys.payment("REQUIRED", auctionId), auctionId, "Payment required",
                "You won \"" + title + "\". " + payInstruction(event),
                NotificationLinks.WALLET_PATH, null,
                new EmailSpec("AUCTION_WON", Map.of(
                        "auctionTitle", title,
                        "bidAmount", money(event.getAmount()),
                        "paymentDeadline", deadline(event.getDeadline()),
                        "actionUrl", links.absolute(NotificationLinks.WALLET_PATH)), true)));
    }

    /** Payment reminder #2 (wallet sends it once, 24h before the deadline): the "final notice" email. */
    private void paymentReminder(PaymentEvent event) {
        UUID auctionId = event.getAuctionId();
        String title = auctions.title(auctionId, event.getAuctionTitle());
        // The template says "your payment of {bidAmount}": what is still owed, or the total when only a confirm is left
        BigDecimal owed = isZero(event.getRemaining()) ? event.getAmount() : event.getRemaining();
        dispatcher.dispatch(new NotificationIntent(event.getUserId(), NotificationType.PAYMENT_REMINDER,
                DedupKeys.payment("REMINDER_24H", auctionId), auctionId, "Payment reminder",
                "You still need to pay for \"" + title + "\". " + payInstruction(event)
                        + " Unpaid wins are cancelled after the deadline.",
                NotificationLinks.WALLET_PATH, null,
                new EmailSpec("PAYMENT_REMINDER_2", Map.of(
                        "auctionTitle", title,
                        "bidAmount", money(owed),
                        "paymentDeadline", deadline(event.getDeadline()),
                        "actionUrl", links.absolute(NotificationLinks.WALLET_PATH)), true)));
    }

    /** What the winner has to do, ending with a period: confirm, top up and pay, or pay the remainder (by the deadline). */
    private static String payInstruction(PaymentEvent event) {
        String byDeadline = event.getDeadline() == null ? "" : " by " + deadline(event.getDeadline());
        if (isZero(event.getRemaining())) {
            return "Please confirm your payment of " + money(event.getAmount()) + byDeadline + ".";
        }
        if (Boolean.TRUE.equals(event.getInsufficientFunds())) {
            return "Please top up your wallet and pay " + money(event.getRemaining()) + byDeadline + ".";
        }
        return "Please complete your payment of " + money(event.getRemaining()) + byDeadline + ".";
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q -pl media-service -am test -Dtest=PaymentNotificationHandlerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS. All existing `required_*` tests are unchanged and still pass, which proves the REQUIRED copy is identical; plus the 3 new tests.

Run (Docker running): `mvn -q -pl media-service -am test -Dtest=NotificationTemplatesPostgresIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS. `PAYMENT_REMINDER_2_{EN,VI}` render with no `{…}` left.

Run: `mvn -q -pl media-service -am test`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**: skipped (the user commits).

---

### Task 5: Docs, verification and roadmap

**Files:**
- Modify: `docs/architecture.md`, `docs/superpowers/plans/2026-09-30-notification-epic-roadmap.md`

- [ ] **Step 1: Update the event contract**

In `docs/architecture.md`, in the wallet-service `payment-event-topic` row (around line 109):
- Replace `` `PaymentEvent { paymentType: REQUIRED \| COMPLETED \| FAILED, … }` `` with `` `PaymentEvent { paymentType: REQUIRED \| REMINDER_24H \| COMPLETED \| FAILED, … }` ``.
- In the description cell, replace `Payment required (with deadline, `insufficientFunds`) / payment completed` with `Payment required (with deadline, `insufficientFunds`) / payment reminder #2 (once per hold, sent by the 5-minute reminder scheduler when 24 h are left; same fields as REQUIRED; tracked by `payment_holds.reminder_sent_at`) / payment completed`.
- In the same cell's field-meanings sentence, change "`depositAmount` is the deposit applied for REQUIRED/COMPLETED" to "`depositAmount` is the deposit applied for REQUIRED/REMINDER_24H/COMPLETED", and "`remaining` is still owed for REQUIRED" to "`remaining` is still owed for REQUIRED/REMINDER_24H".

- [ ] **Step 2: Run the full verification**

Run (from `backend/`): `mvn -q -pl wallet-service,media-service -am test`
Expected: BUILD SUCCESS.

Run (Docker running): `mvn -q -pl wallet-service -am test -Dtest=PaymentHoldReminderQueryPostgresIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (3 tests).

Run (Docker running): `mvn -q -pl media-service -am test -Dtest=NotificationTemplatesPostgresIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 3: Update the roadmap**

In Story 6 of `docs/superpowers/plans/2026-09-30-notification-epic-roadmap.md`:
- Tick tasks 6.1–6.4.
- Change the `findDueForReminder` file bullet and the `reminder-after-hours` config to match what was built: `findDueForReminderAuctionIds(status, now, remindBefore, page)` (`deadline > now AND deadline <= now + 24h AND reminder_sent_at IS NULL`) and `wallet.payment.reminder-before-deadline-hours: 24`.
- Add a refinements paragraph:

```
**Refinements (implemented):** due-ness is measured from the deadline (`deadline <= now + 24h`, not yet expired) rather than `created_at` — identical with the 48h deadline, but keeps "24 hours left" true if `deadline-hours` changes and reuses the `(status, deadline)` index; the service re-checks status / not-reminded / window under `SKIP LOCKED` (paid-in-between and racing instances are no-ops); the email's `{bidAmount}` is the amount still owed (the total when only a confirmation is left); REQUIRED and REMINDER_24H share one "please confirm / top up / pay" sentence. Wallet's repository query has a real-Postgres `@DataJpaTest` IT (`PaymentHoldReminderQueryPostgresIT`, explicit run).
```

- [ ] **Step 4: Manual smoke (handed to the user; a merge gate)**

The user runs this with `docker compose up` and these local wallet-service overrides: `wallet.payment.deadline-hours: 2`, `wallet.payment.reminder-before-deadline-hours: 1` and `wallet.payment.reminder-interval-ms: 30000`. The hours are whole numbers, so this is the shortest window.
1. End an auction with a winner and don't pay. The winner receives the REQUIRED email at once.
2. About 1 h later (when 1 h is left), the winner receives one "URGENT: Final notice" email plus an in-app `PAYMENT_REMINDER`, and `payment_holds.reminder_sent_at` is set.
3. No second reminder arrives on later scheduler runs. At the deadline the forfeit email (`FAILED`) arrives.
4. Paying before the reminder window means no reminder is ever sent.

- [ ] **Step 5: Commit**: skipped. The user commits `feat(notification): send payment reminder #2 (NOTIF-106)`.
