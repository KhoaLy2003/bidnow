# Story 6 — RT-101: Real-Time Auction Updates over STOMP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Browsers viewing an auction receive `BID_PLACED`, `AUCTION_EXTENDED`, `AUCTION_ENDED` and `AUCTION_CANCELLED` messages on `/topic/auctions/{auctionId}` within about a second of the commit. The outbid user also receives a private `OUTBID` message on `/user/queue/notifications`. Everything is reachable through the gateway, and anonymous viewers can subscribe to auction topics.

**Architecture:**
- **Broadcasting.** media-service already runs a Spring STOMP simple broker at `/ws-notifications` (SockJS). A new `AuctionRealtimeConsumer` listens to `bid-placed-topic`, `auction-extended-topic`, `auction-ended-topic` and `auction-cancelled-topic`. Each listener uses a **per-instance** consumer group (`media-realtime-${random.uuid}`, `auto.offset.reset=latest`), so every media instance forwards every event to its own connected clients. It then calls `AuctionRealtimeBroadcaster`, which maps each event to an `AuctionRealtimeMessage {type, auctionId, payload}` and sends it with `SimpMessagingTemplate`.
- **User identity.** `GatewayUserHandshakeHandler` turns the gateway-injected `X-User-Id` header into the STOMP `Principal`, so `convertAndSendToUser(userId, …)` reaches that user.
- **Gateway.** It routes `/ws-notifications/**` to `lb://media-service`, and Spring Cloud Gateway upgrades to `ws` automatically when the request carries `Upgrade: websocket`. The WebSocket path uses **optional authentication**: the gateway strips client-supplied identity headers, reads a JWT from `Authorization` or from the `access_token` query parameter (browsers cannot set headers on WebSocket/SockJS), injects `X-User-Id` when the JWT is valid, and removes `access_token` from the forwarded URI.

**Tech Stack:** Java 17, Spring Boot 3.2.4, Spring WebSocket/STOMP (simple broker, SockJS), Spring Kafka, Spring Cloud Gateway (WebFlux), Lombok, JUnit 5, Mockito, AssertJ.

**Spec:** `docs/superpowers/specs/2026-07-04-bidding-service-design.md` §6. Roadmap: `docs/superpowers/plans/2026-09-29-bidding-epic-roadmap.md` (Story 6).

## Prerequisites (check before Task 1)

- **Story 4 is merged into the working branch:**
  - `common/src/main/java/com/bidnow/common/dto/event/AuctionExtendedEvent.java` exists, with fields `auctionId, auctionTitle, previousEndTime, newEndTime, extensionCount, triggeredByBidId, triggeredByUserId`.
  - auction-service publishes it to `auction-extended-topic`.
- **Story 5 is merged into the working branch:** `api-gateway/src/test/java/com/bidnow/gateway/filter/AuthenticationFilterTest.java` exists, and `PUBLIC_PATHS` contains `/api/v1/bids/auction/*`. This plan edits the same filter and test.
- If either is missing, stop and report BLOCKED. Do not re-create them.

## Global Constraints

- **Scope:** media-service, api-gateway and docs. Do not modify common, auction-service, bidding-service, wallet-service or user-service.
- **Destinations:** the topic is `/topic/auctions/{auctionId}`. The user queue is `/user/queue/notifications`, sent with `convertAndSendToUser(userId.toString(), "/queue/notifications", …)`.
- **Message envelope:** `AuctionRealtimeMessage(String type, UUID auctionId, Object payload)`. The `type` values are exactly:
  - `BID_PLACED`
  - `AUCTION_EXTENDED`
  - `AUCTION_ENDED`
  - `AUCTION_CANCELLED`
  - `OUTBID`
- **Payloads** (JSON field names are fixed):
  - `BID_PLACED`: `{bidId, bidderId, bidderName, amount, placedAt, totalBids, endTime, antiSnipingTriggered}`. `placedAt` is `BidPlacedEvent.bidTime`, which is UTC `LocalDateTime`, converted to `OffsetDateTime` at `ZoneOffset.UTC`.
  - `AUCTION_EXTENDED`: `{previousEndTime, newEndTime, extensionCount}`
  - `AUCTION_ENDED`: `{winnerId, finalPrice, endedAt}`
  - `AUCTION_CANCELLED`: `{reason}`
  - `OUTBID`: `{auctionTitle, currentPrice, newLeaderName}`. Send it only when `previousHighestBidderId != null` and `!= bidderId`.
- **Broadcast is best-effort.** A send failure is logged at WARN and never rethrown, so Kafka never redelivers stale real-time messages.
- **Consumer groups:** each real-time listener uses `groupId = "media-realtime-${random.uuid}"` and `properties = "auto.offset.reset=latest"`. The existing `NotificationKafkaConsumer`, which uses the shared `media-service-group`, is unchanged.
- **Security:**
  - media-service `permitAll` covers `/ws-notifications/**`.
  - The gateway always strips client `X-User-Id` / `X-User-Roles` on `/ws-notifications/**`.
  - A missing token means anonymous. A present but invalid token returns 401. A valid token injects the identity headers.
  - The gateway never forwards the `access_token` query parameter.
