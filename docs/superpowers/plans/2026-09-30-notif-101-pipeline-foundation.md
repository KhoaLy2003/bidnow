# NOTIF-101 — Notification Pipeline Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Any media-service handler can call `NotificationDispatcher.dispatch(intent)`. That persists exactly one in-app notification per `(userId, dedupKey)`, sends an optional EN/VI email linked to it, and pushes it live on `/user/queue/notifications` from whichever media instance holds the user's socket. media-service keeps its own projection of auctions and their bidders. Welcome emails are the first flow moved onto the dispatcher.

**Architecture:** The dispatcher runs in one DB transaction. It uses an idempotent `INSERT … ON CONFLICT DO NOTHING` (JDBC) plus an unread count. Email and push are side effects registered with `AfterCommit`, so they never fire for a rolled-back or duplicate row. The email goes through `EmailService` in its own `REQUIRES_NEW` transaction, because writes made inside `afterCommit` would otherwise never commit. The push is published to Kafka `user-notification-push-topic`. A per-instance consumer forwards it to STOMP, following the existing `AuctionRealtimeConsumer` pattern. The auction/bidder projection is fed by its own consumer group, `media-projection-group`. Recipient emails come from identity-service, and language + email opt-in from a new user-service batch endpoint.

**Tech Stack:** Java 17, Spring Boot 3.2.4, Spring Data JPA + `NamedParameterJdbcTemplate`, PostgreSQL + Liquibase, Spring Kafka, STOMP (`SimpMessagingTemplate`), OpenFeign, JUnit 5, Mockito, AssertJ, standalone MockMvc, Testcontainers (via `bdd-support`, for the `*PostgresIT` test only).

**Spec:** `docs/superpowers/plans/2026-09-30-notification-epic-roadmap.md`. Read the Decisions section (5, 6, 11, 12) and Story 1. This plan refines Story 1 in three places, and Task 10 records them in the roadmap:
- The projection is plain JDBC (`AuctionProjectionRepository` + `AuctionRef` record) instead of JPA entities.
- The projection consumer uses its own group, `media-projection-group`. Sharing `media-service-group` with `NotificationKafkaConsumer` on `bid-placed-topic` would split partitions between the two listeners, so each would see only part of the events.
- Language and opt-in come from a new `POST /api/v1/users/internal/notification-preferences`, because `/internal/profiles` is the create endpoint.

## Global Constraints

- media-service owns `media_*` tables only. There are no cross-DB queries. Other services' data comes from events or internal Feign endpoints.
- Internal endpoints live under `/api/v1/users/internal/**` (user-service `permitAll`) and are never added to gateway routes.
- New shared DTOs go in `common/dto` and are only ever added to, never changed destructively.
- Responses are `ResponseEntity<BaseResponse<T>>`. user-service batch lookups are capped at 100 IDs (`UserSummariesRequest`).
- Side effects (Kafka publish, email) run after commit. Email and push failures are logged and never propagate to the Kafka listener.
- The push topic is `user-notification-push-topic`, with STOMP user destination `/queue/notifications` (clients subscribe to `/user/queue/notifications`, which `StompInboundGuard` already allows).
- Emails are single-attempt. On failure the log row is `FAILED`, with no retry (roadmap Decision 11).
- Template names are `{BASE}_{EN|VI}`, e.g. `WELCOME_EMAIL_VI`. A missing or inactive VI template falls back to EN.
- Liquibase changeset author: `hiep.nguyen`.
- Maven runs from `backend/`. Default unit tests must not need Docker. The `*PostgresIT` test runs only when named explicitly.
- **Agents do not commit.** Each task ends with a review stop. The user commits with the suggested conventional-commit message.

## File Structure

**common**
- Create `common/src/main/java/com/bidnow/common/dto/UserNotificationPreferenceResponse.java`: the cross-service preference DTO.

**user-service**
- Modify `repository/UserPreferencesRepository.java`: add `findByUserIdIn`.
- Modify `service/UserProfileService.java` and `service/impl/UserProfileServiceImpl.java`: add `getNotificationPreferences`.
- Modify `controller/UserProfileController.java`: add `POST /internal/notification-preferences`.
- Tests: `service/impl/UserProfileServiceImplTest.java`, `controller/UserProfileControllerTest.java`.

**media-service** (`backend/media-service/src/main/java/com/bidnow/media/…` unless noted)
- Create `resources/db/changelog/migrations/06-notification-pipeline.sql`, and modify `db.changelog-master.xml`.
- Modify `pom.xml`: add `bdd-support` (test scope).
- Modify `domain/entity/Notification.java`: `dedupKey`, `deletedAt`.
- Modify `domain/enums/NotificationType.java` (new values) and `domain/enums/NotificationLanguage.java` (`fromCode`).
- Create `repository/NotificationInboxRepository.java`: JDBC idempotent insert + unread count.
- Create `repository/AuctionProjectionRepository.java`: JDBC upserts and reads for the projection.
- Create `projection/AuctionRef.java` (record) and `projection/AuctionProjectionService.java` (event → projection).
- Create `kafka/AuctionProjectionConsumer.java`, group `media-projection-group`.
- Create `notification/DedupKeys.java`, `notification/NotificationIntent.java`, `notification/Recipient.java`, `notification/RecipientDirectory.java`, `notification/TemplateResolver.java`, `notification/NotificationDispatcher.java`.
- Create `feign/UserServiceClient.java` and `feign/UserIdsRequest.java`.
- Modify `service/EmailService.java` and `service/impl/EmailServiceImpl.java`: add an overload with `notificationId`, `REQUIRES_NEW`.
- Create `dto/response/NotificationResponse.java`.
- Create `realtime/UserNotificationMessage.java`, `realtime/UserNotificationPush.java`, `realtime/UserNotificationPusher.java`.
- Create `kafka/UserNotificationPushPublisher.java` and `kafka/UserNotificationPushConsumer.java`.
- Create `util/AfterCommit.java` and `config/ClockConfig.java`.
- Modify `service/impl/NotificationServiceImpl.java`: welcome → dispatcher, OTP → `TemplateResolver`.
- Tests under `src/test/java/com/bidnow/media/`, listed per task.

---

### Task 1: user-service batch notification-preferences endpoint

**Files:**
- Create: `backend/common/src/main/java/com/bidnow/common/dto/UserNotificationPreferenceResponse.java`
- Modify: `backend/user-service/src/main/java/com/bidnow/user/repository/UserPreferencesRepository.java`
- Modify: `backend/user-service/src/main/java/com/bidnow/user/service/UserProfileService.java`
- Modify: `backend/user-service/src/main/java/com/bidnow/user/service/impl/UserProfileServiceImpl.java`
- Modify: `backend/user-service/src/main/java/com/bidnow/user/controller/UserProfileController.java`
- Test: `backend/user-service/src/test/java/com/bidnow/user/service/impl/UserProfileServiceImplTest.java`
- Test: `backend/user-service/src/test/java/com/bidnow/user/controller/UserProfileControllerTest.java`

**Interfaces:**
- Produces: `POST /api/v1/users/internal/notification-preferences`, body `{"userIds": [UUID…]}` (1–100), → `BaseResponse<List<UserNotificationPreferenceResponse>>`. Users without a `user_preferences` row are omitted.
- Produces: `com.bidnow.common.dto.UserNotificationPreferenceResponse { UUID userId; String language; Boolean emailNotifications; }` (Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`).

- [ ] **Step 1: Create the shared DTO**

```java
package com.bidnow.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A user's notification preferences, for cross-service consumption")
public class UserNotificationPreferenceResponse {

    @Schema(description = "User UUID")
    private UUID userId;

    @Schema(description = "Preferred language code, e.g. en or vi")
    private String language;

    @Schema(description = "Whether the user accepts non-transactional emails")
    private Boolean emailNotifications;
}
```

- [ ] **Step 2: Write the failing service test.** Append to `UserProfileServiceImplTest` (add imports `com.bidnow.common.dto.UserNotificationPreferenceResponse` and `com.bidnow.user.domain.entity.UserPreferences`):

```java
    // -------------------------------------------------------
    // getNotificationPreferences
    // -------------------------------------------------------

    @Test
    void getNotificationPreferences_returnsExistingPreferencesWithOneQuery() {
        UUID alice = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        when(userPreferencesRepository.findByUserIdIn(any())).thenReturn(List.of(
                UserPreferences.builder().userId(alice).language("vi").emailNotifications(false).build()));

        List<UserNotificationPreferenceResponse> result =
                userProfileService.getNotificationPreferences(List.of(alice, unknown, alice));

        assertThat(result).singleElement().satisfies(p -> {
            assertThat(p.getUserId()).isEqualTo(alice);
            assertThat(p.getLanguage()).isEqualTo("vi");
            assertThat(p.getEmailNotifications()).isFalse();
        });
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(userPreferencesRepository).findByUserIdIn(ids.capture());
        assertThat(ids.getValue()).containsExactlyInAnyOrder(alice, unknown);
    }
```

- [ ] **Step 3: Write the failing controller tests.** Append to `UserProfileControllerTest` (add import `com.bidnow.common.dto.UserNotificationPreferenceResponse`):

```java
    // -------------------------------------------------------
    // POST /api/v1/users/internal/notification-preferences
    // -------------------------------------------------------

    @Test
    void getNotificationPreferences_returns200WithList() throws Exception {
        UUID alice = UUID.randomUUID();
        when(userProfileService.getNotificationPreferences(anyList())).thenReturn(List.of(
                UserNotificationPreferenceResponse.builder()
                        .userId(alice).language("vi").emailNotifications(true).build()));

        mockMvc.perform(post("/api/v1/users/internal/notification-preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[\"" + alice + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value(alice.toString()))
                .andExpect(jsonPath("$.data[0].language").value("vi"))
                .andExpect(jsonPath("$.data[0].emailNotifications").value(true));
    }

    @Test
    void getNotificationPreferences_emptyIds_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/internal/notification-preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userProfileService);
    }
```

- [ ] **Step 4: Run the tests to verify they fail**

Run: `mvn -q -pl user-service -am test -Dtest='UserProfileServiceImplTest,UserProfileControllerTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation FAILURE, because `findByUserIdIn` / `getNotificationPreferences` are not defined.

- [ ] **Step 5: Implement.** In `UserPreferencesRepository` add (imports `java.util.Collection`, `java.util.List`):

```java
    List<UserPreferences> findByUserIdIn(Collection<UUID> userIds);
```

In `UserProfileService`, below `getUserSummaries` (import `com.bidnow.common.dto.UserNotificationPreferenceResponse`):

```java
    List<UserNotificationPreferenceResponse> getNotificationPreferences(List<UUID> userIds);
```

In `UserProfileServiceImpl`, below `getUserSummaries` (same import):

```java
    @Override
    @Transactional(readOnly = true)
    public List<UserNotificationPreferenceResponse> getNotificationPreferences(List<UUID> userIds) {
        return userPreferencesRepository.findByUserIdIn(new LinkedHashSet<>(userIds)).stream()
                .map(preferences -> UserNotificationPreferenceResponse.builder()
                        .userId(preferences.getUserId())
                        .language(preferences.getLanguage())
                        .emailNotifications(preferences.getEmailNotifications())
                        .build())
                .toList();
    }
```

In `UserProfileController`, directly below `getUserSummaries` (same import):

```java
    @Operation(summary = "Get notification preferences by user IDs (Internal)", hidden = true)
    @PostMapping("/internal/notification-preferences")
    public ResponseEntity<BaseResponse<List<UserNotificationPreferenceResponse>>> getNotificationPreferences(
            @Valid @RequestBody UserSummariesRequest request) {
        return ResponseEntity.ok(BaseResponse.success(userProfileService.getNotificationPreferences(request.getUserIds())));
    }
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `mvn -q -pl user-service -am test -Dtest='UserProfileServiceImplTest,UserProfileControllerTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS

- [ ] **Step 7: Stop for review.** Suggested commit: `feat(user): add internal batch notification-preferences endpoint (NOTIF-101)`

---

### Task 2: Schema, entity fields and JDBC repositories

**Files:**
- Create: `backend/media-service/src/main/resources/db/changelog/migrations/06-notification-pipeline.sql`
- Modify: `backend/media-service/src/main/resources/db/changelog/db.changelog-master.xml`
- Modify: `backend/media-service/pom.xml`
- Modify: `backend/media-service/src/main/java/com/bidnow/media/domain/entity/Notification.java`
- Create: `backend/media-service/src/main/java/com/bidnow/media/repository/NotificationInboxRepository.java`
- Create: `backend/media-service/src/main/java/com/bidnow/media/projection/AuctionRef.java`
- Create: `backend/media-service/src/main/java/com/bidnow/media/repository/AuctionProjectionRepository.java`
- Test: `backend/media-service/src/test/java/com/bidnow/media/repository/NotificationPersistencePostgresIT.java`

**Interfaces:**
- Produces: `Notification.getDedupKey()/setDedupKey(String)`, `getDeletedAt()/setDeletedAt(LocalDateTime)`.
- Produces: `NotificationInboxRepository.insertIfAbsent(Notification row): boolean` (true = inserted, false = duplicate `(userId, dedupKey)`) and `countUnread(UUID userId): long`.
- Produces: `AuctionProjectionRepository.upsertAuction(UUID auctionId, String title, UUID sellerId, OffsetDateTime endTime)` (a null argument keeps the stored value), `upsertParticipant(UUID auctionId, UUID userId, BigDecimal amount, LocalDateTime bidAt)` (only a newer-or-equal `bidAt` overwrites), `findParticipantIds(UUID auctionId): List<UUID>`, `findAuction(UUID auctionId): Optional<AuctionRef>`.
- Produces: `record AuctionRef(UUID auctionId, String title, UUID sellerId, OffsetDateTime endTime)` in `com.bidnow.media.projection`.

