# Auction Payment Event Consumer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Auction-service consumes wallet's `PaymentEvent` and projects the payment outcome onto the auction: `payment_deadline`, `winner_paid_at`, and `COMPLETED → FAILED` on forfeit.

**Architecture:** A thin `@KafkaListener` (`PaymentEventConsumer`) delegates to a transactional `AuctionPaymentService` that row-locks the auction (`findByIdForUpdate`) and applies idempotent, state-guarded effects. A `KafkaConsumerConfig` (copied from wallet-service) gives every listener 1s/2s/4s retries, then `<topic>.DLT`. No schema, API or UI change.

**Tech Stack:** Java 17, Spring Boot 3.2, Spring Kafka, JPA, JUnit 5 + Mockito + AssertJ, Maven.

**Spec:** `docs/superpowers/specs/2026-10-15-auction-payment-event-consumer-design.md`

## Global Constraints

- Topic `payment-event-topic`, payload class `com.bidnow.common.dto.event.PaymentEvent`, unchanged. Do not touch wallet-service or `common`.
- Consumer group id `auction-service-group` (`spring.kafka.consumer.group-id`), `auto-offset-reset: earliest`.
- Retry 1s / 2s / 4s (three retries, multiplier 2.0), then `<topic>.DLT`.
- No schema change, no Liquibase migration, no new DTO field, no new `AuctionStatus` value.
- `paymentType` handled: `REQUIRED`, `COMPLETED`, `FAILED`; all others ignored.
- The paid timestamp is the consume time from the injected `Clock`; no timestamp is added to `PaymentEvent`.
- No new Kafka event is published.
- Backend tests are JUnit/Mockito (`AGENTS.md`). Run Maven from `backend/`.
- **No git commits.** The project owner's rule is to commit only when explicitly asked, so this plan has no commit steps.

## Review Focus

Spec-implied inputs that no obvious test would cover, most likely first. Each is pinned by a named test in Task 2 (or Task 3 where noted).

- A forfeit (`FAILED`) event for a sale whose `winnerPaidAt` is already set must not fail a paid sale. Test: `failed_afterPayment_isIgnored`.
- An `ACTIVE` auction has no `winnerId` yet, so the (defensive) retry guard must run before the winner check and throw, not silently skip. It should not happen, because every `AuctionEndedEvent` is published after its transaction commits; if it ever does, the throw lets the error handler retry (1s/2s/4s) before dead-lettering. Test: `activeAuction_withoutWinner_throwsSoItIsRetried`.
- A malformed event (null event, null `auctionId`, null or unknown `paymentType`) must be dropped, not sent to the DLT. Tests: `malformedEvents_areIgnoredWithoutTouchingTheDatabase` (Task 2) and `onPaymentEvent_delegatesNullEvent` (Task 3).
- `REQUIRED` with a null `deadline` must not null out or overwrite anything. Test: `required_withoutDeadline_changesNothing`.
- An event whose `userId` is not the auction's winner (or is null) must be skipped. Tests: `wrongUser_isSkipped`, `nullUser_isSkipped`.

---

## File Structure

| File | Responsibility |
|---|---|
| `backend/auction-service/src/main/resources/application.yml` (modify) | `spring.kafka.consumer` block |
| `backend/auction-service/src/main/java/com/bidnow/auction/config/KafkaConsumerConfig.java` (create) | Retry + DLT error handler |
| `backend/auction-service/src/main/java/com/bidnow/auction/service/AuctionPaymentService.java` (create) | Guards and the three effects |
| `backend/auction-service/src/main/java/com/bidnow/auction/kafka/PaymentEventConsumer.java` (create) | `@KafkaListener`, delegates only |
| `backend/auction-service/src/test/java/com/bidnow/auction/config/KafkaConsumerConfigTest.java` (create) | Backoff values |
| `backend/auction-service/src/test/java/com/bidnow/auction/service/AuctionPaymentServiceTest.java` (create) | All behaviour |
| `backend/auction-service/src/test/java/com/bidnow/auction/kafka/PaymentEventConsumerTest.java` (create) | Delegation |
| `docs/...` (modify) | See Task 4 |