- **Do not run `git commit` or `git add`.** Leave all changes uncommitted.
- **Maven** (run from `backend/`):
  - Unit: `mvn -q -pl <module> -am test -Dtest=<Class> -Dsurefire.failIfNoSpecifiedTests=false`.
  - Full: `mvn -q -pl <module> -am test`.
  - Never run `mvn install`.

---

## File Map

Paths are relative to `backend/`.

| File | Action | Responsibility |
|---|---|---|
| `media-service/src/main/java/com/bidnow/media/realtime/AuctionRealtimeMessage.java` | Create | Envelope and type constants |
| `media-service/src/main/java/com/bidnow/media/realtime/RealtimePayloads.java` | Create | Payload records |
| `media-service/src/main/java/com/bidnow/media/realtime/AuctionRealtimeBroadcaster.java` | Create | Event → STOMP messages |
| `media-service/src/test/java/com/bidnow/media/realtime/AuctionRealtimeBroadcasterTest.java` | Create | Mapping and outbid rules |
| `media-service/src/main/java/com/bidnow/media/kafka/AuctionRealtimeConsumer.java` | Create | Four per-instance listeners |
| `media-service/src/test/java/com/bidnow/media/kafka/AuctionRealtimeConsumerTest.java` | Create | Delegation and group config |
| `media-service/src/main/java/com/bidnow/media/config/GatewayUserHandshakeHandler.java` | Create | `X-User-Id` → `Principal` |
| `media-service/src/test/java/com/bidnow/media/config/GatewayUserHandshakeHandlerTest.java` | Create | Handler test |
| `media-service/src/main/java/com/bidnow/media/config/WebSocketConfig.java` | Modify | Register the handshake handler |
| `media-service/src/main/java/com/bidnow/media/config/SecurityConfig.java` | Modify | `permitAll /ws-notifications/**` |
| `api-gateway/src/main/resources/application.yml` | Modify | WS route; move the misplaced `default-filters` |
| `api-gateway/src/main/java/com/bidnow/gateway/filter/AuthenticationFilter.java` | Modify | Optional auth for `/ws-notifications/**` |
| `api-gateway/src/test/java/com/bidnow/gateway/filter/AuthenticationFilterTest.java` | Modify | WS auth tests |
| repo root: `docs/architecture.md`, roadmap | Modify | Docs and smoke-test steps |

media-service has no `src/test` directory yet. Create it. `spring-boot-starter-test` is inherited from the parent pom.

---

### Task 1: Message model + `AuctionRealtimeBroadcaster`

**Files:**
- Create: `media-service/src/main/java/com/bidnow/media/realtime/AuctionRealtimeMessage.java`
- Create: `media-service/src/main/java/com/bidnow/media/realtime/RealtimePayloads.java`
- Create: `media-service/src/main/java/com/bidnow/media/realtime/AuctionRealtimeBroadcaster.java`
- Test: `media-service/src/test/java/com/bidnow/media/realtime/AuctionRealtimeBroadcasterTest.java`

**Interfaces:**
- Consumes: the common events `BidPlacedEvent` (incl. `bidId`, `totalBids`, `endTime`), `AuctionExtendedEvent`, `AuctionEndedEvent` and `AuctionCancelledEvent`, plus the Spring `SimpMessagingTemplate` bean.
- Produces:
  - `record AuctionRealtimeMessage(String type, UUID auctionId, Object payload)`, with constants `BID_PLACED`, `AUCTION_EXTENDED`, `AUCTION_ENDED`, `AUCTION_CANCELLED` and `OUTBID`.
  - `RealtimePayloads.{BidPlaced, AuctionExtended, AuctionEnded, AuctionCancelled, Outbid}` records.
  - `AuctionRealtimeBroadcaster.bidPlaced/auctionExtended/auctionEnded/auctionCancelled(event)`, plus `static String auctionTopic(UUID)` and `static final String USER_QUEUE = "/queue/notifications"`.

- [ ] **Step 1: Write the failing test**

`media-service/src/test/java/com/bidnow/media/realtime/AuctionRealtimeBroadcasterTest.java`:

```java
package com.bidnow.media.realtime;

import com.bidnow.common.dto.event.AuctionCancelledEvent;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import com.bidnow.common.dto.event.AuctionExtendedEvent;
import com.bidnow.common.dto.event.BidPlacedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuctionRealtimeBroadcasterTest {

    private static final UUID AUCTION_ID = UUID.fromString("b0000000-0000-0000-0000-000000000005");
    private static final UUID BIDDER = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID PREVIOUS = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final String TOPIC = "/topic/auctions/b0000000-0000-0000-0000-000000000005";

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private AuctionRealtimeBroadcaster broadcaster;

    private static BidPlacedEvent bidPlaced(UUID previousHighestBidderId) {
        return BidPlacedEvent.builder()
                .bidId(UUID.randomUUID()).auctionId(AUCTION_ID).auctionTitle("Vintage Watch")
                .bidderId(BIDDER).bidderName("Bob").bidAmount(new BigDecimal("105.00"))
                .bidTime(LocalDateTime.of(2026, 10, 1, 12, 0))
                .previousHighestBidderId(previousHighestBidderId).isAntiSnipingTriggered(true)
                .totalBids(2).endTime(OffsetDateTime.parse("2026-10-01T12:05:00Z"))
                .build();
    }

    private AuctionRealtimeMessage capturedTopicMessage() {
        ArgumentCaptor<Object> message = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq(TOPIC), message.capture());
        return (AuctionRealtimeMessage) message.getValue();
    }

    @Test
    void auctionTopic_hasContractFormat() {
        assertThat(AuctionRealtimeBroadcaster.auctionTopic(AUCTION_ID)).isEqualTo(TOPIC);
    }

    @Test
    void bidPlaced_broadcastsToAuctionTopic() {
        BidPlacedEvent event = bidPlaced(null);

        broadcaster.bidPlaced(event);

        AuctionRealtimeMessage message = capturedTopicMessage();
        assertThat(message.type()).isEqualTo(AuctionRealtimeMessage.BID_PLACED);
        assertThat(message.auctionId()).isEqualTo(AUCTION_ID);
        RealtimePayloads.BidPlaced payload = (RealtimePayloads.BidPlaced) message.payload();
        assertThat(payload.bidId()).isEqualTo(event.getBidId());
        assertThat(payload.bidderId()).isEqualTo(BIDDER);
        assertThat(payload.bidderName()).isEqualTo("Bob");
        assertThat(payload.amount()).isEqualByComparingTo("105.00");
        assertThat(payload.placedAt()).isEqualTo(OffsetDateTime.of(2026, 10, 1, 12, 0, 0, 0, ZoneOffset.UTC));
        assertThat(payload.totalBids()).isEqualTo(2);
        assertThat(payload.endTime()).isEqualTo(OffsetDateTime.parse("2026-10-01T12:05:00Z"));
        assertThat(payload.antiSnipingTriggered()).isTrue();
    }

    @Test
    void bidPlaced_notifiesOutbidPreviousLeader() {
        broadcaster.bidPlaced(bidPlaced(PREVIOUS));

        ArgumentCaptor<Object> message = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSendToUser(eq(PREVIOUS.toString()),
                eq(AuctionRealtimeBroadcaster.USER_QUEUE), message.capture());
        AuctionRealtimeMessage outbid = (AuctionRealtimeMessage) message.getValue();
        assertThat(outbid.type()).isEqualTo(AuctionRealtimeMessage.OUTBID);
        assertThat(outbid.auctionId()).isEqualTo(AUCTION_ID);
        RealtimePayloads.Outbid payload = (RealtimePayloads.Outbid) outbid.payload();
        assertThat(payload.auctionTitle()).isEqualTo("Vintage Watch");
        assertThat(payload.currentPrice()).isEqualByComparingTo("105.00");
        assertThat(payload.newLeaderName()).isEqualTo("Bob");
    }

    @Test
    void bidPlaced_noPreviousLeader_sendsNoOutbid() {
        broadcaster.bidPlaced(bidPlaced(null));

        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void bidPlaced_leaderRaisingOwnBid_sendsNoOutbid() {
        broadcaster.bidPlaced(bidPlaced(BIDDER));

        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void auctionExtended_broadcastsNewEndTime() {
        broadcaster.auctionExtended(AuctionExtendedEvent.builder().auctionId(AUCTION_ID)
                .previousEndTime(Instant.parse("2026-10-01T12:00:00Z"))
                .newEndTime(Instant.parse("2026-10-01T12:05:00Z")).extensionCount(1).build());

        AuctionRealtimeMessage message = capturedTopicMessage();
        assertThat(message.type()).isEqualTo(AuctionRealtimeMessage.AUCTION_EXTENDED);
        RealtimePayloads.AuctionExtended payload = (RealtimePayloads.AuctionExtended) message.payload();
        assertThat(payload.newEndTime()).isEqualTo(Instant.parse("2026-10-01T12:05:00Z"));
        assertThat(payload.previousEndTime()).isEqualTo(Instant.parse("2026-10-01T12:00:00Z"));
        assertThat(payload.extensionCount()).isEqualTo(1);
    }

    @Test
    void auctionEnded_broadcastsWinnerAndFinalPrice() {
        broadcaster.auctionEnded(AuctionEndedEvent.builder().auctionId(AUCTION_ID).winnerId(BIDDER)
                .winningBidAmount(new BigDecimal("300.00")).endedAt(Instant.parse("2026-10-01T13:00:00Z")).build());

        AuctionRealtimeMessage message = capturedTopicMessage();
        assertThat(message.type()).isEqualTo(AuctionRealtimeMessage.AUCTION_ENDED);
        RealtimePayloads.AuctionEnded payload = (RealtimePayloads.AuctionEnded) message.payload();
        assertThat(payload.winnerId()).isEqualTo(BIDDER);
        assertThat(payload.finalPrice()).isEqualByComparingTo("300.00");
    }

    @Test
    void auctionEnded_withoutWinner_isStillBroadcast() {
        broadcaster.auctionEnded(AuctionEndedEvent.builder().auctionId(AUCTION_ID).build());

        RealtimePayloads.AuctionEnded payload = (RealtimePayloads.AuctionEnded) capturedTopicMessage().payload();
        assertThat(payload.winnerId()).isNull();
        assertThat(payload.finalPrice()).isNull();
    }

    @Test
    void auctionCancelled_broadcastsReason() {
        broadcaster.auctionCancelled(AuctionCancelledEvent.builder().auctionId(AUCTION_ID).reason("Fraud").build());

        AuctionRealtimeMessage message = capturedTopicMessage();
        assertThat(message.type()).isEqualTo(AuctionRealtimeMessage.AUCTION_CANCELLED);
        assertThat(((RealtimePayloads.AuctionCancelled) message.payload()).reason()).isEqualTo("Fraud");
    }

    @Test
    void sendFailure_isSwallowed() {
        doThrow(new MessageDeliveryException("broker down")).when(messagingTemplate).convertAndSend(eq(TOPIC), any(Object.class));

        assertThatCode(() -> broadcaster.auctionCancelled(
                AuctionCancelledEvent.builder().auctionId(AUCTION_ID).reason("x").build()))
                .doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `mvn -q -pl media-service -am test -Dtest=AuctionRealtimeBroadcasterTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement**