JDBC (not JPA/native queries) is used because an explicit SQL type per parameter makes null UUID/timestamp binding and `ON CONFLICT` deterministic. `NamedParameterJdbcTemplate` is auto-configured and joins the JPA transaction.

- [ ] **Step 1: Add the migration**

`06-notification-pipeline.sql`:

```sql
-- liquibase formatted sql

-- changeset hiep.nguyen:notification-pipeline-dedup-and-soft-delete
-- comment: Idempotent notification inserts keyed by (user_id, dedup_key) and soft delete for the user inbox
ALTER TABLE media_notifications ADD COLUMN dedup_key VARCHAR(200);
UPDATE media_notifications SET dedup_key = 'LEGACY:' || id::text WHERE dedup_key IS NULL;
ALTER TABLE media_notifications ALTER COLUMN dedup_key SET NOT NULL;
ALTER TABLE media_notifications ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE media_notifications
    ADD CONSTRAINT uq_media_notifications_user_dedup UNIQUE (user_id, dedup_key);
CREATE INDEX idx_media_notifications_user_feed
    ON media_notifications (user_id, deleted_at, created_at DESC);
CREATE INDEX idx_media_notifications_user_unread
    ON media_notifications (user_id) WHERE read_at IS NULL AND deleted_at IS NULL;

-- changeset hiep.nguyen:notification-pipeline-auction-projection
-- comment: media-service's own view of auctions and their bidders, fed by Kafka events
CREATE TABLE media_auctions
(
    auction_id UUID PRIMARY KEY,
    title      VARCHAR(255),
    seller_id  UUID,
    end_time   TIMESTAMPTZ,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE media_auction_participants
(
    auction_id      UUID           NOT NULL,
    user_id         UUID           NOT NULL,
    last_bid_amount NUMERIC(19, 2) NOT NULL,
    last_bid_at     TIMESTAMP      NOT NULL,
    PRIMARY KEY (auction_id, user_id)
);
```

In `db.changelog-master.xml`, after the `05-…` include:

```xml
    <include file="db/changelog/migrations/06-notification-pipeline.sql"/>
```

- [ ] **Step 2: Add the entity fields.** In `Notification.java`, after `retryCount`:

```java
    @Column(name = "dedup_key", nullable = false, length = 200)
    private String dedupKey;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
```

- [ ] **Step 3: Add the test dependency.** In `media-service/pom.xml` `<dependencies>`:

```xml
        <dependency>
            <groupId>com.bidnow</groupId>
            <artifactId>bdd-support</artifactId>
            <version>${project.version}</version>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 4: Write the failing Postgres integration test**

`NotificationPersistencePostgresIT.java`:

```java
package com.bidnow.media.repository;

import com.bidnow.bdd.container.PostgresContainerSupport;
import com.bidnow.media.domain.entity.Notification;
import com.bidnow.media.domain.enums.NotificationChannel;
import com.bidnow.media.domain.enums.NotificationStatus;
import com.bidnow.media.domain.enums.NotificationType;
import com.bidnow.media.projection.AuctionRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real Liquibase changelog against Postgres (Testcontainers, needs Docker).
 * Not part of the default test run - execute explicitly:
 * mvn -q -pl media-service -am test -Dtest=NotificationPersistencePostgresIT -Dsurefire.failIfNoSpecifiedTests=false
 */
class NotificationPersistencePostgresIT {

    private static NamedParameterJdbcTemplate jdbc;

    private NotificationInboxRepository inbox;
    private AuctionProjectionRepository projection;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID auctionId = UUID.randomUUID();

    @BeforeAll
    static void migrate() throws Exception {
        PostgreSQLContainer<?> pg = PostgresContainerSupport.POSTGRES;
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.xml");
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.afterPropertiesSet();
        jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    @BeforeEach
    void setUp() {
        jdbc.getJdbcTemplate().execute("TRUNCATE media_notifications, media_auctions, media_auction_participants");
        inbox = new NotificationInboxRepository(jdbc, new ObjectMapper());
        projection = new AuctionProjectionRepository(jdbc);
    }

    private Notification row(UUID userId, String dedupKey, UUID auction, Map<String, Object> metadata) {
        Notification n = Notification.builder()
                .id(UUID.randomUUID()).userId(userId).type(NotificationType.AUCTION_WON)
                .channel(NotificationChannel.IN_APP).status(NotificationStatus.SENT)
                .title("You won").message("You won Vintage Watch").actionUrl("/auctions/" + auction)
                .auctionId(auction).metadata(metadata).dedupKey(dedupKey)
                .build();
        n.setCreatedAt(LocalDateTime.of(2026, 10, 1, 10, 0));
        return n;
    }

    @Test
    void insertIfAbsent_insertsOncePerUserAndDedupKey() {
        assertThat(inbox.insertIfAbsent(row(alice, "AUCTION_WON:x", auctionId, null))).isTrue();
        assertThat(inbox.insertIfAbsent(row(alice, "AUCTION_WON:x", auctionId, null))).isFalse();
        assertThat(inbox.insertIfAbsent(row(bob, "AUCTION_WON:x", auctionId, null))).isTrue();

        Integer rows = jdbc.getJdbcTemplate().queryForObject("SELECT COUNT(*) FROM media_notifications", Integer.class);
        assertThat(rows).isEqualTo(2);
    }

    @Test
    void insertIfAbsent_storesColumnsIncludingJsonbMetadataAndNullAuction() {
        Notification n = row(alice, "WELCOME", null, Map.of("amount", "105.00"));

        inbox.insertIfAbsent(n);

        Map<String, Object> stored = jdbc.getJdbcTemplate().queryForMap(
                "SELECT type, channel, status, metadata->>'amount' AS amount, auction_id, dedup_key, created_at "
                        + "FROM media_notifications WHERE id = ?", n.getId());
        assertThat(stored.get("type")).isEqualTo("AUCTION_WON");
        assertThat(stored.get("channel")).isEqualTo("IN_APP");
        assertThat(stored.get("status")).isEqualTo("SENT");
        assertThat(stored.get("amount")).isEqualTo("105.00");
        assertThat(stored.get("auction_id")).isNull();
        assertThat(stored.get("dedup_key")).isEqualTo("WELCOME");
        assertThat(stored.get("created_at").toString()).startsWith("2026-10-01 10:00");
    }

    @Test
    void countUnread_ignoresReadAndDeletedRows() {
        Notification unread = row(alice, "A", auctionId, null);
        Notification read = row(alice, "B", auctionId, null);
        Notification deleted = row(alice, "C", auctionId, null);
        inbox.insertIfAbsent(unread);
        inbox.insertIfAbsent(read);
        inbox.insertIfAbsent(deleted);
        inbox.insertIfAbsent(row(bob, "A", auctionId, null));
        jdbc.getJdbcTemplate().update("UPDATE media_notifications SET read_at = now() WHERE id = ?", read.getId());
        jdbc.getJdbcTemplate().update("UPDATE media_notifications SET deleted_at = now() WHERE id = ?", deleted.getId());

        assertThat(inbox.countUnread(alice)).isEqualTo(1);
    }

    @Test
    void upsertAuction_nullArgumentsKeepStoredValues() {
        UUID seller = UUID.randomUUID();
        OffsetDateTime end = OffsetDateTime.parse("2026-10-01T12:00:00Z");
        OffsetDateTime extended = OffsetDateTime.parse("2026-10-01T12:05:00Z");

        projection.upsertAuction(auctionId, "Vintage Watch", seller, end);
        projection.upsertAuction(auctionId, null, null, extended);

        AuctionRef ref = projection.findAuction(auctionId).orElseThrow();
        assertThat(ref.title()).isEqualTo("Vintage Watch");
        assertThat(ref.sellerId()).isEqualTo(seller);
        assertThat(ref.endTime().toInstant()).isEqualTo(extended.toInstant());
    }

    @Test
    void upsertAuction_withoutSellerCreatesTheRow() {
        projection.upsertAuction(auctionId, "Vintage Watch", null, null);

        AuctionRef ref = projection.findAuction(auctionId).orElseThrow();
        assertThat(ref.sellerId()).isNull();
        assertThat(ref.endTime()).isNull();
    }

    @Test
    void findAuction_unknown_isEmpty() {
        assertThat(projection.findAuction(UUID.randomUUID())).isEmpty();
    }

    @Test
    void upsertParticipant_keepsOneRowPerBidderWithTheLatestBid() {
        LocalDateTime t1 = LocalDateTime.of(2026, 10, 1, 11, 0);
        LocalDateTime t2 = t1.plusMinutes(1);
        projection.upsertParticipant(auctionId, alice, new BigDecimal("100.00"), t1);
        projection.upsertParticipant(auctionId, alice, new BigDecimal("120.00"), t2);
        projection.upsertParticipant(auctionId, alice, new BigDecimal("110.00"), t1.plusSeconds(30)); // late redelivery
        projection.upsertParticipant(auctionId, bob, new BigDecimal("115.00"), t1.plusSeconds(45));

        assertThat(projection.findParticipantIds(auctionId)).containsExactlyInAnyOrder(alice, bob);
        BigDecimal aliceAmount = jdbc.getJdbcTemplate().queryForObject(
                "SELECT last_bid_amount FROM media_auction_participants WHERE auction_id = ? AND user_id = ?",
                BigDecimal.class, auctionId, alice);
        assertThat(aliceAmount).isEqualByComparingTo("120.00");
    }
}
```

- [ ] **Step 5: Run the test to verify it fails**

Run: `mvn -q -pl media-service -am test -Dtest=NotificationPersistencePostgresIT -Dsurefire.failIfNoSpecifiedTests=false` (Docker must be running)
Expected: compilation FAILURE, because `NotificationInboxRepository`, `AuctionProjectionRepository` and `AuctionRef` do not exist.

- [ ] **Step 6: Implement `AuctionRef`**

```java
package com.bidnow.media.projection;

import java.time.OffsetDateTime;
import java.util.UUID;

/** media-service's projected view of an auction; any field but the id may be unknown (null). */
public record AuctionRef(UUID auctionId, String title, UUID sellerId, OffsetDateTime endTime) {
}
```

- [ ] **Step 7: Implement `NotificationInboxRepository`**

```java
package com.bidnow.media.repository;

import com.bidnow.media.domain.entity.Notification;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.Map;
import java.util.UUID;

/**
 * Write side of the user inbox. The insert is idempotent on (user_id, dedup_key), so a redelivered Kafka
 * event never creates a second notification. Plain JDBC keeps null UUID/JSON binding and ON CONFLICT explicit.
 */
@Repository
@RequiredArgsConstructor
public class NotificationInboxRepository {

    private static final String INSERT_IF_ABSENT = """
            INSERT INTO media_notifications
                (id, user_id, type, channel, status, title, message, action_url, auction_id,
                 metadata, dedup_key, sent_at, retry_count, created_at, updated_at)
            VALUES
                (:id, :userId, :type, :channel, :status, :title, :message, :actionUrl, :auctionId,
                 CAST(:metadata AS jsonb), :dedupKey, :createdAt, 0, :createdAt, :createdAt)
            ON CONFLICT (user_id, dedup_key) DO NOTHING
            """;