---

### Task 1: Consumer configuration (error handler + yml)

**Files:**
- Create: `backend/auction-service/src/main/java/com/bidnow/auction/config/KafkaConsumerConfig.java`
- Create: `backend/auction-service/src/test/java/com/bidnow/auction/config/KafkaConsumerConfigTest.java`
- Modify: `backend/auction-service/src/main/resources/application.yml` (the `spring.kafka` block, lines 32-38)

**Interfaces:**
- Produces: bean `DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object>)`; package-private `static ExponentialBackOffWithMaxRetries retryBackOff()`. Spring Boot applies the single `CommonErrorHandler` bean to every `@KafkaListener` container.
- Produces: property `spring.kafka.consumer.group-id` (read by Task 3's listener).

- [ ] **Step 1: Write the failing test**

Create `KafkaConsumerConfigTest.java`:

```java
package com.bidnow.auction.config;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaConsumerConfigTest {

    @Test
    void retryBackOff_isThreeRetriesStartingAtOneSecondDoubling() {
        ExponentialBackOffWithMaxRetries backOff = KafkaConsumerConfig.retryBackOff();

        assertThat(backOff.getMaxRetries()).isEqualTo(3);
        assertThat(backOff.getInitialInterval()).isEqualTo(1_000L);
        assertThat(backOff.getMultiplier()).isEqualTo(2.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void kafkaErrorHandler_isBuilt() {
        KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);

        DefaultErrorHandler handler = new KafkaConsumerConfig().kafkaErrorHandler(template);

        assertThat(handler).isNotNull();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run (from `backend/`):

```bash
mvn -q test -pl auction-service -am -Dtest=KafkaConsumerConfigTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compilation FAIL, `cannot find symbol: class KafkaConsumerConfig`.

- [ ] **Step 3: Write the implementation**

Create `KafkaConsumerConfig.java`:

```java
package com.bidnow.auction.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Retries failed records with exponential backoff (1s, 2s, 4s), then publishes them to
 * {@code <topic>.DLT}. Spring Boot applies this CommonErrorHandler to every auction-service listener.
 */
@Configuration
public class KafkaConsumerConfig {

    static final int MAX_RETRIES = 3;
    static final long INITIAL_INTERVAL_MS = 1_000L;
    static final double MULTIPLIER = 2.0;

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafkaTemplate), retryBackOff());
    }

    static ExponentialBackOffWithMaxRetries retryBackOff() {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(MAX_RETRIES);
        backOff.setInitialInterval(INITIAL_INTERVAL_MS);
        backOff.setMultiplier(MULTIPLIER);
        return backOff;
    }
}
```

In `application.yml`, add the `consumer` block directly after the `producer` block (same indentation as `producer:`):

```yaml
    consumer:
      group-id: auction-service-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      properties:
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
        spring.json.trusted.packages: "*"
        reconnect.backoff.ms: 5000
        reconnect.backoff.max.ms: 30000
```

- [ ] **Step 4: Run the test to verify it passes**

Run the same command as Step 2. Expected: 2 tests PASS.

---

### Task 2: `AuctionPaymentService` (guards and effects)

**Files:**
- Create: `backend/auction-service/src/main/java/com/bidnow/auction/service/AuctionPaymentService.java`
- Create: `backend/auction-service/src/test/java/com/bidnow/auction/service/AuctionPaymentServiceTest.java`

**Interfaces:**
- Consumes: `AuctionItemRepository.findByIdForUpdate(UUID) : Optional<AuctionItem>`; `AuctionStatusHistoryRepository.save`; `Clock` bean (`ClockConfig`); `PaymentEvent` getters `getAuctionId() getUserId() getPaymentType() getDeadline()` (`Instant`).
- Produces: `public void applyPayment(PaymentEvent event)` (`@Transactional`). Throws `IllegalStateException` only when the auction is `ACTIVE`. Used by Task 3.

- [ ] **Step 1: Write the failing tests**

Create `AuctionPaymentServiceTest.java`:

```java
package com.bidnow.auction.service;

import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.entity.AuctionStatusHistory;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.repository.AuctionItemRepository;
import com.bidnow.auction.repository.AuctionStatusHistoryRepository;
import com.bidnow.common.dto.event.PaymentEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionPaymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
    private static final Instant DEADLINE = Instant.parse("2026-10-17T12:00:00Z");
    private static final OffsetDateTime EARLIER_PAID_AT =
            OffsetDateTime.ofInstant(Instant.parse("2026-10-14T09:00:00Z"), ZoneOffset.UTC);

    @Mock
    private AuctionItemRepository auctionItemRepository;
    @Mock
    private AuctionStatusHistoryRepository auctionStatusHistoryRepository;

    private AuctionPaymentService service;

    private final UUID auctionId = UUID.randomUUID();
    private final UUID winnerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AuctionPaymentService(auctionItemRepository, auctionStatusHistoryRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private AuctionItem auction(AuctionStatus status, UUID winner) {
        return AuctionItem.builder()
                .id(auctionId)
                .status(status)
                .winnerId(winner)
                .build();
    }

    private AuctionItem stubAuction(AuctionStatus status, UUID winner) {
        AuctionItem auction = auction(status, winner);
        when(auctionItemRepository.findByIdForUpdate(auctionId)).thenReturn(Optional.of(auction));
        return auction;
    }

    private PaymentEvent event(String type) {
        return PaymentEvent.builder()
                .auctionId(auctionId)
                .userId(winnerId)
                .paymentType(type)
                .deadline(DEADLINE)
                .build();
    }

    // ---- REQUIRED ----

    @Test
    void required_setsPaymentDeadline() {
        AuctionItem auction = stubAuction(AuctionStatus.COMPLETED, winnerId);

        service.applyPayment(event("REQUIRED"));

        assertThat(auction.getPaymentDeadline()).isEqualTo(OffsetDateTime.ofInstant(DEADLINE, ZoneOffset.UTC));
        verify(auctionItemRepository).save(auction);
    }

    @Test
    void required_whenDeadlineAlreadySet_keepsTheFirstValue() {
        AuctionItem auction = stubAuction(AuctionStatus.COMPLETED, winnerId);
        OffsetDateTime first = OffsetDateTime.ofInstant(Instant.parse("2026-10-16T00:00:00Z"), ZoneOffset.UTC);
        auction.setPaymentDeadline(first);

        service.applyPayment(event("REQUIRED"));

        assertThat(auction.getPaymentDeadline()).isEqualTo(first);
        verify(auctionItemRepository, never()).save(any());
    }

    @Test
    void required_withoutDeadline_changesNothing() {
        AuctionItem auction = stubAuction(AuctionStatus.COMPLETED, winnerId);
        PaymentEvent noDeadline = event("REQUIRED");
        noDeadline.setDeadline(null);

        service.applyPayment(noDeadline);

        assertThat(auction.getPaymentDeadline()).isNull();
        verify(auctionItemRepository, never()).save(any());
    }

    // ---- COMPLETED ----

    @Test
    void completed_setsWinnerPaidAtToConsumeTime() {
        AuctionItem auction = stubAuction(AuctionStatus.COMPLETED, winnerId);

        service.applyPayment(event("COMPLETED"));

        assertThat(auction.getWinnerPaidAt()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.COMPLETED);
        verify(auctionItemRepository).save(auction);
    }

    @Test
    void completed_replay_keepsTheFirstValue() {
        AuctionItem auction = stubAuction(AuctionStatus.COMPLETED, winnerId);
        auction.setWinnerPaidAt(EARLIER_PAID_AT);

        service.applyPayment(event("COMPLETED"));

        assertThat(auction.getWinnerPaidAt()).isEqualTo(EARLIER_PAID_AT);
        verify(auctionItemRepository, never()).save(any());
    }

    // ---- FAILED (forfeit) ----

    @Test
    void failed_onCompletedAuction_movesToFailedAndRecordsHistory() {
        AuctionItem auction = stubAuction(AuctionStatus.COMPLETED, winnerId);

        service.applyPayment(event("FAILED"));

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.FAILED);
        verify(auctionItemRepository).save(auction);

        ArgumentCaptor<AuctionStatusHistory> history = ArgumentCaptor.forClass(AuctionStatusHistory.class);
        verify(auctionStatusHistoryRepository).save(history.capture());
        assertThat(history.getValue().getAuction()).isSameAs(auction);
        assertThat(history.getValue().getFromStatus()).isEqualTo("COMPLETED");
        assertThat(history.getValue().getToStatus()).isEqualTo("FAILED");
        assertThat(history.getValue().getTriggeredBy()).isNull();
        assertThat(history.getValue().getReason()).isEqualTo("Winner did not pay by the payment deadline");
    }

    @Test
    void failed_afterPayment_isIgnored() {
        AuctionItem auction = stubAuction(AuctionStatus.COMPLETED, winnerId);
        auction.setWinnerPaidAt(EARLIER_PAID_AT);

        service.applyPayment(event("FAILED"));

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.COMPLETED);
        verify(auctionItemRepository, never()).save(any());
        verifyNoInteractions(auctionStatusHistoryRepository);
    }

    @Test
    void failed_replay_whenAlreadyFailed_isNoOp() {
        AuctionItem auction = stubAuction(AuctionStatus.FAILED, winnerId);

        service.applyPayment(event("FAILED"));

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.FAILED);
        verify(auctionItemRepository, never()).save(any());
        verifyNoInteractions(auctionStatusHistoryRepository);
    }

    @Test
    void completed_afterFailed_isIgnored() {
        AuctionItem auction = stubAuction(AuctionStatus.FAILED, winnerId);

        service.applyPayment(event("COMPLETED"));

        assertThat(auction.getWinnerPaidAt()).isNull();
        verify(auctionItemRepository, never()).save(any());
    }

    // ---- guards ----

    @Test
    void unknownAuction_isSkipped() {
        when(auctionItemRepository.findByIdForUpdate(auctionId)).thenReturn(Optional.empty());

        service.applyPayment(event("COMPLETED"));

        verify(auctionItemRepository, never()).save(any());
    }

    @Test
    void wrongUser_isSkipped() {
        AuctionItem auction = stubAuction(AuctionStatus.COMPLETED, winnerId);
        PaymentEvent other = event("COMPLETED");
        other.setUserId(UUID.randomUUID());

        service.applyPayment(other);

        assertThat(auction.getWinnerPaidAt()).isNull();
        verify(auctionItemRepository, never()).save(any());
    }

    @Test
    void nullUser_isSkipped() {
        AuctionItem auction = stubAuction(AuctionStatus.COMPLETED, winnerId);
        PaymentEvent anonymous = event("COMPLETED");
        anonymous.setUserId(null);

        service.applyPayment(anonymous);

        assertThat(auction.getWinnerPaidAt()).isNull();
        verify(auctionItemRepository, never()).save(any());
    }

    @Test
    void cancelledAuction_isSkipped() {
        AuctionItem auction = stubAuction(AuctionStatus.CANCELLED, winnerId);

        service.applyPayment(event("FAILED"));

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.CANCELLED);
        verify(auctionItemRepository, never()).save(any());
    }

    @Test
    void activeAuction_withoutWinner_throwsSoItIsRetried() {
        // An ACTIVE row has no winnerId yet, so this also pins "retry guard runs before the winner check".
        stubAuction(AuctionStatus.ACTIVE, null);

        assertThatThrownBy(() -> service.applyPayment(event("REQUIRED")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(auctionId.toString());

        verify(auctionItemRepository, never()).save(any());
    }

    @Test
    void malformedEvents_areIgnoredWithoutTouchingTheDatabase() {
        PaymentEvent noAuction = event("COMPLETED");
        noAuction.setAuctionId(null);

        service.applyPayment(null);
        service.applyPayment(noAuction);
        service.applyPayment(event(null));
        service.applyPayment(event("REMINDER_24H"));
        service.applyPayment(event("REFUNDED"));

        verifyNoInteractions(auctionItemRepository, auctionStatusHistoryRepository);
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
mvn -q test -pl auction-service -am -Dtest=AuctionPaymentServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compilation FAIL, `cannot find symbol: class AuctionPaymentService`.

- [ ] **Step 3: Write the implementation**

Create `AuctionPaymentService.java`:

```java
package com.bidnow.auction.service;

import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.entity.AuctionStatusHistory;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.repository.AuctionItemRepository;
import com.bidnow.auction.repository.AuctionStatusHistoryRepository;
import com.bidnow.common.dto.event.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Projects wallet-service's {@link PaymentEvent} onto the auction: the payment deadline, the paid
 * timestamp, and {@code COMPLETED → FAILED} when the winner forfeits. Wallet-service stays the system
 * of record; every effect here is guarded by a null or status check, so a replayed event is a no-op.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionPaymentService {

    static final String TYPE_REQUIRED = "REQUIRED";
    static final String TYPE_COMPLETED = "COMPLETED";
    static final String TYPE_FAILED = "FAILED";
    static final String FORFEIT_REASON = "Winner did not pay by the payment deadline";

    private final AuctionItemRepository auctionItemRepository;
    private final AuctionStatusHistoryRepository auctionStatusHistoryRepository;
    private final Clock clock;

    /**
     * @throws IllegalStateException if the auction is still {@code ACTIVE}: wallet creates a hold only after
     *                               the auction ended, but the admin force-close publishes before its
     *                               transaction commits, so the row can briefly lag. The Kafka error handler
     *                               retries, and the next attempt sees the committed state.
     */
    @Transactional
    public void applyPayment(PaymentEvent event) {
        if (event == null || event.getAuctionId() == null || !isHandled(event.getPaymentType())) {
            log.debug("Ignoring payment event {}", event);
            return;
        }

        UUID auctionId = event.getAuctionId();
        AuctionItem auction = auctionItemRepository.findByIdForUpdate(auctionId).orElse(null);
        if (auction == null) {
            log.warn("Payment event {} skipped: auction {} not found", event.getPaymentType(), auctionId);
            return;
        }
        if (auction.getStatus() == AuctionStatus.ACTIVE) {
            throw new IllegalStateException("Auction " + auctionId + " is still ACTIVE; payment event "
                    + event.getPaymentType() + " will be retried");
        }
        if (auction.getStatus() != AuctionStatus.COMPLETED) {
            log.debug("Payment event {} skipped: auction {} is {}", event.getPaymentType(), auctionId,
                    auction.getStatus());
            return;
        }
        if (event.getUserId() == null || !event.getUserId().equals(auction.getWinnerId())) {
            log.warn("Payment event {} skipped: user {} is not the winner of auction {}",
                    event.getPaymentType(), event.getUserId(), auctionId);
            return;
        }

        switch (event.getPaymentType()) {
            case TYPE_REQUIRED -> applyRequired(auction, event);
            case TYPE_COMPLETED -> applyCompleted(auction);
            case TYPE_FAILED -> applyFailed(auction);
            default -> { /* unreachable: isHandled() filtered the type */ }
        }
    }

    private static boolean isHandled(String paymentType) {
        return TYPE_REQUIRED.equals(paymentType) || TYPE_COMPLETED.equals(paymentType)
                || TYPE_FAILED.equals(paymentType);
    }

    private void applyRequired(AuctionItem auction, PaymentEvent event) {
        if (auction.getPaymentDeadline() != null || event.getDeadline() == null) {
            return;
        }
        auction.setPaymentDeadline(OffsetDateTime.ofInstant(event.getDeadline(), ZoneOffset.UTC));
        auctionItemRepository.save(auction);
        log.info("Auction {} payment deadline set to {}", auction.getId(), auction.getPaymentDeadline());
    }

    private void applyCompleted(AuctionItem auction) {
        if (auction.getWinnerPaidAt() != null) {
            return;
        }
        auction.setWinnerPaidAt(OffsetDateTime.now(clock));
        auctionItemRepository.save(auction);
        log.info("Auction {} paid by the winner at {}", auction.getId(), auction.getWinnerPaidAt());
    }

    private void applyFailed(AuctionItem auction) {
        if (auction.getWinnerPaidAt() != null) {
            return;
        }
        auction.setStatus(AuctionStatus.FAILED);
        auctionItemRepository.save(auction);
        auctionStatusHistoryRepository.save(AuctionStatusHistory.builder()
                .auction(auction)
                .fromStatus(AuctionStatus.COMPLETED.name())
                .toStatus(AuctionStatus.FAILED.name())
                .triggeredBy(null)
                .reason(FORFEIT_REASON)
                .build());
        log.info("Auction {} failed: winner did not pay (COMPLETED → FAILED)", auction.getId());
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run the Step 2 command. Expected: 15 tests PASS. If `Mockito strict stubs` reports an unnecessary stubbing, the test stubbed `findByIdForUpdate` for an event that returns before the lookup; fix the test, not the service.

---

### Task 3: `PaymentEventConsumer` (listener)

**Files:**
- Create: `backend/auction-service/src/main/java/com/bidnow/auction/kafka/PaymentEventConsumer.java`
- Create: `backend/auction-service/src/test/java/com/bidnow/auction/kafka/PaymentEventConsumerTest.java`

**Interfaces:**
- Consumes: `AuctionPaymentService.applyPayment(PaymentEvent)` from Task 2; property `spring.kafka.consumer.group-id` from Task 1.
- Produces: the `@KafkaListener` on `payment-event-topic`. Exceptions from the service must propagate so the error handler retries.

- [ ] **Step 1: Write the failing test**

Create `PaymentEventConsumerTest.java`:

```java
package com.bidnow.auction.kafka;

import com.bidnow.auction.service.AuctionPaymentService;
import com.bidnow.common.dto.event.PaymentEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    @Mock
    private AuctionPaymentService auctionPaymentService;

    @InjectMocks
    private PaymentEventConsumer consumer;

    @Test
    void onPaymentEvent_delegatesToTheService() {
        PaymentEvent event = PaymentEvent.builder()
                .auctionId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .paymentType("COMPLETED")
                .build();

        consumer.onPaymentEvent(event);

        verify(auctionPaymentService).applyPayment(event);
    }

    @Test
    void onPaymentEvent_delegatesNullEvent() {
        // A null value (tombstone) must reach the service, which ignores it, rather than NPE in the listener.
        consumer.onPaymentEvent(null);

        verify(auctionPaymentService).applyPayment(null);
    }

    @Test
    void onPaymentEvent_propagatesServiceFailureSoTheErrorHandlerRetries() {
        PaymentEvent event = PaymentEvent.builder().auctionId(UUID.randomUUID()).paymentType("FAILED").build();
        doThrow(new IllegalStateException("still ACTIVE")).when(auctionPaymentService).applyPayment(event);

        assertThatThrownBy(() -> consumer.onPaymentEvent(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("still ACTIVE");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
mvn -q test -pl auction-service -am -Dtest=PaymentEventConsumerTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compilation FAIL, `cannot find symbol: class PaymentEventConsumer`.

- [ ] **Step 3: Write the implementation**

Create `PaymentEventConsumer.java`:

```java
package com.bidnow.auction.kafka;

import com.bidnow.auction.service.AuctionPaymentService;
import com.bidnow.common.dto.event.PaymentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Receives wallet-service's payment events and hands them to {@link AuctionPaymentService}.
 * Deliberately not transactional and free of logic: the service owns the transaction and the guards,
 * and an exception it throws propagates so the Kafka error handler retries, then sends to the DLT.
 */
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final AuctionPaymentService auctionPaymentService;

    @KafkaListener(topics = "payment-event-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void onPaymentEvent(PaymentEvent event) {
        auctionPaymentService.applyPayment(event);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run the Step 2 command. Expected: 3 tests PASS.

---

### Task 4: Documentation

**Files (all modify):**
- `docs/epics/wallet/epic.md` (line 131)
- `docs/epics/auction/auction-service-schema.md` (rows at lines 36-37, the `FAILED` bullet at line 220)
- `docs/diagrams/05-winner-payment-flow.md` (line 76)
- `docs/superpowers/specs/2026-10-15-winner-payment-status-design.md` (table row at line 34, bullet at line 210)

- [ ] **Step 1: Epic follow-up**

In `docs/epics/wallet/epic.md`, replace the line starting `- [ ] Follow-ups: auction-service consumes `PaymentEvent`` with:

```markdown
- [x] auction-service consumes `PaymentEvent` (`REQUIRED` → `payment_deadline`, `COMPLETED` → `winner_paid_at`, `FAILED` → auction `COMPLETED → FAILED`); see `docs/superpowers/specs/2026-10-15-auction-payment-event-consumer-design.md`
```

- [ ] **Step 2: Schema doc**

In `docs/epics/auction/auction-service-schema.md`:
- `winner_paid_at` row, Description cell → `Set when wallet-service's PaymentEvent COMPLETED is consumed (consume time, not the payment instant)`
- `payment_deadline` row, Description cell → `Set from the deadline of wallet-service's PaymentEvent REQUIRED`
- the `FAILED` bullet (line 220) → ``- `FAILED`: Ended with no bids (`winner_id` null), or the winner did not pay (`winner_id` set; moved from `COMPLETED` when wallet-service's `PaymentEvent` `FAILED` is consumed)``

- [ ] **Step 3: Payment flow diagram**

In `docs/diagrams/05-winner-payment-flow.md`, replace the last sentence of line 76, from "wallet-service owns the payment facts and auction-service does not record payment (its `winner_paid_at` and `payment_deadline` columns stay unused). Revisit only if auction-service needs the paid fact for its own features." with:

```markdown
wallet-service owns the payment facts; auction-service keeps a projection of them. `PaymentEventConsumer` consumes `payment-event-topic`: `REQUIRED` sets `payment_deadline`, `COMPLETED` sets `winner_paid_at`, and `FAILED` (forfeit) moves the auction `COMPLETED → FAILED`.
```

- [ ] **Step 4: Winner-payment-status spec**

In `docs/superpowers/specs/2026-10-15-winner-payment-status-design.md`:
- Line 34 (Cost row, column B): change "Auction columns `winner_paid_at` / `payment_deadline` stay unused, as today" to "Auction columns `winner_paid_at` / `payment_deadline` stay unused (superseded: see `2026-10-15-auction-payment-event-consumer-design.md`)".
- Line 210: change the bullet to "Auction-service's `winner_paid_at` and `payment_deadline` columns were unused when this shipped; they are now written by the payment event consumer (`2026-10-15-auction-payment-event-consumer-design.md`)."

- [ ] **Step 5: Check the edits landed**

```bash
grep -n "PaymentEventConsumer\|payment-event-consumer-design" docs/epics/wallet/epic.md docs/diagrams/05-winner-payment-flow.md docs/superpowers/specs/2026-10-15-winner-payment-status-design.md docs/epics/auction/auction-service-schema.md
```

Expected: a hit in each of the four files.

---

### Task 5: Verify the whole module

- [ ] **Step 1: Run the auction-service tests**

```bash
mvn test -pl auction-service -am
```

Expected: BUILD SUCCESS, including the three new test classes (20 new tests). The BDD (Cucumber) suite starts Testcontainers (Postgres, Kafka, Redis) and needs Docker running. If Docker is unavailable, report that those scenarios were not run; do not report success for them.

- [ ] **Step 2: Confirm the listener starts**

In the BDD run's output, or with the service started locally against Kafka, check there is no `NoSuchBeanDefinitionException` for `KafkaTemplate` and that the log shows the consumer joining group `auction-service-group` for `payment-event-topic`.

- [ ] **Step 3: Optional end-to-end smoke (needs a local Kafka and a seeded `COMPLETED` auction)**

Publish a `PaymentEvent` (`paymentType` `COMPLETED`, the winner's `userId`) to `payment-event-topic` and confirm `auction_items.winner_paid_at` is set. Publish `FAILED` for an unpaid auction and confirm `status = 'FAILED'` and one `auction_status_history` row `COMPLETED → FAILED`.