`media-service/src/main/java/com/bidnow/media/realtime/AuctionRealtimeMessage.java`:

```java
package com.bidnow.media.realtime;

import java.util.UUID;

/** STOMP envelope pushed to browsers: {@code {type, auctionId, payload}}. */
public record AuctionRealtimeMessage(String type, UUID auctionId, Object payload) {
    public static final String BID_PLACED = "BID_PLACED";
    public static final String AUCTION_EXTENDED = "AUCTION_EXTENDED";
    public static final String AUCTION_ENDED = "AUCTION_ENDED";
    public static final String AUCTION_CANCELLED = "AUCTION_CANCELLED";
    public static final String OUTBID = "OUTBID";
}
```

`media-service/src/main/java/com/bidnow/media/realtime/RealtimePayloads.java`:

```java
package com.bidnow.media.realtime;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Payload shapes of {@link AuctionRealtimeMessage} — the browser-facing contract (spec §6). */
public final class RealtimePayloads {

    private RealtimePayloads() {
    }

    public record BidPlaced(UUID bidId, UUID bidderId, String bidderName, BigDecimal amount, OffsetDateTime placedAt,
                            Integer totalBids, OffsetDateTime endTime, boolean antiSnipingTriggered) {
    }

    public record AuctionExtended(Instant previousEndTime, Instant newEndTime, Integer extensionCount) {
    }

    public record AuctionEnded(UUID winnerId, BigDecimal finalPrice, Instant endedAt) {
    }

    public record AuctionCancelled(String reason) {
    }

    public record Outbid(String auctionTitle, BigDecimal currentPrice, String newLeaderName) {
    }
}
```

`media-service/src/main/java/com/bidnow/media/realtime/AuctionRealtimeBroadcaster.java`:

```java
package com.bidnow.media.realtime;

import com.bidnow.common.dto.event.AuctionCancelledEvent;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import com.bidnow.common.dto.event.AuctionExtendedEvent;
import com.bidnow.common.dto.event.BidPlacedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Pushes auction events to STOMP subscribers. Best-effort: a failed send is logged and dropped —
 * a stale real-time update is worse than a missed one (clients resync from REST on reload).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionRealtimeBroadcaster {

    static final String USER_QUEUE = "/queue/notifications";

    private final SimpMessagingTemplate messagingTemplate;

    static String auctionTopic(UUID auctionId) {
        return "/topic/auctions/" + auctionId;
    }

    public void bidPlaced(BidPlacedEvent event) {
        sendToAuction(event.getAuctionId(), AuctionRealtimeMessage.BID_PLACED, new RealtimePayloads.BidPlaced(
                event.getBidId(),
                event.getBidderId(),
                event.getBidderName(),
                event.getBidAmount(),
                event.getBidTime() == null ? null : event.getBidTime().atOffset(ZoneOffset.UTC),
                event.getTotalBids(),
                event.getEndTime(),
                event.isAntiSnipingTriggered()));

        UUID previous = event.getPreviousHighestBidderId();
        if (previous != null && !previous.equals(event.getBidderId())) {
            sendToUser(previous, new AuctionRealtimeMessage(AuctionRealtimeMessage.OUTBID, event.getAuctionId(),
                    new RealtimePayloads.Outbid(event.getAuctionTitle(), event.getBidAmount(), event.getBidderName())));
        }
    }

    public void auctionExtended(AuctionExtendedEvent event) {
        sendToAuction(event.getAuctionId(), AuctionRealtimeMessage.AUCTION_EXTENDED, new RealtimePayloads.AuctionExtended(
                event.getPreviousEndTime(), event.getNewEndTime(), event.getExtensionCount()));
    }

    public void auctionEnded(AuctionEndedEvent event) {
        sendToAuction(event.getAuctionId(), AuctionRealtimeMessage.AUCTION_ENDED, new RealtimePayloads.AuctionEnded(
                event.getWinnerId(), event.getWinningBidAmount(), event.getEndedAt()));
    }

    public void auctionCancelled(AuctionCancelledEvent event) {
        sendToAuction(event.getAuctionId(), AuctionRealtimeMessage.AUCTION_CANCELLED,
                new RealtimePayloads.AuctionCancelled(event.getReason()));
    }

    private void sendToAuction(UUID auctionId, String type, Object payload) {
        try {
            messagingTemplate.convertAndSend(auctionTopic(auctionId), new AuctionRealtimeMessage(type, auctionId, payload));
        } catch (RuntimeException ex) {
            log.warn("Real-time {} broadcast failed for auction {}: {}", type, auctionId, ex.getMessage());
        }
    }

    private void sendToUser(UUID userId, AuctionRealtimeMessage message) {
        try {
            messagingTemplate.convertAndSendToUser(userId.toString(), USER_QUEUE, message);
        } catch (RuntimeException ex) {
            log.warn("Real-time {} notification failed for user {}: {}", message.type(), userId, ex.getMessage());
        }
    }
}
```