    private static final String COUNT_UNREAD = """
            SELECT COUNT(*) FROM media_notifications
            WHERE user_id = :userId AND read_at IS NULL AND deleted_at IS NULL
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    /** @return true if the row was inserted, false if (userId, dedupKey) already existed */
    public boolean insertIfAbsent(Notification row) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", row.getId(), Types.OTHER)
                .addValue("userId", row.getUserId(), Types.OTHER)
                .addValue("type", row.getType().name(), Types.VARCHAR)
                .addValue("channel", row.getChannel().name(), Types.VARCHAR)
                .addValue("status", row.getStatus().name(), Types.VARCHAR)
                .addValue("title", row.getTitle(), Types.VARCHAR)
                .addValue("message", row.getMessage(), Types.VARCHAR)
                .addValue("actionUrl", row.getActionUrl(), Types.VARCHAR)
                .addValue("auctionId", row.getAuctionId(), Types.OTHER)
                .addValue("metadata", toJson(row.getMetadata()), Types.VARCHAR)
                .addValue("dedupKey", row.getDedupKey(), Types.VARCHAR)
                .addValue("createdAt", row.getCreatedAt(), Types.TIMESTAMP);
        return jdbc.update(INSERT_IF_ABSENT, params) == 1;
    }

    public long countUnread(UUID userId) {
        Long count = jdbc.queryForObject(COUNT_UNREAD,
                new MapSqlParameterSource().addValue("userId", userId, Types.OTHER), Long.class);
        return count == null ? 0 : count;
    }

    private String toJson(Map<String, Object> metadata) {
        if (metadata == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Notification metadata is not serialisable", ex);
        }
    }
}
```

- [ ] **Step 8: Implement `AuctionProjectionRepository`**

```java
package com.bidnow.media.repository;

import com.bidnow.media.projection.AuctionRef;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Upserts are order-tolerant: events may arrive in any order and may be redelivered. */
@Repository
@RequiredArgsConstructor
public class AuctionProjectionRepository {

    private static final String UPSERT_AUCTION = """
            INSERT INTO media_auctions (auction_id, title, seller_id, end_time, created_at, updated_at)
            VALUES (:auctionId, :title, :sellerId, :endTime, now(), now())
            ON CONFLICT (auction_id) DO UPDATE SET
                title      = COALESCE(EXCLUDED.title, media_auctions.title),
                seller_id  = COALESCE(EXCLUDED.seller_id, media_auctions.seller_id),
                end_time   = COALESCE(EXCLUDED.end_time, media_auctions.end_time),
                updated_at = now()
            """;

    private static final String UPSERT_PARTICIPANT = """
            INSERT INTO media_auction_participants (auction_id, user_id, last_bid_amount, last_bid_at)
            VALUES (:auctionId, :userId, :amount, :bidAt)
            ON CONFLICT (auction_id, user_id) DO UPDATE SET
                last_bid_amount = EXCLUDED.last_bid_amount,
                last_bid_at     = EXCLUDED.last_bid_at
            WHERE EXCLUDED.last_bid_at >= media_auction_participants.last_bid_at
            """;

    private static final String FIND_PARTICIPANT_IDS =
            "SELECT user_id FROM media_auction_participants WHERE auction_id = :auctionId";

    private static final String FIND_AUCTION =
            "SELECT auction_id, title, seller_id, end_time FROM media_auctions WHERE auction_id = :auctionId";

    private final NamedParameterJdbcTemplate jdbc;

    /** A null title/seller/endTime keeps the stored value. */
    public void upsertAuction(UUID auctionId, String title, UUID sellerId, OffsetDateTime endTime) {
        jdbc.update(UPSERT_AUCTION, new MapSqlParameterSource()
                .addValue("auctionId", auctionId, Types.OTHER)
                .addValue("title", title, Types.VARCHAR)
                .addValue("sellerId", sellerId, Types.OTHER)
                .addValue("endTime", endTime, Types.TIMESTAMP_WITH_TIMEZONE));
    }

    /** Only a bid at or after the stored one overwrites it, so a late redelivery cannot roll the row back. */
    public void upsertParticipant(UUID auctionId, UUID userId, BigDecimal amount, LocalDateTime bidAt) {
        jdbc.update(UPSERT_PARTICIPANT, new MapSqlParameterSource()
                .addValue("auctionId", auctionId, Types.OTHER)
                .addValue("userId", userId, Types.OTHER)
                .addValue("amount", amount, Types.NUMERIC)
                .addValue("bidAt", bidAt, Types.TIMESTAMP));
    }

    public List<UUID> findParticipantIds(UUID auctionId) {
        return jdbc.query(FIND_PARTICIPANT_IDS, auctionParam(auctionId),
                (rs, rowNum) -> rs.getObject("user_id", UUID.class));
    }

    public Optional<AuctionRef> findAuction(UUID auctionId) {
        return jdbc.query(FIND_AUCTION, auctionParam(auctionId), (rs, rowNum) -> new AuctionRef(
                        rs.getObject("auction_id", UUID.class),
                        rs.getString("title"),
                        rs.getObject("seller_id", UUID.class),
                        rs.getObject("end_time", OffsetDateTime.class)))
                .stream().findFirst();
    }

    private static MapSqlParameterSource auctionParam(UUID auctionId) {
        return new MapSqlParameterSource().addValue("auctionId", auctionId, Types.OTHER);
    }
}
```

- [ ] **Step 9: Run the integration test to verify it passes**

Run: `mvn -q -pl media-service -am test -Dtest=NotificationPersistencePostgresIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (7 tests). Liquibase applies 01–06 on a fresh Postgres 16.

- [ ] **Step 10: Run the default suite (no Docker needed)**

Run: `mvn -q -pl media-service -am test`
Expected: PASS. The IT is not picked up because its name does not match the surefire default includes.

- [ ] **Step 11: Stop for review.** Suggested commit: `feat(notification): add idempotent inbox and auction projection storage (NOTIF-101)`

---

### Task 3: Notification types, language parsing and dedup keys

**Files:**
- Modify: `backend/media-service/src/main/java/com/bidnow/media/domain/enums/NotificationType.java`
- Modify: `backend/media-service/src/main/java/com/bidnow/media/domain/enums/NotificationLanguage.java`
- Create: `backend/media-service/src/main/java/com/bidnow/media/notification/DedupKeys.java`
- Test: `backend/media-service/src/test/java/com/bidnow/media/notification/DedupKeysTest.java`
- Test: `backend/media-service/src/test/java/com/bidnow/media/domain/enums/NotificationLanguageTest.java`

**Interfaces:**
- Produces: `NotificationType` gains `FIRST_BID, NEW_BID, AUCTION_EXTENDED, AUCTION_CREATED, PAYMENT_REQUIRED, PAYMENT_FAILED` (appended, nothing removed).
- Produces: `NotificationLanguage.fromCode(String code): NotificationLanguage` ("vi" in any case/whitespace → VI, anything else → EN).
- Produces: `DedupKeys` static methods: `welcome()`, `auctionCreated(UUID)`, `won(UUID)`, `lost(UUID)`, `payment(String paymentType, UUID)`, `refund(UUID)`, `cancelled(UUID)`, `extended(UUID, Instant at)`, `firstBid(UUID)`, `batch(NotificationType, UUID, Instant windowStart)`, `endingSoon(UUID, int minutes)`. All return `String`.

- [ ] **Step 1: Write the failing tests**

`DedupKeysTest.java`:

```java
package com.bidnow.media.notification;

import com.bidnow.media.domain.enums.NotificationType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DedupKeysTest {

    private static final UUID A = UUID.fromString("a0000000-0000-0000-0000-000000000001");

    @Test
    void formatsAreFixed() {
        assertThat(DedupKeys.welcome()).isEqualTo("WELCOME");
        assertThat(DedupKeys.auctionCreated(A)).isEqualTo("AUCTION_CREATED:" + A);
        assertThat(DedupKeys.won(A)).isEqualTo("AUCTION_WON:" + A);
        assertThat(DedupKeys.lost(A)).isEqualTo("AUCTION_LOST:" + A);
        assertThat(DedupKeys.payment("REQUIRED", A)).isEqualTo("PAYMENT_REQUIRED:" + A);
        assertThat(DedupKeys.refund(A)).isEqualTo("DEPOSIT_REFUNDED:" + A);
        assertThat(DedupKeys.cancelled(A)).isEqualTo("AUCTION_CANCELLED:" + A);
        assertThat(DedupKeys.firstBid(A)).isEqualTo("FIRST_BID:" + A);
        assertThat(DedupKeys.endingSoon(A, 15)).isEqualTo("ENDING_SOON:" + A + ":15");
        assertThat(DedupKeys.batch(NotificationType.BID_OUTBID, A, Instant.ofEpochMilli(1_700_000_000_123L)))
                .isEqualTo("BID_OUTBID:" + A + ":1700000000123");
    }

    @Test
    void extended_sharesAKeyWithinAFiveMinuteBucket() {
        String first = DedupKeys.extended(A, Instant.parse("2026-10-01T12:00:00Z"));
        String sameBucket = DedupKeys.extended(A, Instant.parse("2026-10-01T12:04:59Z"));
        String nextBucket = DedupKeys.extended(A, Instant.parse("2026-10-01T12:05:00Z"));

        assertThat(first).startsWith("AUCTION_EXTENDED:" + A + ":");
        assertThat(sameBucket).isEqualTo(first);
        assertThat(nextBucket).isNotEqualTo(first);
    }

    @Test
    void keysFitTheColumn() {
        assertThat(DedupKeys.batch(NotificationType.BID_OUTBID, A, Instant.now()).length()).isLessThanOrEqualTo(200);
    }
}
```

`NotificationLanguageTest.java`:

```java
package com.bidnow.media.domain.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationLanguageTest {

    @Test
    void fromCode_mapsVietnameseAndDefaultsToEnglish() {
        assertThat(NotificationLanguage.fromCode("vi")).isEqualTo(NotificationLanguage.VI);
        assertThat(NotificationLanguage.fromCode(" VI ")).isEqualTo(NotificationLanguage.VI);
        assertThat(NotificationLanguage.fromCode("en")).isEqualTo(NotificationLanguage.EN);
        assertThat(NotificationLanguage.fromCode("fr")).isEqualTo(NotificationLanguage.EN);
        assertThat(NotificationLanguage.fromCode(null)).isEqualTo(NotificationLanguage.EN);
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q -pl media-service -am test -Dtest='DedupKeysTest,NotificationLanguageTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation FAILURE, because `DedupKeys` and `fromCode` do not exist.

- [ ] **Step 3: Implement.** Replace `NotificationType.java` with:

```java
package com.bidnow.media.domain.enums;

public enum NotificationType {
    USER_REGISTERED,
    OTP_VERIFICATION,
    BID_PLACED,
    BID_OUTBID,
    AUCTION_ENDING_SOON,
    AUCTION_WON,
    AUCTION_LOST,
    AUCTION_CANCELLED,
    PAYMENT_REMINDER,
    PAYMENT_RECEIVED,
    DEPOSIT_REFUNDED,
    DEPOSIT_FORFEITED,
    WATCHLIST_ITEM_STARTING,
    SYSTEM_ANNOUNCEMENT,
    FIRST_BID,
    NEW_BID,
    AUCTION_EXTENDED,
    AUCTION_CREATED,
    PAYMENT_REQUIRED,
    PAYMENT_FAILED
}
```

Replace `NotificationLanguage.java` with:

```java
package com.bidnow.media.domain.enums;

public enum NotificationLanguage {
    EN,
    VI;

    /** Maps a user-service language code ("en", "vi") to a template language; unknown codes fall back to EN. */
    public static NotificationLanguage fromCode(String code) {
        return code != null && code.trim().equalsIgnoreCase("vi") ? VI : EN;
    }
}
```

Create `DedupKeys.java`:

```java
package com.bidnow.media.notification;

import com.bidnow.media.domain.enums.NotificationType;

import java.time.Instant;
import java.util.UUID;

/**
 * Idempotency keys for {@code media_notifications (user_id, dedup_key)}. One key = one notification per user,
 * so a redelivered event is a no-op. Formats are part of the stored data - never change an existing one.
 */
public final class DedupKeys {

    private static final long EXTENSION_BUCKET_SECONDS = 300;

    private DedupKeys() {
    }

    public static String welcome() {
        return "WELCOME";
    }

    public static String auctionCreated(UUID auctionId) {
        return "AUCTION_CREATED:" + auctionId;
    }

    public static String won(UUID auctionId) {
        return "AUCTION_WON:" + auctionId;
    }

    public static String lost(UUID auctionId) {
        return "AUCTION_LOST:" + auctionId;
    }

    public static String payment(String paymentType, UUID auctionId) {
        return "PAYMENT_" + paymentType + ":" + auctionId;
    }

    public static String refund(UUID auctionId) {
        return "DEPOSIT_REFUNDED:" + auctionId;
    }

    public static String cancelled(UUID auctionId) {
        return "AUCTION_CANCELLED:" + auctionId;
    }

    /** Extensions within the same 5-minute bucket collapse into one notification per user. */
    public static String extended(UUID auctionId, Instant at) {
        return "AUCTION_EXTENDED:" + auctionId + ":" + Math.floorDiv(at.getEpochSecond(), EXTENSION_BUCKET_SECONDS);
    }

    public static String firstBid(UUID auctionId) {
        return "FIRST_BID:" + auctionId;
    }

    public static String batch(NotificationType type, UUID auctionId, Instant windowStart) {
        return type.name() + ":" + auctionId + ":" + windowStart.toEpochMilli();
    }

    public static String endingSoon(UUID auctionId, int thresholdMinutes) {
        return "ENDING_SOON:" + auctionId + ":" + thresholdMinutes;
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q -pl media-service -am test -Dtest='DedupKeysTest,NotificationLanguageTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS

- [ ] **Step 5: Stop for review.** Suggested commit: `feat(notification): add dedup keys and notification types (NOTIF-101)`

---

### Task 4: Recipient directory (email + language + opt-in)

**Files:**
- Create: `backend/media-service/src/main/java/com/bidnow/media/feign/UserIdsRequest.java`
- Create: `backend/media-service/src/main/java/com/bidnow/media/feign/UserServiceClient.java`
- Create: `backend/media-service/src/main/java/com/bidnow/media/notification/Recipient.java`
- Create: `backend/media-service/src/main/java/com/bidnow/media/notification/RecipientDirectory.java`
- Test: `backend/media-service/src/test/java/com/bidnow/media/notification/RecipientDirectoryTest.java`

**Interfaces:**
- Consumes: Task 1 endpoint; `IdentityServiceClient.getEmailsByUserIds(List<UUID>): BaseResponse<Map<UUID,String>>` (existing); `NotificationLanguage.fromCode` (Task 3).
- Produces: `record Recipient(UUID userId, String email, NotificationLanguage language, boolean emailOptIn)` with `static Recipient unknown(UUID userId)` (null email, EN, opt-in true).
- Produces: `RecipientDirectory.resolve(Collection<UUID> userIds): Map<UUID, Recipient>`. It has one entry per distinct non-null ID and never throws on downstream failure.

- [ ] **Step 1: Write the failing test**

```java
package com.bidnow.media.notification;

import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.dto.UserNotificationPreferenceResponse;
import com.bidnow.media.domain.enums.NotificationLanguage;
import com.bidnow.media.feign.IdentityServiceClient;
import com.bidnow.media.feign.UserIdsRequest;
import com.bidnow.media.feign.UserServiceClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipientDirectoryTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Mock
    private IdentityServiceClient identityServiceClient;
    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private RecipientDirectory directory;

    private static UserNotificationPreferenceResponse pref(UUID userId, String language, Boolean email) {
        return UserNotificationPreferenceResponse.builder()
                .userId(userId).language(language).emailNotifications(email).build();
    }

    @Test
    void resolve_combinesEmailsAndPreferences() {
        when(identityServiceClient.getEmailsByUserIds(anyList()))
                .thenReturn(BaseResponse.success(Map.of(ALICE, "alice@example.com", BOB, "bob@example.com")));
        when(userServiceClient.getNotificationPreferences(any()))
                .thenReturn(BaseResponse.success(List.of(pref(ALICE, "vi", false))));

        Map<UUID, Recipient> result = directory.resolve(List.of(ALICE, BOB, ALICE));

        assertThat(result).containsOnlyKeys(ALICE, BOB);
        assertThat(result.get(ALICE)).isEqualTo(new Recipient(ALICE, "alice@example.com", NotificationLanguage.VI, false));
        assertThat(result.get(BOB)).isEqualTo(new Recipient(BOB, "bob@example.com", NotificationLanguage.EN, true));
    }

    @Test
    void resolve_chunksLookupsBy100() {
        List<UUID> ids = IntStream.range(0, 150).mapToObj(i -> UUID.randomUUID()).toList();
        when(identityServiceClient.getEmailsByUserIds(anyList())).thenReturn(BaseResponse.success(Map.of()));
        when(userServiceClient.getNotificationPreferences(any())).thenReturn(BaseResponse.success(List.of()));

        Map<UUID, Recipient> result = directory.resolve(ids);

        assertThat(result).hasSize(150);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> emailChunks = ArgumentCaptor.forClass(List.class);
        verify(identityServiceClient, times(2)).getEmailsByUserIds(emailChunks.capture());
        assertThat(emailChunks.getAllValues()).extracting(List::size).containsExactly(100, 50);
        ArgumentCaptor<UserIdsRequest> prefChunks = ArgumentCaptor.forClass(UserIdsRequest.class);
        verify(userServiceClient, times(2)).getNotificationPreferences(prefChunks.capture());
        assertThat(prefChunks.getAllValues()).extracting(r -> r.userIds().size()).containsExactly(100, 50);
    }

    @Test
    void resolve_identityDown_keepsLanguageButHasNoEmail() {
        when(identityServiceClient.getEmailsByUserIds(anyList())).thenThrow(new RuntimeException("identity down"));
        when(userServiceClient.getNotificationPreferences(any()))
                .thenReturn(BaseResponse.success(List.of(pref(ALICE, "vi", true))));

        Recipient alice = directory.resolve(List.of(ALICE)).get(ALICE);

        assertThat(alice.email()).isNull();
        assertThat(alice.language()).isEqualTo(NotificationLanguage.VI);
    }

    @Test
    void resolve_userServiceDown_defaultsToEnglishAndOptIn() {
        when(identityServiceClient.getEmailsByUserIds(anyList()))
                .thenReturn(BaseResponse.success(Map.of(ALICE, "alice@example.com")));
        when(userServiceClient.getNotificationPreferences(any())).thenThrow(new RuntimeException("user-service down"));

        assertThat(directory.resolve(List.of(ALICE)).get(ALICE))
                .isEqualTo(new Recipient(ALICE, "alice@example.com", NotificationLanguage.EN, true));
    }

    @Test
    void resolve_empty_makesNoCalls() {
        assertThat(directory.resolve(List.of())).isEmpty();
        verifyNoInteractions(identityServiceClient, userServiceClient);
    }

    @Test
    void unknown_hasNoEmailEnglishAndOptIn() {
        assertThat(Recipient.unknown(ALICE)).isEqualTo(new Recipient(ALICE, null, NotificationLanguage.EN, true));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -pl media-service -am test -Dtest=RecipientDirectoryTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation FAILURE, because `RecipientDirectory`, `Recipient`, `UserServiceClient` and `UserIdsRequest` do not exist.

- [ ] **Step 3: Implement**

`UserIdsRequest.java`:

```java
package com.bidnow.media.feign;

import java.util.List;
import java.util.UUID;

/** Body of user-service batch lookups: {@code {"userIds": [...]}} (max 100). */
public record UserIdsRequest(List<UUID> userIds) {
}
```

`UserServiceClient.java`:

```java
package com.bidnow.media.feign;

import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.dto.UserNotificationPreferenceResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "user-service")
public interface UserServiceClient {

    @PostMapping("/api/v1/users/internal/notification-preferences")
    BaseResponse<List<UserNotificationPreferenceResponse>> getNotificationPreferences(@RequestBody UserIdsRequest request);
}
```

`Recipient.java`:

```java
package com.bidnow.media.notification;

import com.bidnow.media.domain.enums.NotificationLanguage;

import java.util.UUID;

/** Where and how to email a user. {@code email} is null when unknown; {@code emailOptIn} gates non-transactional emails. */
public record Recipient(UUID userId, String email, NotificationLanguage language, boolean emailOptIn) {

    public static Recipient unknown(UUID userId) {
        return new Recipient(userId, null, NotificationLanguage.EN, true);
    }
}
```

`RecipientDirectory.java`:

```java
package com.bidnow.media.notification;

import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.dto.UserNotificationPreferenceResponse;
import com.bidnow.media.domain.enums.NotificationLanguage;
import com.bidnow.media.feign.IdentityServiceClient;
import com.bidnow.media.feign.UserIdsRequest;
import com.bidnow.media.feign.UserServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Resolves user IDs to email address (identity-service) plus language and email opt-in (user-service), in
 * batches. A failing lookup degrades instead of throwing: no email for those users, or EN with opt-in.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecipientDirectory {

    /** user-service caps batch lookups at 100 IDs. */
    static final int CHUNK_SIZE = 100;

    private final IdentityServiceClient identityServiceClient;
    private final UserServiceClient userServiceClient;

    public Map<UUID, Recipient> resolve(Collection<UUID> userIds) {
        List<UUID> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<UUID, String> emails = new HashMap<>();
        Map<UUID, UserNotificationPreferenceResponse> preferences = new HashMap<>();
        for (int from = 0; from < ids.size(); from += CHUNK_SIZE) {
            List<UUID> chunk = List.copyOf(ids.subList(from, Math.min(from + CHUNK_SIZE, ids.size())));
            emails.putAll(fetchEmails(chunk));
            fetchPreferences(chunk).forEach(p -> preferences.put(p.getUserId(), p));
        }

        Map<UUID, Recipient> recipients = new LinkedHashMap<>();
        for (UUID id : ids) {
            UserNotificationPreferenceResponse preference = preferences.get(id);
            recipients.put(id, new Recipient(
                    id,
                    emails.get(id),
                    NotificationLanguage.fromCode(preference == null ? null : preference.getLanguage()),
                    preference == null || !Boolean.FALSE.equals(preference.getEmailNotifications())));
        }
        return recipients;
    }

    private Map<UUID, String> fetchEmails(List<UUID> chunk) {
        try {
            BaseResponse<Map<UUID, String>> response = identityServiceClient.getEmailsByUserIds(chunk);
            return response == null || response.getData() == null ? Map.of() : response.getData();
        } catch (RuntimeException ex) {
            log.warn("Email lookup failed for {} users - their emails are skipped: {}", chunk.size(), ex.getMessage());
            return Map.of();
        }
    }

    private List<UserNotificationPreferenceResponse> fetchPreferences(List<UUID> chunk) {
        try {
            BaseResponse<List<UserNotificationPreferenceResponse>> response =
                    userServiceClient.getNotificationPreferences(new UserIdsRequest(chunk));
            return response == null || response.getData() == null ? List.of() : response.getData();
        } catch (RuntimeException ex) {
            log.warn("Preference lookup failed for {} users - defaulting to EN with email opt-in: {}",
                    chunk.size(), ex.getMessage());
            return List.of();
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -pl media-service -am test -Dtest=RecipientDirectoryTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (6 tests)

- [ ] **Step 5: Stop for review.** Suggested commit: `feat(notification): resolve recipient email, language and opt-in (NOTIF-101)`

---

### Task 5: Template resolver and email linked to a notification

**Files:**
- Create: `backend/media-service/src/main/java/com/bidnow/media/notification/TemplateResolver.java`
- Modify: `backend/media-service/src/main/java/com/bidnow/media/service/EmailService.java`
- Modify: `backend/media-service/src/main/java/com/bidnow/media/service/impl/EmailServiceImpl.java`
- Test: `backend/media-service/src/test/java/com/bidnow/media/notification/TemplateResolverTest.java`
- Test: `backend/media-service/src/test/java/com/bidnow/media/service/impl/EmailServiceImplTest.java`

**Interfaces:**
- Consumes: `NotificationTemplateRepository.findByNameAndLanguageAndActiveTrue(String name, NotificationLanguage language)` (existing).
- Produces: `TemplateResolver.resolve(String baseName, NotificationLanguage language): Optional<NotificationTemplate>`. It looks up `{baseName}_{language}`, falls back to `{baseName}_EN` for VI, and returns empty (with an ERROR log) when none is active.
- Produces: `EmailService.sendTemplateEmail(UUID notificationId, String to, NotificationTemplate template, Map<String, Object> variables): EmailLog`, annotated `@Transactional(propagation = REQUIRES_NEW)` so it commits when called from an `afterCommit` hook. The existing 3-arg method delegates with `notificationId = null`.

- [ ] **Step 1: Write the failing tests**

`TemplateResolverTest.java`:

```java
package com.bidnow.media.notification;

import com.bidnow.media.domain.entity.NotificationTemplate;
import com.bidnow.media.domain.enums.NotificationLanguage;
import com.bidnow.media.repository.NotificationTemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateResolverTest {

    @Mock
    private NotificationTemplateRepository templateRepository;

    @InjectMocks
    private TemplateResolver resolver;

    private final NotificationTemplate vi = NotificationTemplate.builder().name("WELCOME_EMAIL_VI").build();
    private final NotificationTemplate en = NotificationTemplate.builder().name("WELCOME_EMAIL_EN").build();

    @Test
    void resolve_requestedLanguagePresent_returnsIt() {
        when(templateRepository.findByNameAndLanguageAndActiveTrue("WELCOME_EMAIL_VI", NotificationLanguage.VI))
                .thenReturn(Optional.of(vi));

        assertThat(resolver.resolve("WELCOME_EMAIL", NotificationLanguage.VI)).contains(vi);
    }

    @Test
    void resolve_vietnameseMissing_fallsBackToEnglish() {
        when(templateRepository.findByNameAndLanguageAndActiveTrue("WELCOME_EMAIL_VI", NotificationLanguage.VI))
                .thenReturn(Optional.empty());
        when(templateRepository.findByNameAndLanguageAndActiveTrue("WELCOME_EMAIL_EN", NotificationLanguage.EN))
                .thenReturn(Optional.of(en));

        assertThat(resolver.resolve("WELCOME_EMAIL", NotificationLanguage.VI)).contains(en);
    }

    @Test
    void resolve_noActiveTemplate_isEmpty() {
        when(templateRepository.findByNameAndLanguageAndActiveTrue(anyString(), eq(NotificationLanguage.VI)))
                .thenReturn(Optional.empty());
        when(templateRepository.findByNameAndLanguageAndActiveTrue(anyString(), eq(NotificationLanguage.EN)))
                .thenReturn(Optional.empty());

        assertThat(resolver.resolve("MISSING", NotificationLanguage.VI)).isEmpty();
    }

    @Test
    void resolve_englishMissing_doesNotLookUpTwice() {
        when(templateRepository.findByNameAndLanguageAndActiveTrue("MISSING_EN", NotificationLanguage.EN))
                .thenReturn(Optional.empty());

        assertThat(resolver.resolve("MISSING", NotificationLanguage.EN)).isEmpty();
        verify(templateRepository, never()).findByNameAndLanguageAndActiveTrue(anyString(), eq(NotificationLanguage.VI));
    }
}
```

`EmailServiceImplTest.java`:

```java
package com.bidnow.media.service.impl;

import com.bidnow.media.domain.entity.EmailLog;
import com.bidnow.media.domain.entity.NotificationTemplate;
import com.bidnow.media.domain.enums.EmailStatus;
import com.bidnow.media.feign.IdentityServiceClient;
import com.bidnow.media.repository.EmailLogRepository;
import com.bidnow.media.repository.NotificationTemplateRepository;
import com.bidnow.media.service.TemplateService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // shared stubs are unused by the annotation-only test
class EmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;
    @Mock
    private TemplateService templateService;
    @Mock
    private EmailLogRepository emailLogRepository;
    @Mock
    private NotificationTemplateRepository templateRepository;
    @Mock
    private IdentityServiceClient identityServiceClient;

    @InjectMocks
    private EmailServiceImpl emailService;

    private final NotificationTemplate template =
            NotificationTemplate.builder().name("WELCOME_EMAIL_EN").bodyText("Hi {userName}").build();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@bidnow.test");
        when(templateService.processSubject(any(), any())).thenReturn("Welcome");
        when(templateService.processHtmlBody(any(), any())).thenReturn(null);
        when(templateService.processTextBody(any(), any())).thenReturn("Hi Alice");
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        when(emailLogRepository.save(any(EmailLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void sendTemplateEmail_withNotificationId_linksTheLogAndMarksItSent() {
        UUID notificationId = UUID.randomUUID();

        EmailLog log = emailService.sendTemplateEmail(notificationId, "alice@example.com", template,
                Map.of("userName", "Alice"));

        assertThat(log.getNotificationId()).isEqualTo(notificationId);
        assertThat(log.getStatus()).isEqualTo(EmailStatus.SENT);
        assertThat(log.getTemplateName()).isEqualTo("WELCOME_EMAIL_EN");
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendTemplateEmail_smtpFailure_logsFailedWithoutThrowing() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(MimeMessage.class));

        EmailLog log = emailService.sendTemplateEmail(UUID.randomUUID(), "alice@example.com", template, Map.of());

        assertThat(log.getStatus()).isEqualTo(EmailStatus.FAILED);
        assertThat(log.getFailureReason()).contains("SMTP down");
    }

    @Test
    void sendTemplateEmail_withoutNotificationId_keepsLegacyBehaviour() {
        EmailLog log = emailService.sendTemplateEmail("alice@example.com", template, Map.of());

        assertThat(log.getNotificationId()).isNull();
        assertThat(log.getStatus()).isEqualTo(EmailStatus.SENT);
    }

    @Test
    void notificationOverload_runsInItsOwnTransaction() throws Exception {
        Method method = EmailServiceImpl.class.getMethod("sendTemplateEmail",
                UUID.class, String.class, NotificationTemplate.class, Map.class);

        assertThat(method.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q -pl media-service -am test -Dtest='TemplateResolverTest,EmailServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation FAILURE, because `TemplateResolver` and the 4-arg `sendTemplateEmail` do not exist.

- [ ] **Step 3: Implement `TemplateResolver`**

```java
package com.bidnow.media.notification;

import com.bidnow.media.domain.entity.NotificationTemplate;
import com.bidnow.media.domain.enums.NotificationLanguage;
import com.bidnow.media.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Picks the active {@code {baseName}_{LANG}} template; a missing or inactive VI template falls back to EN. */
@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateResolver {

    private final NotificationTemplateRepository templateRepository;

    public Optional<NotificationTemplate> resolve(String baseName, NotificationLanguage language) {
        Optional<NotificationTemplate> template = find(baseName, language);
        if (template.isEmpty() && language != NotificationLanguage.EN) {
            template = find(baseName, NotificationLanguage.EN);
            template.ifPresent(t -> log.warn("Template {}_{} missing or inactive - using EN", baseName, language));
        }
        if (template.isEmpty()) {
            log.error("No active template {} for {} (or EN) - email skipped", baseName, language);
        }
        return template;
    }

    private Optional<NotificationTemplate> find(String baseName, NotificationLanguage language) {
        return templateRepository.findByNameAndLanguageAndActiveTrue(baseName + "_" + language.name(), language);
    }
}
```

- [ ] **Step 4: Add the `EmailService` overload.** In `EmailService.java`, below the 3-arg `sendTemplateEmail`:

```java
    /**
     * Same as {@link #sendTemplateEmail(String, NotificationTemplate, Map)} but links the log to an in-app
     * notification. Runs in its own transaction so it commits even when called from an after-commit hook.
     *
     * @param notificationId the media_notifications row this email belongs to (nullable)
     */
    EmailLog sendTemplateEmail(UUID notificationId, String to, NotificationTemplate template, Map<String, Object> variables);
```

In `EmailServiceImpl.java`, add imports `org.springframework.transaction.annotation.Propagation` and `org.springframework.transaction.annotation.Transactional`. Replace the whole existing `sendTemplateEmail(String, NotificationTemplate, Map)` method with these two methods:

```java
    @Override
    public EmailLog sendTemplateEmail(String to, NotificationTemplate template, Map<String, Object> variables) {
        return sendTemplateEmail(null, to, template, variables);
    }

    // REQUIRES_NEW: the notification dispatcher calls this from afterCommit, where the outer transaction is
    // already committed and a joined save would never be flushed. Self-calls above bypass the proxy - fine,
    // those callers are not in an after-commit hook.
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EmailLog sendTemplateEmail(UUID notificationId, String to, NotificationTemplate template,
                                      Map<String, Object> variables) {
        log.info("Sending template email '{}' to: {}", template.getName(), to);

        String subject = templateService.processSubject(template, variables);
        String htmlBody = templateService.processHtmlBody(template, variables);
        String textBody = templateService.processTextBody(template, variables);

        EmailLog emailLog = EmailLog.builder()
                .notificationId(notificationId)
                .recipientEmail(to)
                .subject(subject)
                .templateName(template.getName())
                .retryCount(0)
                .build();

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);

            if (htmlBody != null) {
                helper.setText(textBody, htmlBody);
            } else {
                helper.setText(textBody, false);
            }

            mailSender.send(message);

            emailLog.setStatus(EmailStatus.SENT);
            emailLog.setSentAt(LocalDateTime.now());
            log.info("Template email sent successfully to: {}", to);

        } catch (Exception e) {
            log.error("Failed to send template email to: {}", to, e);
            emailLog.setStatus(EmailStatus.FAILED);
            emailLog.setFailureReason(e.getMessage());
        }

        return emailLogRepository.save(emailLog);
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn -q -pl media-service -am test -Dtest='TemplateResolverTest,EmailServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (8 tests)

- [ ] **Step 6: Stop for review.** Suggested commit: `feat(notification): resolve EN/VI templates and link emails to notifications (NOTIF-101)`

---

### Task 6: Live push pipeline (Kafka → STOMP user queue)

**Files:**
- Create: `backend/media-service/src/main/java/com/bidnow/media/dto/response/NotificationResponse.java`
- Create: `backend/media-service/src/main/java/com/bidnow/media/realtime/UserNotificationMessage.java`
- Create: `backend/media-service/src/main/java/com/bidnow/media/realtime/UserNotificationPush.java`
- Create: `backend/media-service/src/main/java/com/bidnow/media/realtime/UserNotificationPusher.java`
- Create: `backend/media-service/src/main/java/com/bidnow/media/kafka/UserNotificationPushPublisher.java`
- Create: `backend/media-service/src/main/java/com/bidnow/media/kafka/UserNotificationPushConsumer.java`
- Test: `backend/media-service/src/test/java/com/bidnow/media/realtime/UserNotificationPusherTest.java`
- Test: `backend/media-service/src/test/java/com/bidnow/media/realtime/UserNotificationPushJsonTest.java`
- Test: `backend/media-service/src/test/java/com/bidnow/media/kafka/UserNotificationPushPublisherTest.java`
- Test: `backend/media-service/src/test/java/com/bidnow/media/kafka/UserNotificationPushConsumerTest.java`

**Interfaces:**
- Produces: `NotificationResponse` (Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`): `UUID id; String type; String title; String message; String actionUrl; UUID auctionId; Map<String,Object> metadata; boolean read; LocalDateTime createdAt`. Story 2 reuses it for REST.
- Produces: `record UserNotificationMessage(String type, NotificationResponse notification, long unreadCount)` with `NOTIFICATION = "NOTIFICATION"` and `static notification(NotificationResponse, long)`.
- Produces: `record UserNotificationPush(UUID userId, UserNotificationMessage message)` (Kafka payload).
- Produces: `UserNotificationPushPublisher.publish(UUID userId, UserNotificationMessage message)` and `UserNotificationPushPublisher.TOPIC = "user-notification-push-topic"`.
- Produces: `UserNotificationPusher.push(UserNotificationPush)` → `convertAndSendToUser(userId, "/queue/notifications", message)`.

- [ ] **Step 1: Write the failing tests**

`UserNotificationPusherTest.java`:

```java
package com.bidnow.media.realtime;

import com.bidnow.media.dto.response.NotificationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserNotificationPusherTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private UserNotificationPusher pusher;

    private final UserNotificationMessage message = UserNotificationMessage.notification(
            NotificationResponse.builder().id(UUID.randomUUID()).type("AUCTION_WON").build(), 2);

    @Test
    void push_sendsToTheUsersNotificationQueue() {
        pusher.push(new UserNotificationPush(USER, message));

        verify(messagingTemplate).convertAndSendToUser(USER.toString(), "/queue/notifications", message);
    }

    @Test
    void push_deliveryFailure_isSwallowed() {
        doThrow(new MessageDeliveryException("no session"))
                .when(messagingTemplate).convertAndSendToUser(anyString(), anyString(), any(Object.class));

        assertThatCode(() -> pusher.push(new UserNotificationPush(USER, message))).doesNotThrowAnyException();
    }
}
```

`UserNotificationPushJsonTest.java`:

```java
package com.bidnow.media.realtime;

import com.bidnow.media.dto.response.NotificationResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserNotificationPushJsonTest {

    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();

    private final NotificationResponse notification = NotificationResponse.builder()
            .id(UUID.randomUUID()).type("AUCTION_WON").title("You won").message("You won Vintage Watch")
            .actionUrl("/auctions/x").auctionId(UUID.randomUUID()).metadata(Map.of("amount", "105.00"))
            .read(false).createdAt(LocalDateTime.of(2026, 10, 1, 10, 0))
            .build();

    @Test
    void kafkaPayload_roundTrips() throws Exception {
        UserNotificationPush push = new UserNotificationPush(UUID.randomUUID(),
                UserNotificationMessage.notification(notification, 3));

        UserNotificationPush read = mapper.readValue(mapper.writeValueAsString(push), UserNotificationPush.class);

        assertThat(read).isEqualTo(push);
    }

    @Test
    void stompMessage_hasContractShape() throws Exception {
        JsonNode root = mapper.readTree(mapper.writeValueAsString(UserNotificationMessage.notification(notification, 3)));

        assertThat(root.get("type").asText()).isEqualTo("NOTIFICATION");
        assertThat(root.get("unreadCount").asLong()).isEqualTo(3);
        assertThat(root.get("notification").get("read").asBoolean()).isFalse();
        assertThat(root.get("notification").get("createdAt").asText()).isEqualTo("2026-10-01T10:00:00");
    }
}
```

`UserNotificationPushPublisherTest.java`:

```java
package com.bidnow.media.kafka;

import com.bidnow.media.dto.response.NotificationResponse;
import com.bidnow.media.realtime.UserNotificationMessage;
import com.bidnow.media.realtime.UserNotificationPush;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserNotificationPushPublisherTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private UserNotificationPushPublisher publisher;

    private final UserNotificationMessage message =
            UserNotificationMessage.notification(NotificationResponse.builder().id(UUID.randomUUID()).build(), 1);

    @Test
    void publish_sendsKeyedByUser() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(new CompletableFuture<>());

        publisher.publish(USER, message);

        verify(kafkaTemplate).send("user-notification-push-topic", USER.toString(), new UserNotificationPush(USER, message));
    }

    @Test
    void publish_failedSend_isOnlyLogged() {
        CompletableFuture<SendResult<String, Object>> failed = CompletableFuture.failedFuture(new RuntimeException("broker down"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failed);

        assertThatCode(() -> publisher.publish(USER, message)).doesNotThrowAnyException();
    }
}
```

`UserNotificationPushConsumerTest.java`:

```java
package com.bidnow.media.kafka;

import com.bidnow.media.realtime.UserNotificationPush;
import com.bidnow.media.realtime.UserNotificationPusher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.annotation.KafkaListener;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserNotificationPushConsumerTest {

    @Mock
    private UserNotificationPusher pusher;

    @InjectMocks
    private UserNotificationPushConsumer consumer;

    @Test
    void onPush_delegatesToThePusher() {
        UserNotificationPush push = new UserNotificationPush(UUID.randomUUID(), null);

        consumer.onPush(push);

        verify(pusher).push(push);
    }

    @Test
    void listener_usesPerInstanceGroupReadingOnlyNewEvents() throws Exception {
        KafkaListener listener = UserNotificationPushConsumer.class
                .getMethod("onPush", UserNotificationPush.class).getAnnotation(KafkaListener.class);

        assertThat(listener.topics()).containsExactly("user-notification-push-topic");
        assertThat(listener.groupId()).isEqualTo("media-push-${random.uuid}");
        assertThat(listener.properties()).containsExactly("auto.offset.reset=latest");
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q -pl media-service -am test -Dtest='UserNotificationPush*Test,UserNotificationPusherTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation FAILURE, because the new types do not exist.

- [ ] **Step 3: Implement**

`NotificationResponse.java`:

```java
package com.bidnow.media.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private UUID id;
    private String type;
    private String title;
    private String message;
    private String actionUrl;
    private UUID auctionId;
    private Map<String, Object> metadata;
    private boolean read;
    private LocalDateTime createdAt;
}
```

`UserNotificationMessage.java`:

```java
package com.bidnow.media.realtime;

import com.bidnow.media.dto.response.NotificationResponse;

/** STOMP envelope on {@code /user/queue/notifications}: {@code {type, notification, unreadCount}}. */
public record UserNotificationMessage(String type, NotificationResponse notification, long unreadCount) {

    public static final String NOTIFICATION = "NOTIFICATION";

    public static UserNotificationMessage notification(NotificationResponse notification, long unreadCount) {
        return new UserNotificationMessage(NOTIFICATION, notification, unreadCount);
    }
}
```

`UserNotificationPush.java`:

```java
package com.bidnow.media.realtime;

import java.util.UUID;

/** Kafka payload on user-notification-push-topic: who to push to, and what. */
public record UserNotificationPush(UUID userId, UserNotificationMessage message) {
}
```

`UserNotificationPusher.java`:

```java
package com.bidnow.media.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Delivers a notification to the user's STOMP sessions on this instance. Best-effort: the notification is
 * already stored, so a failed push only means the client sees it on its next REST fetch.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserNotificationPusher {

    static final String USER_QUEUE = "/queue/notifications";

    private final SimpMessagingTemplate messagingTemplate;

    public void push(UserNotificationPush push) {
        try {
            messagingTemplate.convertAndSendToUser(push.userId().toString(), USER_QUEUE, push.message());
        } catch (RuntimeException ex) {
            log.warn("Notification push failed for user {}: {}", push.userId(), ex.getMessage());
        }
    }
}
```

`UserNotificationPushPublisher.java`:

```java
package com.bidnow.media.kafka;

import com.bidnow.media.realtime.UserNotificationMessage;
import com.bidnow.media.realtime.UserNotificationPush;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Fans a stored notification out to every media instance (see {@link UserNotificationPushConsumer}), so it
 * reaches the user whichever instance holds their WebSocket.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserNotificationPushPublisher {

    public static final String TOPIC = "user-notification-push-topic";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(UUID userId, UserNotificationMessage message) {
        kafkaTemplate.send(TOPIC, userId.toString(), new UserNotificationPush(userId, message))
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("Push for user {} not published - the notification is still in the inbox: {}",
                                userId, ex.getMessage());
                    }
                });
    }
}
```

`UserNotificationPushConsumer.java`:

```java
package com.bidnow.media.kafka;

import com.bidnow.media.realtime.UserNotificationPush;
import com.bidnow.media.realtime.UserNotificationPusher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Every media instance must see every push (each forwards to its own connected sessions), so this listener
 * uses a per-instance consumer group and reads only new events - same pattern as AuctionRealtimeConsumer.
 */
@Component
@RequiredArgsConstructor
public class UserNotificationPushConsumer {

    private static final String PER_INSTANCE_GROUP = "media-push-${random.uuid}";
    private static final String NEW_EVENTS_ONLY = "auto.offset.reset=latest";

    private final UserNotificationPusher pusher;

    @KafkaListener(topics = UserNotificationPushPublisher.TOPIC, groupId = PER_INSTANCE_GROUP, properties = NEW_EVENTS_ONLY)
    public void onPush(UserNotificationPush push) {
        pusher.push(push);
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q -pl media-service -am test -Dtest='UserNotificationPush*Test,UserNotificationPusherTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (8 tests)

- [ ] **Step 5: Stop for review.** Suggested commit: `feat(notification): push notifications to user queue via Kafka fan-out (NOTIF-101)`

---

### Task 7: NotificationDispatcher

**Files:**
- Create: `backend/media-service/src/main/java/com/bidnow/media/util/AfterCommit.java`
- Create: `backend/media-service/src/main/java/com/bidnow/media/config/ClockConfig.java`
- Create: `backend/media-service/src/main/java/com/bidnow/media/notification/NotificationIntent.java`
- Create: `backend/media-service/src/main/java/com/bidnow/media/notification/NotificationDispatcher.java`
- Test: `backend/media-service/src/test/java/com/bidnow/media/notification/NotificationDispatcherTest.java`

**Interfaces:**
- Consumes: `NotificationInboxRepository.insertIfAbsent/countUnread` (Task 2); `RecipientDirectory.resolve`, `Recipient.unknown` (Task 4); `TemplateResolver.resolve`, `EmailService.sendTemplateEmail(UUID, String, NotificationTemplate, Map)` (Task 5); `UserNotificationPushPublisher.publish`, `UserNotificationMessage.notification`, `NotificationResponse` (Task 6).
- Produces: `record NotificationIntent(UUID userId, NotificationType type, String dedupKey, UUID auctionId, String title, String message, String actionUrl, Map<String,Object> metadata, NotificationIntent.EmailSpec email)`, where `userId/type/dedupKey/title/message` are required and `email` is nullable.
- Produces: `record NotificationIntent.EmailSpec(String templateBaseName, Map<String,Object> variables, boolean transactional)`.
- Produces: `NotificationDispatcher.dispatch(NotificationIntent)` and `dispatchAll(List<NotificationIntent>)`, both `@Transactional`.
- Produces: `AfterCommit.run(Runnable)` (throws `IllegalStateException` outside a transaction) and a `Clock` bean.

- [ ] **Step 1: Write the failing test**

```java
package com.bidnow.media.notification;

import com.bidnow.media.domain.entity.Notification;
import com.bidnow.media.domain.entity.NotificationTemplate;
import com.bidnow.media.domain.enums.NotificationChannel;
import com.bidnow.media.domain.enums.NotificationLanguage;
import com.bidnow.media.domain.enums.NotificationStatus;
import com.bidnow.media.domain.enums.NotificationType;
import com.bidnow.media.kafka.UserNotificationPushPublisher;
import com.bidnow.media.realtime.UserNotificationMessage;
import com.bidnow.media.repository.NotificationInboxRepository;
import com.bidnow.media.service.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID AUCTION = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final Map<String, Object> VARS = Map.of("userName", "Alice");

    @Mock
    private NotificationInboxRepository inboxRepository;
    @Mock
    private RecipientDirectory recipientDirectory;
    @Mock
    private TemplateResolver templateResolver;
    @Mock
    private EmailService emailService;
    @Mock
    private UserNotificationPushPublisher pushPublisher;

    private NotificationDispatcher dispatcher;
    private final NotificationTemplate template = NotificationTemplate.builder().name("AUCTION_WON_VI").build();

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC);
        dispatcher = new NotificationDispatcher(inboxRepository, recipientDirectory, templateResolver,
                emailService, pushPublisher, clock);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private static void commit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private static NotificationIntent intent(UUID userId, NotificationIntent.EmailSpec email) {
        return new NotificationIntent(userId, NotificationType.AUCTION_WON, "AUCTION_WON:" + AUCTION, AUCTION,
                "You won", "You won Vintage Watch", "/auctions/" + AUCTION, Map.of("amount", "105.00"), email);
    }

    private static NotificationIntent.EmailSpec email(boolean transactional) {
        return new NotificationIntent.EmailSpec("AUCTION_WON", VARS, transactional);
    }

    @Test
    void dispatch_newNotification_persistsNowAndEmailsAndPushesAfterCommit() {
        when(inboxRepository.insertIfAbsent(any())).thenReturn(true);
        when(inboxRepository.countUnread(ALICE)).thenReturn(3L);
        when(recipientDirectory.resolve(Set.of(ALICE)))
                .thenReturn(Map.of(ALICE, new Recipient(ALICE, "alice@example.com", NotificationLanguage.VI, true)));
        when(templateResolver.resolve("AUCTION_WON", NotificationLanguage.VI)).thenReturn(Optional.of(template));

        dispatcher.dispatch(intent(ALICE, email(false)));

        verifyNoInteractions(recipientDirectory, emailService, pushPublisher);
        commit();

        ArgumentCaptor<Notification> row = ArgumentCaptor.forClass(Notification.class);
        verify(inboxRepository).insertIfAbsent(row.capture());
        assertThat(row.getValue().getUserId()).isEqualTo(ALICE);
        assertThat(row.getValue().getType()).isEqualTo(NotificationType.AUCTION_WON);
        assertThat(row.getValue().getChannel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(row.getValue().getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(row.getValue().getDedupKey()).isEqualTo("AUCTION_WON:" + AUCTION);
        assertThat(row.getValue().getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 10, 0));
        UUID notificationId = row.getValue().getId();
        assertThat(notificationId).isNotNull();

        verify(emailService).sendTemplateEmail(notificationId, "alice@example.com", template, VARS);

        ArgumentCaptor<UserNotificationMessage> pushed = ArgumentCaptor.forClass(UserNotificationMessage.class);
        verify(pushPublisher).publish(eq(ALICE), pushed.capture());
        assertThat(pushed.getValue().type()).isEqualTo(UserNotificationMessage.NOTIFICATION);
        assertThat(pushed.getValue().unreadCount()).isEqualTo(3);
        assertThat(pushed.getValue().notification().getId()).isEqualTo(notificationId);
        assertThat(pushed.getValue().notification().getType()).isEqualTo("AUCTION_WON");
        assertThat(pushed.getValue().notification().isRead()).isFalse();
        assertThat(pushed.getValue().notification().getMetadata()).containsEntry("amount", "105.00");
    }

    @Test
    void dispatch_duplicate_hasNoSideEffects() {
        when(inboxRepository.insertIfAbsent(any())).thenReturn(false);

        dispatcher.dispatch(intent(ALICE, email(false)));
        commit();

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verify(inboxRepository, never()).countUnread(any());
        verifyNoInteractions(recipientDirectory, templateResolver, emailService, pushPublisher);
    }

    @Test
    void dispatch_withoutEmail_onlyPushes() {
        when(inboxRepository.insertIfAbsent(any())).thenReturn(true);
        when(inboxRepository.countUnread(ALICE)).thenReturn(1L);

        dispatcher.dispatch(intent(ALICE, null));
        commit();

        verifyNoInteractions(recipientDirectory, templateResolver, emailService);
        verify(pushPublisher).publish(eq(ALICE), any());
    }

    @Test
    void dispatch_optedOutUser_skipsNonTransactionalEmail() {
        when(inboxRepository.insertIfAbsent(any())).thenReturn(true);
        when(recipientDirectory.resolve(any()))
                .thenReturn(Map.of(ALICE, new Recipient(ALICE, "alice@example.com", NotificationLanguage.EN, false)));

        dispatcher.dispatch(intent(ALICE, email(false)));
        commit();

        verifyNoInteractions(templateResolver, emailService);
        verify(pushPublisher).publish(eq(ALICE), any());
    }

    @Test
    void dispatch_optedOutUser_stillGetsTransactionalEmail() {
        when(inboxRepository.insertIfAbsent(any())).thenReturn(true);
        when(recipientDirectory.resolve(any()))
                .thenReturn(Map.of(ALICE, new Recipient(ALICE, "alice@example.com", NotificationLanguage.EN, false)));
        when(templateResolver.resolve("AUCTION_WON", NotificationLanguage.EN)).thenReturn(Optional.of(template));

        dispatcher.dispatch(intent(ALICE, email(true)));
        commit();

        verify(emailService).sendTemplateEmail(any(UUID.class), eq("alice@example.com"), eq(template), eq(VARS));
    }

    @Test
    void dispatch_recipientWithoutEmail_skipsEmailButPushes() {
        when(inboxRepository.insertIfAbsent(any())).thenReturn(true);
        when(recipientDirectory.resolve(any())).thenReturn(Map.of());

        dispatcher.dispatch(intent(ALICE, email(true)));
        commit();

        verifyNoInteractions(templateResolver, emailService);
        verify(pushPublisher).publish(eq(ALICE), any());
    }

    @Test
    void dispatch_emailServiceThrows_stillPushes() {
        when(inboxRepository.insertIfAbsent(any())).thenReturn(true);
        when(recipientDirectory.resolve(any()))
                .thenReturn(Map.of(ALICE, new Recipient(ALICE, "alice@example.com", NotificationLanguage.EN, true)));
        when(templateResolver.resolve(anyString(), any())).thenReturn(Optional.of(template));
        doThrow(new IllegalStateException("template broken"))
                .when(emailService).sendTemplateEmail(any(UUID.class), anyString(), any(), any());

        dispatcher.dispatch(intent(ALICE, email(true)));
        commit();

        verify(pushPublisher).publish(eq(ALICE), any());
    }

    @Test
    void dispatchAll_resolvesRecipientsOnceForTheBatch() {
        when(inboxRepository.insertIfAbsent(any())).thenReturn(true);
        when(recipientDirectory.resolve(Set.of(ALICE, BOB))).thenReturn(Map.of());

        dispatcher.dispatchAll(List.of(intent(ALICE, email(true)), intent(BOB, email(true))));
        commit();

        verify(recipientDirectory).resolve(Set.of(ALICE, BOB));
        verify(pushPublisher).publish(eq(ALICE), any());
        verify(pushPublisher).publish(eq(BOB), any());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -pl media-service -am test -Dtest=NotificationDispatcherTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation FAILURE, because `NotificationDispatcher` and `NotificationIntent` do not exist.

- [ ] **Step 3: Implement the supporting pieces**

`AfterCommit.java`:

```java
package com.bidnow.media.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Defers side effects (email, Kafka push) until the surrounding transaction commits, so they never fire for
 * a rolled-back notification. Any data access inside the action needs its own transaction (REQUIRES_NEW).
 */
@Slf4j
public final class AfterCommit {

    private AfterCommit() {
    }

    /**
     * @throws IllegalStateException if no transaction synchronization is active
     */
    public static void run(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    action.run();
                } catch (RuntimeException ex) {
                    // the notification is already committed, so the caller must not see an error
                    log.error("After-commit action failed - the notification is stored but was not delivered", ex);
                }
            }
        });
    }
}
```

`ClockConfig.java`:

```java
package com.bidnow.media.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /** System default zone, matching BaseEntity's LocalDateTime.now() timestamps in media tables. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
```

`NotificationIntent.java`:

```java
package com.bidnow.media.notification;

import com.bidnow.media.domain.enums.NotificationType;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * What a handler wants delivered to one user: an in-app notification (always) plus an optional email.
 * {@code actionUrl} is a frontend-relative path (e.g. /auctions/{id}); {@code email} may be null.
 */
public record NotificationIntent(
        UUID userId,
        NotificationType type,
        String dedupKey,
        UUID auctionId,
        String title,
        String message,
        String actionUrl,
        Map<String, Object> metadata,
        EmailSpec email) {

    public NotificationIntent {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(dedupKey, "dedupKey");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(message, "message");
    }

    /**
     * @param templateBaseName template name without the language suffix, e.g. WELCOME_EMAIL
     * @param transactional    true = sent even if the user opted out of emails (payments, wins)
     */
    public record EmailSpec(String templateBaseName, Map<String, Object> variables, boolean transactional) {
    }
}
```

- [ ] **Step 4: Implement `NotificationDispatcher`**

```java
package com.bidnow.media.notification;

import com.bidnow.media.domain.entity.Notification;
import com.bidnow.media.domain.enums.NotificationChannel;
import com.bidnow.media.domain.enums.NotificationStatus;
import com.bidnow.media.dto.response.NotificationResponse;
import com.bidnow.media.kafka.UserNotificationPushPublisher;
import com.bidnow.media.realtime.UserNotificationMessage;
import com.bidnow.media.repository.NotificationInboxRepository;
import com.bidnow.media.service.EmailService;
import com.bidnow.media.util.AfterCommit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Single entry point for delivering notifications. Inside the transaction: idempotent insert (one row per
 * user and dedup key) and unread count. After commit: email and live push. Duplicates produce nothing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final NotificationInboxRepository inboxRepository;
    private final RecipientDirectory recipientDirectory;
    private final TemplateResolver templateResolver;
    private final EmailService emailService;
    private final UserNotificationPushPublisher pushPublisher;
    private final Clock clock;

    @Transactional
    public void dispatch(NotificationIntent intent) {
        dispatchAll(List.of(intent));
    }

    @Transactional
    public void dispatchAll(List<NotificationIntent> intents) {
        List<Delivery> deliveries = new ArrayList<>();
        for (NotificationIntent intent : intents) {
            Notification row = toRow(intent);
            if (!inboxRepository.insertIfAbsent(row)) {
                log.debug("Duplicate notification {} for user {} ignored", intent.dedupKey(), intent.userId());
                continue;
            }
            deliveries.add(new Delivery(intent, toResponse(row), inboxRepository.countUnread(intent.userId())));
        }
        if (!deliveries.isEmpty()) {
            AfterCommit.run(() -> deliver(deliveries));
        }
    }

    private void deliver(List<Delivery> deliveries) {
        Set<UUID> emailUserIds = deliveries.stream()
                .filter(d -> d.intent().email() != null)
                .map(d -> d.intent().userId())
                .collect(Collectors.toSet());
        Map<UUID, Recipient> recipients = emailUserIds.isEmpty() ? Map.of() : recipientDirectory.resolve(emailUserIds);

        for (Delivery delivery : deliveries) {
            UUID userId = delivery.intent().userId();
            if (delivery.intent().email() != null) {
                sendEmail(delivery, recipients.getOrDefault(userId, Recipient.unknown(userId)));
            }
            try {
                pushPublisher.publish(userId, UserNotificationMessage.notification(delivery.notification(), delivery.unreadCount()));
            } catch (RuntimeException ex) {
                log.warn("Push for notification {} failed: {}", delivery.notification().getId(), ex.getMessage());
            }
        }
    }

    private void sendEmail(Delivery delivery, Recipient recipient) {
        NotificationIntent.EmailSpec spec = delivery.intent().email();
        if (recipient.email() == null) {
            log.warn("No email address for user {} - {} email skipped", recipient.userId(), spec.templateBaseName());
            return;
        }
        if (!spec.transactional() && !recipient.emailOptIn()) {
            log.debug("User {} opted out of emails - {} skipped", recipient.userId(), spec.templateBaseName());
            return;
        }
        templateResolver.resolve(spec.templateBaseName(), recipient.language()).ifPresent(template -> {
            try {
                emailService.sendTemplateEmail(delivery.notification().getId(), recipient.email(), template, spec.variables());
            } catch (RuntimeException ex) {
                log.error("Email {} for user {} failed: {}", template.getName(), recipient.userId(), ex.getMessage());
            }
        });
    }

    private Notification toRow(NotificationIntent intent) {
        Notification row = Notification.builder()
                .id(UUID.randomUUID())
                .userId(intent.userId())
                .type(intent.type())
                .channel(NotificationChannel.IN_APP)
                .status(NotificationStatus.SENT)
                .title(intent.title())
                .message(intent.message())
                .actionUrl(intent.actionUrl())
                .auctionId(intent.auctionId())
                .metadata(intent.metadata())
                .dedupKey(intent.dedupKey())
                .build();
        row.setCreatedAt(LocalDateTime.now(clock));
        return row;
    }

    private static NotificationResponse toResponse(Notification row) {
        return NotificationResponse.builder()
                .id(row.getId())
                .type(row.getType().name())
                .title(row.getTitle())
                .message(row.getMessage())
                .actionUrl(row.getActionUrl())
                .auctionId(row.getAuctionId())
                .metadata(row.getMetadata())
                .read(false)
                .createdAt(row.getCreatedAt())
                .build();
    }

    private record Delivery(NotificationIntent intent, NotificationResponse notification, long unreadCount) {
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn -q -pl media-service -am test -Dtest=NotificationDispatcherTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (8 tests)

- [ ] **Step 6: Stop for review.** Suggested commit: `feat(notification): add idempotent notification dispatcher (NOTIF-101)`

---

### Task 8: Auction/participant projection consumer

**Files:**
- Create: `backend/media-service/src/main/java/com/bidnow/media/projection/AuctionProjectionService.java`
- Create: `backend/media-service/src/main/java/com/bidnow/media/kafka/AuctionProjectionConsumer.java`
- Test: `backend/media-service/src/test/java/com/bidnow/media/projection/AuctionProjectionServiceTest.java`
- Test: `backend/media-service/src/test/java/com/bidnow/media/kafka/AuctionProjectionConsumerTest.java`

**Interfaces:**
- Consumes: `AuctionProjectionRepository.upsertAuction/upsertParticipant` (Task 2); `Clock` bean (Task 7); events `AuctionCreatedEvent {auctionId, sellerId, title, endTime: Instant}`, `BidPlacedEvent {auctionId, auctionTitle, bidderId, bidAmount, bidTime: LocalDateTime, endTime: OffsetDateTime}`, `AuctionExtendedEvent {auctionId, auctionTitle, newEndTime: Instant}`.
- Produces: `AuctionProjectionService.onAuctionCreated(AuctionCreatedEvent)`, `onBidPlaced(BidPlacedEvent)` and `onAuctionExtended(AuctionExtendedEvent)`. Kafka listeners in group `media-projection-group`.

- [ ] **Step 1: Write the failing tests**

`AuctionProjectionServiceTest.java`:

```java
package com.bidnow.media.projection;

import com.bidnow.common.dto.event.AuctionCreatedEvent;
import com.bidnow.common.dto.event.AuctionExtendedEvent;
import com.bidnow.common.dto.event.BidPlacedEvent;
import com.bidnow.media.repository.AuctionProjectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuctionProjectionServiceTest {

    private static final UUID AUCTION = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID BIDDER = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Mock
    private AuctionProjectionRepository repository;

    private AuctionProjectionService service;

    @BeforeEach
    void setUp() {
        service = new AuctionProjectionService(repository,
                Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void onAuctionCreated_upsertsFullAuction() {
        service.onAuctionCreated(AuctionCreatedEvent.builder()
                .auctionId(AUCTION).sellerId(SELLER).title("Vintage Watch")
                .endTime(Instant.parse("2026-10-02T12:00:00Z")).build());

        verify(repository).upsertAuction(AUCTION, "Vintage Watch", SELLER, OffsetDateTime.parse("2026-10-02T12:00:00Z"));
    }

    @Test
    void onBidPlaced_upsertsAuctionAndParticipant() {
        LocalDateTime bidTime = LocalDateTime.of(2026, 10, 1, 11, 0);
        OffsetDateTime endTime = OffsetDateTime.parse("2026-10-02T12:00:00Z");

        service.onBidPlaced(BidPlacedEvent.builder()
                .auctionId(AUCTION).auctionTitle("Vintage Watch").bidderId(BIDDER)
                .bidAmount(new BigDecimal("105.00")).bidTime(bidTime).endTime(endTime).build());

        verify(repository).upsertAuction(AUCTION, "Vintage Watch", null, endTime);
        verify(repository).upsertParticipant(AUCTION, BIDDER, new BigDecimal("105.00"), bidTime);
    }

    @Test
    void onBidPlaced_missingBidTime_usesNow() {
        service.onBidPlaced(BidPlacedEvent.builder()
                .auctionId(AUCTION).bidderId(BIDDER).bidAmount(new BigDecimal("105.00")).build());

        verify(repository).upsertParticipant(AUCTION, BIDDER, new BigDecimal("105.00"), LocalDateTime.of(2026, 10, 1, 10, 0));
    }

    @Test
    void onBidPlaced_withoutBidder_skipsParticipant() {
        service.onBidPlaced(BidPlacedEvent.builder().auctionId(AUCTION).auctionTitle("Vintage Watch").build());

        verify(repository).upsertAuction(AUCTION, "Vintage Watch", null, null);
        verify(repository, never()).upsertParticipant(any(), any(), any(), any());
    }

    @Test
    void onAuctionExtended_updatesEndTime() {
        service.onAuctionExtended(AuctionExtendedEvent.builder()
                .auctionId(AUCTION).auctionTitle("Vintage Watch")
                .newEndTime(Instant.parse("2026-10-02T12:05:00Z")).build());

        verify(repository).upsertAuction(AUCTION, "Vintage Watch", null, OffsetDateTime.parse("2026-10-02T12:05:00Z"));
    }
}
```

`AuctionProjectionConsumerTest.java`:

```java
package com.bidnow.media.kafka;

import com.bidnow.common.dto.event.AuctionCreatedEvent;
import com.bidnow.common.dto.event.AuctionExtendedEvent;
import com.bidnow.common.dto.event.BidPlacedEvent;
import com.bidnow.media.projection.AuctionProjectionService;
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
class AuctionProjectionConsumerTest {

    @Mock
    private AuctionProjectionService projectionService;

    @InjectMocks
    private AuctionProjectionConsumer consumer;

    @Test
    void delegatesEachEvent() {
        AuctionCreatedEvent created = AuctionCreatedEvent.builder().auctionId(UUID.randomUUID()).build();
        BidPlacedEvent bid = BidPlacedEvent.builder().auctionId(UUID.randomUUID()).build();
        AuctionExtendedEvent extended = AuctionExtendedEvent.builder().auctionId(UUID.randomUUID()).build();

        consumer.onAuctionCreated(created);
        consumer.onBidPlaced(bid);
        consumer.onAuctionExtended(extended);

        verify(projectionService).onAuctionCreated(created);
        verify(projectionService).onBidPlaced(bid);
        verify(projectionService).onAuctionExtended(extended);
    }

    @Test
    void listenersUseTheirOwnStableGroup() {
        Map<String, KafkaListener> listeners = Arrays.stream(AuctionProjectionConsumer.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(KafkaListener.class))
                .collect(Collectors.toMap(Method::getName, m -> m.getAnnotation(KafkaListener.class)));

        assertThat(listeners).containsOnlyKeys("onAuctionCreated", "onBidPlaced", "onAuctionExtended");
        assertThat(listeners.get("onAuctionCreated").topics()).containsExactly("auction-created-topic");
        assertThat(listeners.get("onBidPlaced").topics()).containsExactly("bid-placed-topic");
        assertThat(listeners.get("onAuctionExtended").topics()).containsExactly("auction-extended-topic");
        // Not media-service-group: NotificationKafkaConsumer already listens to bid-placed-topic in that
        // group, and two listeners in one group split the partitions between them.
        listeners.values().forEach(l -> assertThat(l.groupId()).isEqualTo("media-projection-group"));
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q -pl media-service -am test -Dtest='AuctionProjection*Test' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation FAILURE, because `AuctionProjectionService` and `AuctionProjectionConsumer` do not exist.

- [ ] **Step 3: Implement**

`AuctionProjectionService.java`:

```java
package com.bidnow.media.projection;

import com.bidnow.common.dto.event.AuctionCreatedEvent;
import com.bidnow.common.dto.event.AuctionExtendedEvent;
import com.bidnow.common.dto.event.BidPlacedEvent;
import com.bidnow.media.repository.AuctionProjectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Maintains media-service's own view of auctions and who bid on them, so notification handlers can find
 * titles, sellers, losers and active bidders without calling other services.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionProjectionService {

    private final AuctionProjectionRepository repository;
    private final Clock clock;

    public void onAuctionCreated(AuctionCreatedEvent event) {
        repository.upsertAuction(event.getAuctionId(), event.getTitle(), event.getSellerId(), toUtc(event.getEndTime()));
    }

    @Transactional
    public void onBidPlaced(BidPlacedEvent event) {
        repository.upsertAuction(event.getAuctionId(), event.getAuctionTitle(), null, event.getEndTime());
        if (event.getBidderId() == null || event.getBidAmount() == null) {
            log.warn("BidPlacedEvent for auction {} has no bidder/amount - participant not recorded", event.getAuctionId());
            return;
        }
        LocalDateTime bidAt = event.getBidTime() != null ? event.getBidTime() : LocalDateTime.now(clock);
        repository.upsertParticipant(event.getAuctionId(), event.getBidderId(), event.getBidAmount(), bidAt);
    }

    public void onAuctionExtended(AuctionExtendedEvent event) {
        repository.upsertAuction(event.getAuctionId(), event.getAuctionTitle(), null, toUtc(event.getNewEndTime()));
    }

    private static OffsetDateTime toUtc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
```

`AuctionProjectionConsumer.java`:

```java
package com.bidnow.media.kafka;

import com.bidnow.common.dto.event.AuctionCreatedEvent;
import com.bidnow.common.dto.event.AuctionExtendedEvent;
import com.bidnow.common.dto.event.BidPlacedEvent;
import com.bidnow.media.projection.AuctionProjectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Feeds the auction/participant projection. Uses its own stable group: NotificationKafkaConsumer already
 * consumes bid-placed-topic in media-service-group, and two listeners in one group would split partitions.
 * Reads from the earliest offset on first start, which backfills bids still retained in Kafka.
 */
@Component
@RequiredArgsConstructor
public class AuctionProjectionConsumer {

    private static final String PROJECTION_GROUP = "media-projection-group";

    private final AuctionProjectionService projectionService;

    @KafkaListener(topics = "auction-created-topic", groupId = PROJECTION_GROUP)
    public void onAuctionCreated(AuctionCreatedEvent event) {
        projectionService.onAuctionCreated(event);
    }

    @KafkaListener(topics = "bid-placed-topic", groupId = PROJECTION_GROUP)
    public void onBidPlaced(BidPlacedEvent event) {
        projectionService.onBidPlaced(event);
    }

    @KafkaListener(topics = "auction-extended-topic", groupId = PROJECTION_GROUP)
    public void onAuctionExtended(AuctionExtendedEvent event) {
        projectionService.onAuctionExtended(event);
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q -pl media-service -am test -Dtest='AuctionProjection*Test' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (7 tests)

- [ ] **Step 5: Stop for review.** Suggested commit: `feat(notification): project auctions and bidders from Kafka events (NOTIF-101)`

---

### Task 9: Welcome through the dispatcher, OTP through the template resolver

**Files:**
- Modify: `backend/media-service/src/main/java/com/bidnow/media/service/impl/NotificationServiceImpl.java`
- Test: `backend/media-service/src/test/java/com/bidnow/media/service/impl/NotificationServiceImplTest.java`

**Interfaces:**
- Consumes: `NotificationDispatcher.dispatch`, `NotificationIntent`, `NotificationIntent.EmailSpec`, `DedupKeys.welcome()` (Tasks 3, 7), `TemplateResolver.resolve` (Task 5), `EmailService.sendTemplateEmail(String, NotificationTemplate, Map)` (existing).
- Produces: the `NotificationService` interface is unchanged. The other handler stubs stay (Story 4).

- [ ] **Step 1: Write the failing test**

```java
package com.bidnow.media.service.impl;

import com.bidnow.common.dto.event.UserRegisteredEvent;
import com.bidnow.common.dto.event.UserVerificationRequestedEvent;
import com.bidnow.media.domain.entity.NotificationTemplate;
import com.bidnow.media.domain.enums.NotificationLanguage;
import com.bidnow.media.domain.enums.NotificationType;
import com.bidnow.media.notification.NotificationDispatcher;
import com.bidnow.media.notification.NotificationIntent;
import com.bidnow.media.notification.TemplateResolver;
import com.bidnow.media.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @Mock
    private EmailService emailService;
    @Mock
    private TemplateResolver templateResolver;
    @Mock
    private NotificationDispatcher dispatcher;

    @InjectMocks
    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "http://localhost:3000");
    }

    private NotificationIntent capturedIntent() {
        ArgumentCaptor<NotificationIntent> intent = ArgumentCaptor.forClass(NotificationIntent.class);
        verify(dispatcher).dispatch(intent.capture());
        return intent.getValue();
    }

    @Test
    void userRegistered_dispatchesWelcomeInAppAndEmail() {
        service.handleUserRegistered(UserRegisteredEvent.builder()
                .userId(USER).email("alice@example.com").firstName("Alice").build());

        NotificationIntent intent = capturedIntent();
        assertThat(intent.userId()).isEqualTo(USER);
        assertThat(intent.type()).isEqualTo(NotificationType.USER_REGISTERED);
        assertThat(intent.dedupKey()).isEqualTo("WELCOME");
        assertThat(intent.title()).isEqualTo("Welcome to BidNow");
        assertThat(intent.actionUrl()).isEqualTo("/auctions");
        assertThat(intent.email().templateBaseName()).isEqualTo("WELCOME_EMAIL");
        assertThat(intent.email().transactional()).isFalse();
        assertThat(intent.email().variables())
                .containsEntry("userName", "Alice")
                .containsEntry("actionUrl", "http://localhost:3000/auctions");
    }

    @Test
    void userRegistered_withoutFirstName_derivesNameFromEmail() {
        service.handleUserRegistered(UserRegisteredEvent.builder().userId(USER).email("john.doe@example.com").build());

        assertThat(capturedIntent().email().variables()).containsEntry("userName", "john.doe");
    }

    @Test
    void verificationRequested_sendsOtpEmailInEnglishWithoutInAppRow() {
        NotificationTemplate otp = NotificationTemplate.builder().name("OTP_VERIFICATION_EN").build();
        when(templateResolver.resolve("OTP_VERIFICATION", NotificationLanguage.EN)).thenReturn(Optional.of(otp));

        service.handleUserVerificationRequested(UserVerificationRequestedEvent.builder()
                .userId(USER).email("alice@example.com").otp("123456").build());

        verify(emailService).sendTemplateEmail("alice@example.com", otp, Map.of("otp", "123456"));
        verifyNoInteractions(dispatcher);
    }

    @Test
    void verificationRequested_missingTemplate_sendsNothing() {
        when(templateResolver.resolve("OTP_VERIFICATION", NotificationLanguage.EN)).thenReturn(Optional.empty());

        service.handleUserVerificationRequested(UserVerificationRequestedEvent.builder()
                .userId(USER).email("alice@example.com").otp("123456").build());

        verifyNoInteractions(emailService, dispatcher);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -pl media-service -am test -Dtest=NotificationServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL. The dispatcher is never called, and `@InjectMocks` cannot supply the old `NotificationTemplateRepository` constructor argument. It may also fail at compile time on the missing constructor parameters.

- [ ] **Step 3: Implement.** Replace `NotificationServiceImpl.java` with the following (the Story 4 stubs are kept verbatim):

```java
package com.bidnow.media.service.impl;

import com.bidnow.common.annotation.Loggable;
import com.bidnow.common.dto.event.AuctionCreatedEvent;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import com.bidnow.common.dto.event.BidPlacedEvent;
import com.bidnow.common.dto.event.PaymentEvent;
import com.bidnow.common.dto.event.UserRegisteredEvent;
import com.bidnow.common.dto.event.UserVerificationRequestedEvent;
import com.bidnow.media.domain.enums.NotificationLanguage;
import com.bidnow.media.domain.enums.NotificationType;
import com.bidnow.media.notification.DedupKeys;
import com.bidnow.media.notification.NotificationDispatcher;
import com.bidnow.media.notification.NotificationIntent;
import com.bidnow.media.notification.TemplateResolver;
import com.bidnow.media.service.EmailService;
import com.bidnow.media.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Loggable
public class NotificationServiceImpl implements NotificationService {

    private final EmailService emailService;
    private final TemplateResolver templateResolver;
    private final NotificationDispatcher dispatcher;
    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    // -------------------------------------------------------------------------
    // OTP Verification — triggered by USER_VERIFICATION_REQUESTED event
    // -------------------------------------------------------------------------

    @Override
    public void handleUserVerificationRequested(UserVerificationRequestedEvent event) {
        log.info("Handling UserVerificationRequestedEvent for user: {}", event.getUserId());

        // Email only: the account is not active yet, so there is no inbox, and no language preference to read
        templateResolver.resolve("OTP_VERIFICATION", NotificationLanguage.EN).ifPresent(template -> {
            emailService.sendTemplateEmail(event.getEmail(), template, Map.of("otp", event.getOtp()));
            log.info("OTP verification email sent to: {}", event.getEmail());
        });
    }

    // -------------------------------------------------------------------------
    // Welcome — triggered by USER_REGISTERED event (after OTP verified)
    // -------------------------------------------------------------------------

    @Override
    public void handleUserRegistered(UserRegisteredEvent event) {
        log.info("Handling UserRegisteredEvent for user: {}", event.getUserId());

        String userName = displayName(event);
        dispatcher.dispatch(new NotificationIntent(
                event.getUserId(),
                NotificationType.USER_REGISTERED,
                DedupKeys.welcome(),
                null,
                "Welcome to BidNow",
                "Hi " + userName + ", your account is ready. Explore live auctions and place your first bid.",
                "/auctions",
                null,
                new NotificationIntent.EmailSpec("WELCOME_EMAIL",
                        Map.of("userName", userName, "actionUrl", frontendBaseUrl + "/auctions"),
                        false)));
    }

    // -------------------------------------------------------------------------
    // Remaining handlers (stubs — implemented in later issues)
    // -------------------------------------------------------------------------

    @Override
    public void handleAuctionCreated(AuctionCreatedEvent event) {
        log.info("Handling AuctionCreatedEvent for auction: {}", event.getAuctionId());
        // TODO: Implement in issue #29 (Seller Auction Management)
    }

    @Override
    public void handleBidPlaced(BidPlacedEvent event) {
        log.info("Handling BidPlacedEvent for auction: {}", event.getAuctionId());
        // TODO: Implement real-time broadcast and smart batching in issue #19
    }

    @Override
    public void handleAuctionEnded(AuctionEndedEvent event) {
        log.info("Handling AuctionEndedEvent for auction: {}", event.getAuctionId());
        // TODO: Implement winner/loser emails in issue #19
    }

    @Override
    public void handlePaymentEvent(PaymentEvent event) {
        log.info("Handling PaymentEvent for auction: {}, type: {}", event.getAuctionId(), event.getPaymentType());
        // TODO: Implement payment emails in issue #19
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** First name when known, else the local part of the email ("john.doe@x" → "john.doe"), else "there". */
    private static String displayName(UserRegisteredEvent event) {
        if (StringUtils.hasText(event.getFirstName())) {
            return event.getFirstName().trim();
        }
        String email = event.getEmail();
        if (email == null || !email.contains("@")) {
            return "there";
        }
        return email.substring(0, email.indexOf('@'));
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -pl media-service -am test -Dtest=NotificationServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (4 tests)

- [ ] **Step 5: Stop for review.** Suggested commit: `feat(notification): route welcome through the dispatcher (NOTIF-101)`

---

### Task 10: Full verification, smoke test and roadmap update

**Files:**
- Modify: `docs/superpowers/plans/2026-09-30-notification-epic-roadmap.md` (Story 1 section)

- [ ] **Step 1: Run both suites**

Run: `mvn -q -pl media-service,user-service -am test`
Expected: PASS, with no failures in the existing `AuctionRealtime*`, `StompInboundGuard*` and `GatewayUserHandshakeHandler*` tests.

- [ ] **Step 2: Run the Postgres IT (Docker required)**

Run: `mvn -q -pl media-service -am test -Dtest=NotificationPersistencePostgresIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS

- [ ] **Step 3: Manual smoke test with docker-compose** (full stack running, Mailtrap configured)
  1. Rebuild and restart media-service and user-service. The media log shows Liquibase applying `06-notification-pipeline.sql` and three listener containers subscribing with group `media-projection-group`.
  2. Register a new user and verify the OTP. In `media_db`: `SELECT type, dedup_key, channel FROM media_notifications ORDER BY created_at DESC LIMIT 1;` → `USER_REGISTERED | WELCOME | IN_APP`. `SELECT template_name, notification_id, status FROM media_email_logs ORDER BY created_at DESC LIMIT 1;` → `WELCOME_EMAIL_EN | <that notification id> | SENT`. Mailtrap shows the welcome email.
  3. Push fan-out: `kafka-console-consumer --bootstrap-server localhost:9092 --topic user-notification-push-topic --from-beginning` shows one `{"userId":…,"message":{"type":"NOTIFICATION","notification":{…},"unreadCount":1}}` for that welcome. (The browser socket path is exercised end-to-end in Story 8. A new user cannot be connected before their welcome is sent.)
  4. Idempotency: re-publish the same `user-registered-topic` record (copy its value with `kafka-console-consumer --property print.value=true`, then send it with `kafka-console-producer`). No new `media_notifications` row, no new email log, and no new push message appear.
  5. Language: the user-service log shows `POST /api/v1/users/internal/notification-preferences` for each welcome. Set `language = 'vi'` on the new user's `user_preferences` row in `user_db`, delete their `WELCOME` row from `media_notifications`, and re-publish the event. The new email log uses `WELCOME_EMAIL_VI`.
  6. Projection: place a bid on an active auction (Swagger `POST /api/v1/bids`). `SELECT * FROM media_auction_participants;` shows the bidder, and `media_auctions` has the title and end time.

- [ ] **Step 4: Update the roadmap's Story 1 section** so it matches what was built. In `docs/superpowers/plans/2026-09-30-notification-epic-roadmap.md`:
  - Replace the bullet `Create domain/entity/AuctionRef.java, domain/entity/AuctionParticipant.java (+ @IdClass), repository/AuctionRefRepository.java, repository/AuctionParticipantRepository.java (…)` with: `Create repository/NotificationInboxRepository.java and repository/AuctionProjectionRepository.java (JDBC; upserts are order-tolerant) and the projection/AuctionRef record.`
  - In the `RecipientDirectory` bullet, replace `feign/UserServiceClient.getProfiles (POST /api/v1/users/internal/profiles; confirm …)` with `feign/UserServiceClient.getNotificationPreferences (new POST /api/v1/users/internal/notification-preferences, max 100 IDs per call, chunked)`.
  - In the `AuctionProjectionConsumer` bullet, replace `(shared group)` with `(own stable group media-projection-group: sharing media-service-group on bid-placed-topic would split partitions with NotificationKafkaConsumer)`.
  - Tick tasks 1.1–1.7 (and 1.4a).
  - Under "Risks carried forward", add: `**Projection lag vs. handlers.** The projection and notification handlers consume bid-placed-topic in different groups, so an auction-ended handler (Story 4) could read participants before the final bid is projected. Anti-sniping keeps the last bid ≥ minutes before the end, so this is unlikely. If it shows up, have the ended handler also include AuctionEndedEvent.winnerId explicitly.`

- [ ] **Step 5: Stop for review.** Suggested commit: `docs(notification): record NOTIF-101 design refinements in roadmap`