- [ ] **Step 4: Run it and confirm it passes**

Run: `mvn -q -pl media-service -am test -Dtest=AuctionRealtimeBroadcasterTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (10 tests).

---

### Task 2: `AuctionRealtimeConsumer` — per-instance listeners

**Files:**
- Create: `media-service/src/main/java/com/bidnow/media/kafka/AuctionRealtimeConsumer.java`
- Test: `media-service/src/test/java/com/bidnow/media/kafka/AuctionRealtimeConsumerTest.java`

**Interfaces:**
- Consumes: `AuctionRealtimeBroadcaster` (Task 1).
- Produces: listeners on the four topics, each with `groupId = "media-realtime-${random.uuid}"` and `properties = {"auto.offset.reset=latest"}`.

- [ ] **Step 1: Write the failing test**

`media-service/src/test/java/com/bidnow/media/kafka/AuctionRealtimeConsumerTest.java`:

```java
package com.bidnow.media.kafka;

import com.bidnow.common.dto.event.AuctionCancelledEvent;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import com.bidnow.common.dto.event.AuctionExtendedEvent;
import com.bidnow.common.dto.event.BidPlacedEvent;
import com.bidnow.media.realtime.AuctionRealtimeBroadcaster;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.annotation.KafkaListener;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuctionRealtimeConsumerTest {

    @Mock
    private AuctionRealtimeBroadcaster broadcaster;

    @InjectMocks
    private AuctionRealtimeConsumer consumer;

    @Test
    void delegatesEachEventToTheBroadcaster() {
        BidPlacedEvent bid = BidPlacedEvent.builder().auctionId(UUID.randomUUID()).build();
        AuctionExtendedEvent extended = AuctionExtendedEvent.builder().auctionId(UUID.randomUUID()).build();
        AuctionEndedEvent ended = AuctionEndedEvent.builder().auctionId(UUID.randomUUID()).build();
        AuctionCancelledEvent cancelled = AuctionCancelledEvent.builder().auctionId(UUID.randomUUID()).build();

        consumer.onBidPlaced(bid);
        consumer.onAuctionExtended(extended);
        consumer.onAuctionEnded(ended);
        consumer.onAuctionCancelled(cancelled);

        verify(broadcaster).bidPlaced(bid);
        verify(broadcaster).auctionExtended(extended);
        verify(broadcaster).auctionEnded(ended);
        verify(broadcaster).auctionCancelled(cancelled);
    }

    @Test
    void everyListenerUsesPerInstanceGroupReadingOnlyNewEvents() {
        Map<String, KafkaListener> listeners = Arrays.stream(AuctionRealtimeConsumer.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(KafkaListener.class))
                .collect(Collectors.toMap(Method::getName, m -> m.getAnnotation(KafkaListener.class)));

        assertThat(listeners).containsOnlyKeys("onBidPlaced", "onAuctionExtended", "onAuctionEnded", "onAuctionCancelled");
        assertThat(listeners.get("onBidPlaced").topics()).containsExactly("bid-placed-topic");
        assertThat(listeners.get("onAuctionExtended").topics()).containsExactly("auction-extended-topic");
        assertThat(listeners.get("onAuctionEnded").topics()).containsExactly("auction-ended-topic");
        assertThat(listeners.get("onAuctionCancelled").topics()).containsExactly("auction-cancelled-topic");
        listeners.values().forEach(listener -> {
            assertThat(listener.groupId()).isEqualTo("media-realtime-${random.uuid}");
            assertThat(listener.properties()).containsExactly("auto.offset.reset=latest");
        });
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `mvn -q -pl media-service -am test -Dtest=AuctionRealtimeConsumerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement**

`media-service/src/main/java/com/bidnow/media/kafka/AuctionRealtimeConsumer.java`:

```java
package com.bidnow.media.kafka;

import com.bidnow.common.dto.event.AuctionCancelledEvent;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import com.bidnow.common.dto.event.AuctionExtendedEvent;
import com.bidnow.common.dto.event.BidPlacedEvent;
import com.bidnow.media.realtime.AuctionRealtimeBroadcaster;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Feeds real-time STOMP pushes. Every media instance must see every event (each forwards to its own
 * connected clients), so each listener uses a per-instance consumer group, and reads only new events
 * - replaying history to live browsers would be wrong. Email/notification processing stays in
 * {@link NotificationKafkaConsumer} with the shared group.
 */
@Component
@RequiredArgsConstructor
public class AuctionRealtimeConsumer {

    private static final String PER_INSTANCE_GROUP = "media-realtime-${random.uuid}";
    private static final String NEW_EVENTS_ONLY = "auto.offset.reset=latest";

    private final AuctionRealtimeBroadcaster broadcaster;

    @KafkaListener(topics = "bid-placed-topic", groupId = PER_INSTANCE_GROUP, properties = NEW_EVENTS_ONLY)
    public void onBidPlaced(BidPlacedEvent event) {
        broadcaster.bidPlaced(event);
    }

    @KafkaListener(topics = "auction-extended-topic", groupId = PER_INSTANCE_GROUP, properties = NEW_EVENTS_ONLY)
    public void onAuctionExtended(AuctionExtendedEvent event) {
        broadcaster.auctionExtended(event);
    }

    @KafkaListener(topics = "auction-ended-topic", groupId = PER_INSTANCE_GROUP, properties = NEW_EVENTS_ONLY)
    public void onAuctionEnded(AuctionEndedEvent event) {
        broadcaster.auctionEnded(event);
    }

    @KafkaListener(topics = "auction-cancelled-topic", groupId = PER_INSTANCE_GROUP, properties = NEW_EVENTS_ONLY)
    public void onAuctionCancelled(AuctionCancelledEvent event) {
        broadcaster.auctionCancelled(event);
    }
}
```

- [ ] **Step 4: Run it and confirm it passes**

Run: `mvn -q -pl media-service -am test -Dtest=AuctionRealtimeConsumerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (2 tests).

---

### Task 3: STOMP user identity + anonymous access in media-service

**Files:**
- Create: `media-service/src/main/java/com/bidnow/media/config/GatewayUserHandshakeHandler.java`
- Test: `media-service/src/test/java/com/bidnow/media/config/GatewayUserHandshakeHandlerTest.java`
- Modify: `media-service/src/main/java/com/bidnow/media/config/WebSocketConfig.java`
- Modify: `media-service/src/main/java/com/bidnow/media/config/SecurityConfig.java`

**Interfaces:**
- Produces: `GatewayUserHandshakeHandler extends DefaultHandshakeHandler`. When a non-blank `X-User-Id` is present, the principal's name is that user ID. Otherwise the principal is `null`, meaning an anonymous session that can still subscribe to `/topic/**`.

- [ ] **Step 1: Write the failing test**

`media-service/src/test/java/com/bidnow/media/config/GatewayUserHandshakeHandlerTest.java`:

```java
package com.bidnow.media.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import java.security.Principal;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayUserHandshakeHandlerTest {

    private final GatewayUserHandshakeHandler handler = new GatewayUserHandshakeHandler();

    private static ServletServerHttpRequest request(String userId) {
        MockHttpServletRequest servlet = new MockHttpServletRequest("GET", "/ws-notifications/websocket");
        if (userId != null) {
            servlet.addHeader("X-User-Id", userId);
        }
        return new ServletServerHttpRequest(servlet);
    }

    @Test
    void gatewayUserHeader_becomesPrincipal() {
        Principal principal = handler.determineUser(request("550e8400-e29b-41d4-a716-446655440010"), null, new HashMap<>());

        assertThat(principal).isNotNull();
        assertThat(principal.getName()).isEqualTo("550e8400-e29b-41d4-a716-446655440010");
    }

    @Test
    void noHeader_isAnonymous() {
        assertThat(handler.determineUser(request(null), null, new HashMap<>())).isNull();
    }

    @Test
    void blankHeader_isAnonymous() {
        assertThat(handler.determineUser(request("  "), null, new HashMap<>())).isNull();
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `mvn -q -pl media-service -am test -Dtest=GatewayUserHandshakeHandlerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement**

`media-service/src/main/java/com/bidnow/media/config/GatewayUserHandshakeHandler.java`:

```java
package com.bidnow.media.config;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * Names the STOMP session after the gateway-verified user so {@code convertAndSendToUser(userId, …)}
 * reaches them. The gateway strips client-supplied X-User-Id and only injects it from a valid JWT.
 * No header → anonymous session (may still subscribe to public /topic destinations).
 */
public class GatewayUserHandshakeHandler extends DefaultHandshakeHandler {

    static final String X_USER_ID = "X-User-Id";

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        String userId = request.getHeaders().getFirst(X_USER_ID);
        if (userId == null || userId.isBlank()) {
            return null;
        }
        return () -> userId;
    }
}
```

In `WebSocketConfig.registerStompEndpoints`, change the registration to:

```java
        registry.addEndpoint("/ws-notifications")
                .setHandshakeHandler(new GatewayUserHandshakeHandler())
                .setAllowedOriginPatterns("*")
                .withSockJS();
```

In `SecurityConfig` (media-service), insert this rule after the `PUBLIC_ENDPOINTS` rule and before `.anyRequest()`:

```java
                        .requestMatchers("/ws-notifications/**")
                        .permitAll()
```

- [ ] **Step 4: Run the tests and the media-service suite**

Run: `mvn -q -pl media-service -am test`
Expected: BUILD SUCCESS (the 15 new media tests, plus any other tests).

---

### Task 4: Gateway — WebSocket route, header dedupe, optional auth

**Files:**
- Modify: `api-gateway/src/main/resources/application.yml`
- Modify: `api-gateway/src/main/java/com/bidnow/gateway/filter/AuthenticationFilter.java`
- Modify: `api-gateway/src/test/java/com/bidnow/gateway/filter/AuthenticationFilterTest.java` (created by Story 5)

**Interfaces:**
- Produces:
  - The route `media-websocket`: `Path=/ws-notifications/**` → `lb://media-service`. Spring Cloud Gateway switches to `ws` when the request has `Upgrade: websocket`.
  - Optional-auth handling in `AuthenticationFilter` for `/ws-notifications/**`.

- [ ] **Step 1: Write the failing tests**

In `AuthenticationFilterTest`, which Story 5 created:
- Replace the field `private final AuthenticationFilter filter = new AuthenticationFilter(mock(JwtUtil.class));` with a mock you can stub:

  ```java
      private final JwtUtil jwtUtil = mock(JwtUtil.class);
      private final AuthenticationFilter filter = new AuthenticationFilter(jwtUtil);
  ```

- Add imports: `org.mockito.ArgumentCaptor`, `org.springframework.web.server.ServerWebExchange`, `org.springframework.http.HttpHeaders`.
- Add these tests:

```java
    private ServerWebExchange forwarded(GatewayFilterChain chain) {
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        return captor.getValue();
    }

    @Test
    void websocket_withValidQueryToken_injectsUserAndDropsToken() {
        when(jwtUtil.isTokenValid("good")).thenReturn(true);
        when(jwtUtil.extractUserId("good")).thenReturn("user-1");
        when(jwtUtil.extractRoles("good")).thenReturn("USER");
        GatewayFilterChain chain = passingChain();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws-notifications/info?t=123&access_token=good").build());

        filter.filter(exchange, chain).block();

        ServerWebExchange out = forwarded(chain);
        assertThat(out.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("user-1");
        assertThat(out.getRequest().getHeaders().getFirst("X-User-Roles")).isEqualTo("USER");
        assertThat(out.getRequest().getURI().getQuery()).isEqualTo("t=123");
    }

    @Test
    void websocket_withBearerHeader_injectsUser() {
        when(jwtUtil.isTokenValid("good")).thenReturn(true);
        when(jwtUtil.extractUserId("good")).thenReturn("user-1");
        GatewayFilterChain chain = passingChain();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/ws-notifications/info")
                .header(HttpHeaders.AUTHORIZATION, "Bearer good").build());

        filter.filter(exchange, chain).block();

        assertThat(forwarded(chain).getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("user-1");
    }

    @Test
    void websocket_anonymous_passesThroughAndStripsSpoofedIdentity() {
        GatewayFilterChain chain = passingChain();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/ws-notifications/info")
                .header("X-User-Id", "spoofed").header("X-User-Roles", "ADMIN").build());

        filter.filter(exchange, chain).block();

        ServerWebExchange out = forwarded(chain);
        assertThat(out.getRequest().getHeaders().containsKey("X-User-Id")).isFalse();
        assertThat(out.getRequest().getHeaders().containsKey("X-User-Roles")).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void websocket_invalidToken_is401() {
        when(jwtUtil.isTokenValid("bad")).thenReturn(false);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws-notifications/info?access_token=bad").build());

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `mvn -q -pl api-gateway -am test -Dtest=AuthenticationFilterTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: the four new tests FAIL. `/ws-notifications/**` is neither public nor tokenless-allowed today, so it returns 401. The Story 5 tests still pass.

- [ ] **Step 3: Implement optional auth**

In `AuthenticationFilter`:
- Add the import `org.springframework.web.util.UriComponentsBuilder` and `java.net.URI`.
- Add these constants below `PUBLIC_PATHS`:

```java
    /**
     * Paths where a JWT is optional: anonymous callers pass through (public auction topics), a valid
     * JWT identifies the user (private queues), an invalid one is rejected. Browsers cannot set
     * headers on WebSocket/SockJS, so the token may also arrive as the {@code access_token} query param.
     */
    private static final List<String> OPTIONAL_AUTH_PATHS = List.of(
            "/ws-notifications/**"
    );
    private static final String ACCESS_TOKEN_PARAM = "access_token";
```

- In `filter`, insert this directly after the internal-path block and before the public-path check:

```java
        if (isOptionalAuthPath(path)) {
            return filterOptionalAuth(exchange, chain, path);
        }
```

- Add these methods:

```java
    private Mono<Void> filterOptionalAuth(ServerWebExchange exchange, GatewayFilterChain chain, String path) {
        ServerHttpRequest request = exchange.getRequest();
        String token = resolveToken(request);
        if (token != null && !jwtUtil.isTokenValid(token)) {
            log.warn("Invalid or expired JWT for optional-auth path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        URI withoutToken = UriComponentsBuilder.fromUri(request.getURI())
                .replaceQueryParam(ACCESS_TOKEN_PARAM)
                .build(true)
                .toUri();
        String userId = token == null ? null : jwtUtil.extractUserId(token);
        String roles = token == null ? null : jwtUtil.extractRoles(token);
        ServerHttpRequest mutated = request.mutate()
                .uri(withoutToken)
                .headers(headers -> {
                    headers.remove(X_USER_ID_HEADER); // never trust client-supplied identity
                    headers.remove(X_USER_ROLES_HEADER);
                    if (userId != null) {
                        headers.add(X_USER_ID_HEADER, userId);
                        if (roles != null) {
                            headers.add(X_USER_ROLES_HEADER, roles);
                        }
                    }
                })
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private static String resolveToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }
        String queryToken = request.getQueryParams().getFirst(ACCESS_TOKEN_PARAM);
        return queryToken == null || queryToken.isBlank() ? null : queryToken;
    }

    private boolean isOptionalAuthPath(String path) {
        return OPTIONAL_AUTH_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }
```

- [ ] **Step 4: Route and response-header dedupe in `application.yml`**

1. Under `spring.cloud.gateway.routes`, add this route. Place it after the existing `media-service` route, at the same indentation as the other routes:

```yaml
        - id: media-websocket
          uri: lb://media-service
          predicates:
            - Path=/ws-notifications/**
```

2. Move the existing `default-filters:` block to the gateway level. It is currently indented under `globalcors:`, where Spring Cloud Gateway ignores it. Both the gateway and media's SockJS endpoint add `Access-Control-Allow-Origin`, and browsers reject duplicated values, so the dedupe must actually apply.
   - Cut the `default-filters:` key and its list from under `globalcors`.
   - Re-add it with the same content as a sibling of `routes:` and `globalcors:` (6-space indent under `gateway:`):

```yaml
      default-filters:
        - name: DedupeResponseHeader
          args:
            name: Access-Control-Allow-Origin Access-Control-Allow-Credentials
            strategy: RETAIN_FIRST
```

   - Check that exactly one `default-filters` key remains, directly under `spring.cloud.gateway`.

- [ ] **Step 5: Run the tests and the gateway suite**

Run: `mvn -q -pl api-gateway -am test`
Expected: BUILD SUCCESS.

---

### Task 5: Documentation + manual smoke test

**Files** (repo root): `docs/architecture.md`, `docs/superpowers/plans/2026-09-29-bidding-epic-roadmap.md`

- [ ] **Step 1: architecture.md**

Replace the body of the existing "### Real-time Communication" section with the text below. Keep the heading.

```markdown
Real-time auction updates use STOMP over WebSocket (SockJS fallback) served by media-service at `/ws-notifications`, reached through the gateway (`/ws-notifications/**` → `lb://media-service`, auto-upgraded to `ws`).

- **Public auction topic** `/topic/auctions/{auctionId}` — messages `{type, auctionId, payload}` with `type` ∈ `BID_PLACED` `{bidId, bidderId, bidderName, amount, placedAt, totalBids, endTime, antiSnipingTriggered}`, `AUCTION_EXTENDED` `{previousEndTime, newEndTime, extensionCount}`, `AUCTION_ENDED` `{winnerId, finalPrice, endedAt}`, `AUCTION_CANCELLED` `{reason}`. Anonymous viewers may subscribe.
- **Private queue** `/user/queue/notifications` — `OUTBID` `{auctionTitle, currentPrice, newLeaderName}` to the previous leader. Requires an identified session.
- **Auth:** the gateway treats `/ws-notifications/**` as optional-auth: it strips client `X-User-Id`/`X-User-Roles`, accepts a JWT via `Authorization: Bearer` or `?access_token=` (browsers cannot set WebSocket headers), injects `X-User-Id` when valid (401 when invalid), and never forwards `access_token`. media-service names the STOMP session after `X-User-Id` (`GatewayUserHandshakeHandler`).
- **Fan-out:** media-service consumes `bid-placed-topic`, `auction-extended-topic`, `auction-ended-topic`, `auction-cancelled-topic` with a per-instance consumer group (`media-realtime-<uuid>`, latest offsets) so every instance pushes to its own clients. Delivery is best-effort; clients resync via REST on reload.
```

- [ ] **Step 2: Roadmap**

In Story 6:
- Tick 6.1, 6.2, 6.3 and 6.4.
- Leave 6.5 (manual smoke) unticked, and append the smoke steps below under it:

```markdown
  Smoke steps (requires the full stack via docker-compose):
  1. Two browser tabs, each running `new SockJS('http://localhost:8080/ws-notifications?access_token=<jwt>')` with `@stomp/stompjs`, subscribing to `/topic/auctions/<id>` (one tab also to `/user/queue/notifications`).
  2. Place a bid via Swagger / `POST /api/v1/bids` as another user → both tabs receive `BID_PLACED`; the previous leader's tab receives `OUTBID`.
  3. Place a bid within the last 2 minutes → `AUCTION_EXTENDED` arrives. Admin cancel → `AUCTION_CANCELLED`.
```

Also add a "Risks carried forward" bullet: `Per-instance media consumer groups (media-realtime-<uuid>) accumulate one group per restart until Kafka's offsets.retention expires them — harmless, but visible in tooling. media-service's consumer uses a plain JsonDeserializer (pre-existing): a poison message on these topics would retry-loop; consider ErrorHandlingDeserializer.`

---

## Self-review notes

- **Coverage against roadmap Story 6:**

| Roadmap item | Task |
|---|---|
| 6.1 | Task 1 (incl. outbid rules) |
| 6.2 | Task 2 (delegation + per-instance group assertion) |
| 6.3 | Task 3 (handler + `permitAll`) |
| 6.4 | Task 4 (route + optional auth + `access_token`) |
| 6.5 | Documented manual smoke; left open |

  Spec §6 destinations and payloads match the Global Constraints.

- **Findings baked in:**
  - media-service required authentication on `/ws-notifications` (anonymous viewers were blocked), so Task 3 adds `permitAll`.
  - The gateway's `default-filters` were mis-indented under `globalcors` and ignored, so Task 4 moves them.
  - The gateway's public paths don't strip identity headers, which is why WS uses optional-auth instead of `PUBLIC_PATHS`.
- **Out of scope:**
  - The frontend STOMP client is Story 7.
  - Outbid email batching is #19.
  - Persisting notifications is #18, which the user deferred.
