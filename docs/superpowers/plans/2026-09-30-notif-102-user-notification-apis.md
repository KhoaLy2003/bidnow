# NOTIF-102 — User Notification APIs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Signed-in users can list, filter, search, read/unread and soft-delete their own notifications through `GET|PUT|DELETE /api/v1/notifications/**` on the gateway. Every change to their unread count is pushed live as an `UNREAD_COUNT` message, so other tabs and devices stay in sync.

**Architecture:**
- A new `NotificationController` in media-service delegates to `UserNotificationService`. The service reads and writes `media_notifications` through JPA (`NotificationRepository`):
  - **Lists:** a `Specification`, built with the shared `SpecificationBuilder`, scoped to the caller and to non-deleted rows.
  - **Bulk updates:** JPQL `@Modifying` queries.
- **Unread-count push:** after a change that can move the unread count, the service counts inside the transaction and registers an `AfterCommit` push through Story 1's `UserNotificationPushPublisher`, using a new `UNREAD_COUNT` message type.
- **Gateway:** the `media-service` route gains `/api/v1/notifications/**`.

**Tech Stack:** Java 17, Spring Boot 3.2.4, Spring Data JPA (Specifications, `@Modifying` JPQL), Hibernate 6, PostgreSQL + Liquibase, Spring Cloud Gateway, JUnit 5, Mockito, AssertJ, standalone MockMvc, `@DataJpaTest` + Testcontainers (for the `*PostgresIT` only).

**Spec:** `docs/superpowers/plans/2026-09-30-notification-epic-roadmap.md`: read Story 2, plus Decisions 6 and 10. It builds on the NOTIF-101 code, which is in the working tree: `Notification.dedupKey/deletedAt`, `NotificationResponse`, `UserNotificationMessage`, `UserNotificationPushPublisher`, `AfterCommit`, `ClockConfig`, `NotificationDispatcher`.

This plan refines Story 2 in five places. Task 6 records each one in the roadmap.
1. **Query DTO.** The query DTO is `NotificationQuery` (page/size validated, like bidding's `BidHistoryQuery`) instead of `NotificationCriteria`, and `types` binds to `List<NotificationType>`, so an unknown type returns 400.
2. **Security config.** `SecurityConfig` needs no change. media-service already requires authentication for `anyRequest()`.
3. **`delete-read` sends no push.** Deleting read notifications cannot change the unread count, so only `get`/`read`/`unread`/`mark-all-read`/`delete`/`delete-all` push `UNREAD_COUNT`, and only when something changed.
4. **Missing user header.** A missing `X-User-Id` becomes 401 inside the controller as well. In production the gateway and Spring Security already reject it, but the standalone MockMvc test needs the check in code.
5. **API docs.** Regenerating `media-service/api-docs.json` needs the running service, so it is a manual step (Task 6). The Postman collection moves to Story 9 (docs).

## Global Constraints

- Every endpoint is scoped to the caller from `@AuthenticatedUserId` (`X-User-Id`). Another user's notification ID returns **404, not 403**, so IDs cannot be probed.
- Soft delete only: set `deleted_at`, never `DELETE` rows. Deleted rows never appear in lists, counts or single reads.
- "Read" means `read_at IS NOT NULL`. The `status` column is not changed by these APIs.
- Pagination: `page` is 0-based with default 0. `size` defaults to 20, with minimum 1 and maximum 100 (101 → 400). Sort is `createdAt DESC`, then `id DESC`, and is not client-configurable.
- Responses are `ResponseEntity<BaseResponse<T>>`. Lists are `BaseResponse<PageResponse<NotificationResponse>>`. Errors are `BaseException` subclasses rendered as common `ErrorResponse`. An invalid UUID or enum is 400 `INVALID_INPUT`.
- Push: STOMP user destination `/queue/notifications`. The unread-count message is `{"type":"UNREAD_COUNT","notification":null,"unreadCount":n}`. It is published after commit and never fails the request.
- Timestamps use the injected `Clock` bean (`ClockConfig`, system default zone, matching `BaseEntity`). The `from`/`to` query params are ISO-8601 offset date-times, converted to that zone.
- Internal endpoints are never routed. `/api/v1/**/internal/**` stays blocked at the gateway.
- Maven runs from `backend/`. Default unit tests must not need Docker. `*PostgresIT` tests run only when named explicitly.
- **Agents do not commit.** Each task ends with a review stop. The user commits with the suggested conventional-commit message.

## File Structure

**media-service** (`backend/media-service/src/main/java/com/bidnow/media/…`)
- Modify `dto/response/NotificationResponse.java`: add a `static from(Notification)` factory, where `read` is derived from `readAt`.
- Modify `notification/NotificationDispatcher.java`: use `NotificationResponse.from` and drop its private mapper.
- Modify `realtime/UserNotificationMessage.java`: add `UNREAD_COUNT` and a `static unreadCount(long)` factory.
- Create `dto/response/UnreadCountResponse.java` (`record(long count)`) and `dto/response/BulkUpdateResponse.java` (`record(int updated)`).
- Create `dto/request/NotificationQuery.java`: paging and filter params.
- Modify `repository/NotificationRepository.java`: add `JpaSpecificationExecutor`, scoped finders, the unread count and bulk updates.
- Create `repository/NotificationSpecifications.java`: the inbox `Specification`.
- Create `service/UserNotificationService.java` and `service/impl/UserNotificationServiceImpl.java`.
- Create `controller/NotificationController.java`.
- Create `exception/MediaExceptionHandler.java`: 400 for type-mismatched path and query params, following bidding's `BiddingExceptionHandler`.
- Tests under `src/test/java/com/bidnow/media/…`, listed per task. That includes `repository/RepositoryTestConfig.java` for the JPA Postgres IT.

**api-gateway**
- Modify `backend/api-gateway/src/main/resources/application.yml`: add the `media-service` route predicate.
- Modify `backend/api-gateway/src/test/java/com/bidnow/gateway/GatewayRoutesTest.java`.

**docs**
- Modify `docs/superpowers/plans/2026-09-30-notification-epic-roadmap.md`: record the Story 2 refinements and tick its tasks (Task 6).

---

### Task 1: Shared response mapping and the UNREAD_COUNT message

**Files:**
- Modify: `backend/media-service/src/main/java/com/bidnow/media/dto/response/NotificationResponse.java`
- Modify: `backend/media-service/src/main/java/com/bidnow/media/notification/NotificationDispatcher.java`
- Modify: `backend/media-service/src/main/java/com/bidnow/media/realtime/UserNotificationMessage.java`
- Create: `backend/media-service/src/main/java/com/bidnow/media/dto/response/UnreadCountResponse.java`
- Create: `backend/media-service/src/main/java/com/bidnow/media/dto/response/BulkUpdateResponse.java`
- Test: `backend/media-service/src/test/java/com/bidnow/media/dto/response/NotificationResponseTest.java`
- Test: `backend/media-service/src/test/java/com/bidnow/media/realtime/UserNotificationPushJsonTest.java` (add one test)

**Interfaces:**
- Consumes: `Notification` entity (fields `id, type, title, message, actionUrl, auctionId, metadata, readAt, createdAt`), `NotificationResponse` (Lombok builder, `boolean read`), `record UserNotificationMessage(String type, NotificationResponse notification, long unreadCount)` with `NOTIFICATION` and `notification(NotificationResponse, long)`.
- Produces: `NotificationResponse.from(Notification): NotificationResponse`, where `read = readAt != null` and `type = type.name()`.
- Produces: `UserNotificationMessage.UNREAD_COUNT = "UNREAD_COUNT"` and `UserNotificationMessage.unreadCount(long): UserNotificationMessage` (with `notification == null`).
- Produces: `record UnreadCountResponse(long count)` and `record BulkUpdateResponse(int updated)` in `com.bidnow.media.dto.response`.

- [ ] **Step 1: Write the failing tests**

`NotificationResponseTest.java`:

```java
package com.bidnow.media.dto.response;

import com.bidnow.media.domain.entity.Notification;
import com.bidnow.media.domain.enums.NotificationChannel;
import com.bidnow.media.domain.enums.NotificationType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationResponseTest {

    private static Notification notification(LocalDateTime readAt) {
        Notification n = Notification.builder()
                .id(UUID.randomUUID()).userId(UUID.randomUUID()).type(NotificationType.AUCTION_WON)
                .channel(NotificationChannel.IN_APP).title("You won").message("You won Vintage Watch")
                .actionUrl("/auctions/x").auctionId(UUID.randomUUID()).metadata(Map.of("amount", "105.00"))
                .dedupKey("AUCTION_WON:x").readAt(readAt)
                .build();
        n.setCreatedAt(LocalDateTime.of(2026, 10, 1, 10, 0));
        return n;
    }

    @Test
    void from_mapsFields() {
        Notification n = notification(null);

        NotificationResponse response = NotificationResponse.from(n);

        assertThat(response.getId()).isEqualTo(n.getId());
        assertThat(response.getType()).isEqualTo("AUCTION_WON");
        assertThat(response.getTitle()).isEqualTo("You won");
        assertThat(response.getMessage()).isEqualTo("You won Vintage Watch");
        assertThat(response.getActionUrl()).isEqualTo("/auctions/x");
        assertThat(response.getAuctionId()).isEqualTo(n.getAuctionId());
        assertThat(response.getMetadata()).containsEntry("amount", "105.00");
        assertThat(response.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 10, 0));
        assertThat(response.isRead()).isFalse();
    }

    @Test
    void from_readAtSet_isRead() {
        assertThat(NotificationResponse.from(notification(LocalDateTime.of(2026, 10, 1, 11, 0))).isRead()).isTrue();
    }
}
```

Append to `UserNotificationPushJsonTest` (it already has the `mapper` field and imports `JsonNode`):

```java
    @Test
    void unreadCountMessage_hasContractShape() throws Exception {
        JsonNode root = mapper.readTree(mapper.writeValueAsString(UserNotificationMessage.unreadCount(4)));

        assertThat(root.get("type").asText()).isEqualTo("UNREAD_COUNT");
        assertThat(root.get("unreadCount").asLong()).isEqualTo(4);
        assertThat(root.get("notification").isNull()).isTrue();
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q -pl media-service -am test -Dtest='NotificationResponseTest,UserNotificationPushJsonTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation FAILURE, because `NotificationResponse.from` and `UserNotificationMessage.unreadCount` are not defined.

- [ ] **Step 3: Implement**

In `NotificationResponse.java`, add the import `com.bidnow.media.domain.entity.Notification` and this method inside the class:

```java
    /** Maps a stored notification; {@code read} is derived from {@code readAt}. */
    public static NotificationResponse from(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType().name())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .actionUrl(notification.getActionUrl())
                .auctionId(notification.getAuctionId())
                .metadata(notification.getMetadata())
                .read(notification.getReadAt() != null)
                .createdAt(notification.getCreatedAt())
                .build();
    }
```

Replace `UserNotificationMessage.java` with:

```java
package com.bidnow.media.realtime;

import com.bidnow.media.dto.response.NotificationResponse;

/**
 * STOMP envelope on {@code /user/queue/notifications}: {@code {type, notification, unreadCount}}.
 * {@code NOTIFICATION} carries a new notification; {@code UNREAD_COUNT} only syncs the badge (notification is null).
 */
public record UserNotificationMessage(String type, NotificationResponse notification, long unreadCount) {

    public static final String NOTIFICATION = "NOTIFICATION";
    public static final String UNREAD_COUNT = "UNREAD_COUNT";

    public static UserNotificationMessage notification(NotificationResponse notification, long unreadCount) {
        return new UserNotificationMessage(NOTIFICATION, notification, unreadCount);
    }

    public static UserNotificationMessage unreadCount(long unreadCount) {
        return new UserNotificationMessage(UNREAD_COUNT, null, unreadCount);
    }
}
```

In `NotificationDispatcher.java`, replace the call `toResponse(row)` in `dispatchAll` with `NotificationResponse.from(row)`, and delete the private `toResponse(Notification row)` method. Nothing else in that file changes. A freshly inserted row has `readAt == null`, so `read` stays `false`.

Create `UnreadCountResponse.java`:

```java
package com.bidnow.media.dto.response;

public record UnreadCountResponse(long count) {
}
```

Create `BulkUpdateResponse.java`:

```java
package com.bidnow.media.dto.response;

/** Result of a bulk inbox operation: how many notifications were changed. */
public record BulkUpdateResponse(int updated) {
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q -pl media-service -am test -Dtest='NotificationResponseTest,UserNotificationPushJsonTest,NotificationDispatcherTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS. `NotificationResponseTest` passes 2 tests and `UserNotificationPushJsonTest` passes 3 (2 existing plus the new one). All 13 `NotificationDispatcherTest` tests still pass, so the dispatcher refactor didn't change behaviour.

- [ ] **Step 5: Stop for review.** Suggested commit: `refactor(notification): share notification response mapping and add unread-count message (NOTIF-102)`

---

### Task 2: Inbox queries (repository + specification)

**Files:**
- Modify: `backend/media-service/src/main/java/com/bidnow/media/repository/NotificationRepository.java`
- Create: `backend/media-service/src/main/java/com/bidnow/media/dto/request/NotificationQuery.java`
- Create: `backend/media-service/src/main/java/com/bidnow/media/repository/NotificationSpecifications.java`
- Create: `backend/media-service/src/test/java/com/bidnow/media/repository/RepositoryTestConfig.java`
- Test: `backend/media-service/src/test/java/com/bidnow/media/repository/NotificationRepositoryPostgresIT.java`

**Interfaces:**
- Consumes: `Notification` entity (`userId, type, title, message, readAt, deletedAt, createdAt` plus inherited `updatedAt`), `NotificationType`, and the common `SpecificationBuilder`/`SearchOperator`.
- Produces: `NotificationRepository extends JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification>` with:
  - `Optional<Notification> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId)`
  - `long countByUserIdAndReadAtIsNullAndDeletedAtIsNull(UUID userId)`
  - `int markAllRead(UUID userId, LocalDateTime now)`
  - `int softDeleteAll(UUID userId, LocalDateTime now)`
  - `int softDeleteRead(UUID userId, LocalDateTime now)`
- Produces: `NotificationQuery`, a Lombok `@Data` class with fields:
  - `int page = 0` (`@Min(0)`)
  - `int size = 20` (`@Min(1) @Max(100)`)
  - `Boolean read`
  - `List<NotificationType> types`
  - `OffsetDateTime from`, `OffsetDateTime to` (ISO date-time)
  - `String search` (`@Size(max = 100)`)
  - `Pageable toPageable()`, sorted `createdAt DESC, id DESC`
- Produces: `NotificationSpecifications.inbox(UUID userId, NotificationQuery query, ZoneId zone): Specification<Notification>`.

- [ ] **Step 1: Write the query DTO and test config (needed for the IT to compile)**

`NotificationQuery.java`:

```java
package com.bidnow.media.dto.request;

import com.bidnow.media.domain.enums.NotificationType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;
import java.util.List;

/** Inbox paging and filters. Newest first; id breaks ties so pages are stable. */
@Data
public class NotificationQuery {

    @Min(value = 0, message = "page must be >= 0")
    private int page = 0;

    @Min(value = 1, message = "size must be between 1 and 100")
    @Max(value = 100, message = "size must be between 1 and 100")
    private int size = 20;

    /** true = only read, false = only unread, null = both. */
    private Boolean read;

    private List<NotificationType> types;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime to;

    @Size(max = 100, message = "search must be at most 100 characters")
    private String search;

    public Pageable toPageable() {
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }
}
```

`RepositoryTestConfig.java` (test sources). `@DataJpaTest` looks for the nearest `@SpringBootConfiguration` walking up from the test's package. This one sits in `com.bidnow.media.repository`, so it is found before `MediaApplication`, whose `@EnableFeignClients` would need beans a JPA slice doesn't create:

```java
package com.bidnow.media.repository;

import com.bidnow.media.domain.entity.Notification;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Minimal boot configuration for JPA slice tests in this package (avoids MediaApplication's Feign/Eureka setup).
 * @DataJpaTest supplies the JPA/Liquibase/DataSource auto-configuration itself.
 */
@SpringBootConfiguration
@EntityScan(basePackageClasses = Notification.class)
@EnableJpaRepositories(basePackageClasses = NotificationRepository.class)
class RepositoryTestConfig {
}
```

- [ ] **Step 2: Write the failing Postgres integration test**

`NotificationRepositoryPostgresIT.java`:

```java
package com.bidnow.media.repository;

import com.bidnow.bdd.container.PostgresContainerSupport;
import com.bidnow.media.domain.entity.Notification;
import com.bidnow.media.domain.enums.NotificationChannel;
import com.bidnow.media.domain.enums.NotificationStatus;
import com.bidnow.media.domain.enums.NotificationType;
import com.bidnow.media.dto.request.NotificationQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA queries against real Postgres with the real Liquibase changelog (Testcontainers, needs Docker).
 * Not part of the default test run - execute explicitly:
 * mvn -q -pl media-service -am test -Dtest=NotificationRepositoryPostgresIT -Dsurefire.failIfNoSpecifiedTests=false
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NotificationRepositoryPostgresIT {

    private static final ZoneId ZONE = ZoneOffset.UTC;
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 1, 10, 0);

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        PostgresContainerSupport.properties().forEach((key, value) -> registry.add(key, () -> value));
    }

    @Autowired
    private NotificationRepository repository;
    @Autowired
    private TestEntityManager em;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    private Notification unreadWon;
    private Notification readLost;
    private Notification deletedWon;

    private Notification persist(UUID userId, NotificationType type, String title, String message,
                                 LocalDateTime createdAt, LocalDateTime readAt, LocalDateTime deletedAt) {
        // no id: the entity uses @GeneratedValue, and persist() rejects a pre-set id as a detached entity
        Notification n = Notification.builder()
                .userId(userId).type(type).channel(NotificationChannel.IN_APP)
                .status(NotificationStatus.SENT).title(title).message(message)
                .dedupKey(type + ":" + UUID.randomUUID()).readAt(readAt).deletedAt(deletedAt)
                .build();
        em.persist(n);
        em.flush();
        // BaseEntity @PrePersist stamps now(); pin created_at so date filters are deterministic
        em.getEntityManager().createNativeQuery("UPDATE media_notifications SET created_at = ?1 WHERE id = ?2")
                .setParameter(1, createdAt).setParameter(2, n.getId()).executeUpdate();
        em.clear();
        return repository.findById(n.getId()).orElseThrow();
    }

    @BeforeEach
    void setUp() {
        unreadWon = persist(alice, NotificationType.AUCTION_WON, "You won", "You won Vintage Watch", T0, null, null);
        readLost = persist(alice, NotificationType.AUCTION_LOST, "Auction lost", "Someone outbid you on Camera",
                T0.plusHours(1), T0.plusHours(2), null);
        deletedWon = persist(alice, NotificationType.AUCTION_WON, "You won", "Deleted one", T0.plusHours(2), null, T0);
        persist(bob, NotificationType.AUCTION_WON, "You won", "Bob's watch", T0.plusHours(3), null, null);
    }

    private List<UUID> inboxIds(NotificationQuery query) {
        Page<Notification> page = repository.findAll(NotificationSpecifications.inbox(alice, query, ZONE), query.toPageable());
        return page.getContent().stream().map(Notification::getId).toList();
    }

    @Test
    void inbox_isScopedToUserAndExcludesDeleted_newestFirst() {
        assertThat(inboxIds(new NotificationQuery())).containsExactly(readLost.getId(), unreadWon.getId());
    }

    @Test
    void inbox_filtersCombine() {
        NotificationQuery unread = new NotificationQuery();
        unread.setRead(false);
        assertThat(inboxIds(unread)).containsExactly(unreadWon.getId());

        NotificationQuery read = new NotificationQuery();
        read.setRead(true);
        assertThat(inboxIds(read)).containsExactly(readLost.getId());

        NotificationQuery byType = new NotificationQuery();
        byType.setTypes(List.of(NotificationType.AUCTION_LOST));
        assertThat(inboxIds(byType)).containsExactly(readLost.getId());

        NotificationQuery byDate = new NotificationQuery();
        byDate.setFrom(OffsetDateTime.of(T0.plusMinutes(30), ZoneOffset.UTC));
        byDate.setTo(OffsetDateTime.of(T0.plusHours(5), ZoneOffset.UTC));
        assertThat(inboxIds(byDate)).containsExactly(readLost.getId());

        NotificationQuery bySearch = new NotificationQuery();
        bySearch.setSearch("CAMERA");
        assertThat(inboxIds(bySearch)).containsExactly(readLost.getId());

        NotificationQuery combined = new NotificationQuery();
        combined.setRead(false);
        combined.setTypes(List.of(NotificationType.AUCTION_WON));
        combined.setSearch("watch");
        assertThat(inboxIds(combined)).containsExactly(unreadWon.getId());
    }

    @Test
    void findByIdAndUserId_ignoresOtherUsersAndDeletedRows() {
        assertThat(repository.findByIdAndUserIdAndDeletedAtIsNull(unreadWon.getId(), alice)).isPresent();
        assertThat(repository.findByIdAndUserIdAndDeletedAtIsNull(unreadWon.getId(), bob)).isEmpty();
        assertThat(repository.findByIdAndUserIdAndDeletedAtIsNull(deletedWon.getId(), alice)).isEmpty();
    }

    @Test
    void countUnread_ignoresReadAndDeleted() {
        assertThat(repository.countByUserIdAndReadAtIsNullAndDeletedAtIsNull(alice)).isEqualTo(1);
    }

    @Test
    void markAllRead_touchesOnlyCallersUnreadNonDeletedRows() {
        LocalDateTime now = T0.plusDays(1);

        int updated = repository.markAllRead(alice, now);

        assertThat(updated).isEqualTo(1);
        assertThat(repository.findById(unreadWon.getId()).orElseThrow().getReadAt()).isEqualTo(now);
        assertThat(repository.findById(readLost.getId()).orElseThrow().getReadAt()).isEqualTo(T0.plusHours(2));
        assertThat(repository.findById(deletedWon.getId()).orElseThrow().getReadAt()).isNull();
        assertThat(repository.countByUserIdAndReadAtIsNullAndDeletedAtIsNull(bob)).isEqualTo(1);
    }

    @Test
    void softDeleteRead_deletesOnlyReadRows() {
        LocalDateTime now = T0.plusDays(1);

        int deleted = repository.softDeleteRead(alice, now);

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findById(readLost.getId()).orElseThrow().getDeletedAt()).isEqualTo(now);
        assertThat(repository.findById(unreadWon.getId()).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    void softDeleteAll_deletesOnlyCallersNonDeletedRows() {
        LocalDateTime now = T0.plusDays(1);

        int deleted = repository.softDeleteAll(alice, now);

        assertThat(deleted).isEqualTo(2);
        assertThat(repository.findById(deletedWon.getId()).orElseThrow().getDeletedAt()).isEqualTo(T0);
        assertThat(inboxIds(new NotificationQuery())).isEmpty();
        assertThat(repository.countByUserIdAndReadAtIsNullAndDeletedAtIsNull(bob)).isEqualTo(1);
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `mvn -q -pl media-service -am test -Dtest=NotificationRepositoryPostgresIT -Dsurefire.failIfNoSpecifiedTests=false` (Docker must be running)
Expected: compilation FAILURE, because `NotificationSpecifications` and the new repository methods do not exist.

- [ ] **Step 4: Implement.** Replace `NotificationRepository.java` with:

```java
package com.bidnow.media.repository;

import com.bidnow.media.domain.entity.Notification;
import com.bidnow.media.domain.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification> {
    Page<Notification> findByUserId(UUID userId, Pageable pageable);

    long countByUserIdAndStatus(UUID userId, NotificationStatus status);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    Optional<Notification> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    long countByUserIdAndReadAtIsNullAndDeletedAtIsNull(UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Notification n set n.readAt = :now, n.updatedAt = :now
            where n.userId = :userId and n.readAt is null and n.deletedAt is null""")
    int markAllRead(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Notification n set n.deletedAt = :now, n.updatedAt = :now
            where n.userId = :userId and n.deletedAt is null""")
    int softDeleteAll(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Notification n set n.deletedAt = :now, n.updatedAt = :now
            where n.userId = :userId and n.readAt is not null and n.deletedAt is null""")
    int softDeleteRead(@Param("userId") UUID userId, @Param("now") LocalDateTime now);
}
```

Create `NotificationSpecifications.java`:

```java
package com.bidnow.media.repository;

import com.bidnow.common.specification.SearchOperator;
import com.bidnow.common.specification.SpecificationBuilder;
import com.bidnow.media.domain.entity.Notification;
import com.bidnow.media.dto.request.NotificationQuery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

/** The caller's inbox: own, non-deleted notifications narrowed by the query's optional filters. */
public final class NotificationSpecifications {

    private NotificationSpecifications() {
    }

    /**
     * @param zone zone of the stored (zone-less) created_at timestamps; from/to are converted into it
     */
    public static Specification<Notification> inbox(UUID userId, NotificationQuery query, ZoneId zone) {
        SpecificationBuilder<Notification> builder = SpecificationBuilder.<Notification>forEntity()
                .with("userId", SearchOperator.EQUAL, userId)
                .withIsNull("deletedAt");

        if (Boolean.TRUE.equals(query.getRead())) {
            builder.withIsNotNull("readAt");
        } else if (Boolean.FALSE.equals(query.getRead())) {
            builder.withIsNull("readAt");
        }
        builder.withInIfPresent("type", query.getTypes());
        builder.withIfPresent("createdAt", SearchOperator.GREATER_THAN_OR_EQUAL, toLocal(query.getFrom(), zone));
        builder.withIfPresent("createdAt", SearchOperator.LESS_THAN, toLocal(query.getTo(), zone));

        if (StringUtils.hasText(query.getSearch())) {
            String pattern = "%" + query.getSearch().trim().toLowerCase() + "%";
            builder.orGroup(or -> or.withLike("title", pattern).withLike("message", pattern));
        }
        return builder.build();
    }

    private static LocalDateTime toLocal(OffsetDateTime value, ZoneId zone) {
        return value == null ? null : value.atZoneSameInstant(zone).toLocalDateTime();
    }
}
```

- [ ] **Step 5: Run the integration test to verify it passes**

Run: `mvn -q -pl media-service -am test -Dtest=NotificationRepositoryPostgresIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (7 tests).

If the context fails to start because `application.yml` placeholders (`${DB_URL}` etc.) are resolved before `@DynamicPropertySource` wins, the dynamic properties already override `spring.datasource.*`, so the failure is somewhere else. Read the root cause. If it is an unrelated auto-configuration (for example, Kafka or mail), add `properties = {"spring.kafka.bootstrap-servers=localhost:0"}` or the matching exclusion to `@DataJpaTest`, and record it in the report.

- [ ] **Step 6: Run the default suite (no Docker needed)**

Run: `mvn -q -pl media-service -am test`
Expected: PASS. `*PostgresIT` classes are not picked up.

- [ ] **Step 7: Stop for review.** Suggested commit: `feat(notification): add inbox specification and bulk inbox updates (NOTIF-102)`

---

### Task 3: UserNotificationService

**Files:**
- Create: `backend/media-service/src/main/java/com/bidnow/media/service/UserNotificationService.java`
- Create: `backend/media-service/src/main/java/com/bidnow/media/service/impl/UserNotificationServiceImpl.java`
- Test: `backend/media-service/src/test/java/com/bidnow/media/service/impl/UserNotificationServiceImplTest.java`

**Interfaces:**
- Consumes: Task 2 repository methods and `NotificationSpecifications.inbox`; Task 1 `NotificationResponse.from`, `UserNotificationMessage.unreadCount`; Story 1's `UserNotificationPushPublisher.publish(UUID, UserNotificationMessage)`, `AfterCommit.run(Runnable)` and the `Clock` bean; common `PageResponse.of(Page)`, `NotFoundException(String, String)`, `ErrorCodes.NOT_FOUND`.
- Produces `UserNotificationService` with:
  - `PageResponse<NotificationResponse> list(UUID userId, NotificationQuery query)`
  - `long unreadCount(UUID userId)`
  - `NotificationResponse get(UUID userId, UUID id)`
  - `NotificationResponse markRead(UUID userId, UUID id)`
  - `NotificationResponse markUnread(UUID userId, UUID id)`
  - `int markAllRead(UUID userId)`
  - `void delete(UUID userId, UUID id)`
  - `int deleteAll(UUID userId)`
  - `int deleteRead(UUID userId)`
- Behaviour:
  - A missing or other user's or deleted ID gives `NotFoundException`.
  - `get` marks the notification read.
  - An `UNREAD_COUNT` push is registered after commit only when the unread count can have changed.

- [ ] **Step 1: Write the failing test**

```java
package com.bidnow.media.service.impl;

import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.media.domain.entity.Notification;
import com.bidnow.media.domain.enums.NotificationChannel;
import com.bidnow.media.domain.enums.NotificationType;
import com.bidnow.media.dto.request.NotificationQuery;
import com.bidnow.media.dto.response.NotificationResponse;
import com.bidnow.media.kafka.UserNotificationPushPublisher;
import com.bidnow.media.realtime.UserNotificationMessage;
import com.bidnow.media.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserNotificationServiceImplTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 10, 0);
    private static final LocalDateTime EARLIER = NOW.minusHours(1);

    @Mock
    private NotificationRepository repository;
    @Mock
    private UserNotificationPushPublisher pushPublisher;

    private UserNotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserNotificationServiceImpl(repository, pushPublisher,
                Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC));
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private static void commit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private static Notification notification(LocalDateTime readAt) {
        Notification n = Notification.builder()
                .id(ID).userId(ALICE).type(NotificationType.AUCTION_WON).channel(NotificationChannel.IN_APP)
                .title("You won").message("You won Vintage Watch").dedupKey("AUCTION_WON:x").readAt(readAt)
                .build();
        n.setCreatedAt(EARLIER);
        return n;
    }

    private UserNotificationMessage pushedMessage() {
        ArgumentCaptor<UserNotificationMessage> message = ArgumentCaptor.forClass(UserNotificationMessage.class);
        verify(pushPublisher).publish(eq(ALICE), message.capture());
        return message.getValue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_queriesTheCallersInboxNewestFirst() {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(notification(null))));

        PageResponse<NotificationResponse> page = service.list(ALICE, new NotificationQuery());

        assertThat(page.getData()).extracting(NotificationResponse::getId).containsExactly(ID);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void unreadCount_delegates() {
        when(repository.countByUserIdAndReadAtIsNullAndDeletedAtIsNull(ALICE)).thenReturn(3L);

        assertThat(service.unreadCount(ALICE)).isEqualTo(3);
    }

    @Test
    void get_notFoundForCaller_throws() {
        when(repository.findByIdAndUserIdAndDeletedAtIsNull(ID, ALICE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(ALICE, ID)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void get_unread_marksReadAndPushesCountAfterCommit() {
        Notification n = notification(null);
        when(repository.findByIdAndUserIdAndDeletedAtIsNull(ID, ALICE)).thenReturn(Optional.of(n));
        when(repository.countByUserIdAndReadAtIsNullAndDeletedAtIsNull(ALICE)).thenReturn(2L);

        NotificationResponse response = service.get(ALICE, ID);

        assertThat(n.getReadAt()).isEqualTo(NOW);
        assertThat(response.isRead()).isTrue();
        verifyNoInteractions(pushPublisher);
        commit();
        UserNotificationMessage message = pushedMessage();
        assertThat(message.type()).isEqualTo(UserNotificationMessage.UNREAD_COUNT);
        assertThat(message.unreadCount()).isEqualTo(2);
    }

    @Test
    void get_alreadyRead_keepsReadAtAndDoesNotPush() {
        Notification n = notification(EARLIER);
        when(repository.findByIdAndUserIdAndDeletedAtIsNull(ID, ALICE)).thenReturn(Optional.of(n));

        service.get(ALICE, ID);
        commit();

        assertThat(n.getReadAt()).isEqualTo(EARLIER);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verifyNoInteractions(pushPublisher);
    }

    @Test
    void markUnread_read_clearsReadAtAndPushes() {
        Notification n = notification(EARLIER);
        when(repository.findByIdAndUserIdAndDeletedAtIsNull(ID, ALICE)).thenReturn(Optional.of(n));
        when(repository.countByUserIdAndReadAtIsNullAndDeletedAtIsNull(ALICE)).thenReturn(1L);

        NotificationResponse response = service.markUnread(ALICE, ID);
        commit();

        assertThat(n.getReadAt()).isNull();
        assertThat(response.isRead()).isFalse();
        assertThat(pushedMessage().unreadCount()).isEqualTo(1);
    }

    @Test
    void markUnread_alreadyUnread_doesNotPush() {
        when(repository.findByIdAndUserIdAndDeletedAtIsNull(ID, ALICE)).thenReturn(Optional.of(notification(null)));

        service.markUnread(ALICE, ID);
        commit();

        verifyNoInteractions(pushPublisher);
    }

    @Test
    void markAllRead_returnsCountAndPushes() {
        when(repository.markAllRead(ALICE, NOW)).thenReturn(4);
        when(repository.countByUserIdAndReadAtIsNullAndDeletedAtIsNull(ALICE)).thenReturn(0L);

        assertThat(service.markAllRead(ALICE)).isEqualTo(4);
        commit();

        assertThat(pushedMessage().unreadCount()).isZero();
    }

    @Test
    void markAllRead_nothingUnread_doesNotPush() {
        when(repository.markAllRead(ALICE, NOW)).thenReturn(0);

        assertThat(service.markAllRead(ALICE)).isZero();
        commit();

        verifyNoInteractions(pushPublisher);
    }

    @Test
    void delete_unread_softDeletesAndPushes() {
        Notification n = notification(null);
        when(repository.findByIdAndUserIdAndDeletedAtIsNull(ID, ALICE)).thenReturn(Optional.of(n));
        when(repository.countByUserIdAndReadAtIsNullAndDeletedAtIsNull(ALICE)).thenReturn(0L);

        service.delete(ALICE, ID);
        commit();

        assertThat(n.getDeletedAt()).isEqualTo(NOW);
        assertThat(pushedMessage().unreadCount()).isZero();
    }

    @Test
    void delete_read_softDeletesWithoutPush() {
        Notification n = notification(EARLIER);
        when(repository.findByIdAndUserIdAndDeletedAtIsNull(ID, ALICE)).thenReturn(Optional.of(n));

        service.delete(ALICE, ID);
        commit();

        assertThat(n.getDeletedAt()).isEqualTo(NOW);
        verifyNoInteractions(pushPublisher);
    }

    @Test
    void deleteAll_returnsCountAndPushes() {
        when(repository.softDeleteAll(ALICE, NOW)).thenReturn(3);
        when(repository.countByUserIdAndReadAtIsNullAndDeletedAtIsNull(ALICE)).thenReturn(0L);

        assertThat(service.deleteAll(ALICE)).isEqualTo(3);
        commit();

        assertThat(pushedMessage().unreadCount()).isZero();
    }

    @Test
    void deleteRead_returnsCountWithoutPush() {
        when(repository.softDeleteRead(ALICE, NOW)).thenReturn(2);

        assertThat(service.deleteRead(ALICE)).isEqualTo(2);
        commit();

        verify(repository, never()).countByUserIdAndReadAtIsNullAndDeletedAtIsNull(any());
        verifyNoInteractions(pushPublisher);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -pl media-service -am test -Dtest=UserNotificationServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation FAILURE, because `UserNotificationServiceImpl` does not exist.

- [ ] **Step 3: Implement**

`UserNotificationService.java`:

```java
package com.bidnow.media.service;

import com.bidnow.common.dto.PageResponse;
import com.bidnow.media.dto.request.NotificationQuery;
import com.bidnow.media.dto.response.NotificationResponse;

import java.util.UUID;

/** The caller's own inbox. Unknown, deleted or other users' notification ids are "not found". */
public interface UserNotificationService {

    PageResponse<NotificationResponse> list(UUID userId, NotificationQuery query);

    long unreadCount(UUID userId);

    /** Returns the notification and marks it read. */
    NotificationResponse get(UUID userId, UUID id);

    NotificationResponse markRead(UUID userId, UUID id);

    NotificationResponse markUnread(UUID userId, UUID id);

    /** @return how many notifications were marked read */
    int markAllRead(UUID userId);

    void delete(UUID userId, UUID id);

    /** @return how many notifications were deleted */
    int deleteAll(UUID userId);

    /** @return how many read notifications were deleted */
    int deleteRead(UUID userId);
}
```

`UserNotificationServiceImpl.java`:

```java
package com.bidnow.media.service.impl;

import com.bidnow.common.constant.ErrorCodes;
import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.media.domain.entity.Notification;
import com.bidnow.media.dto.request.NotificationQuery;
import com.bidnow.media.dto.response.NotificationResponse;
import com.bidnow.media.kafka.UserNotificationPushPublisher;
import com.bidnow.media.realtime.UserNotificationMessage;
import com.bidnow.media.repository.NotificationRepository;
import com.bidnow.media.repository.NotificationSpecifications;
import com.bidnow.media.service.UserNotificationService;
import com.bidnow.media.util.AfterCommit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Inbox reads and edits. Whenever an edit can change the unread count, the new count is pushed as UNREAD_COUNT
 * after commit so the user's other tabs and devices update their badge.
 */
@Service
@RequiredArgsConstructor
public class UserNotificationServiceImpl implements UserNotificationService {

    private final NotificationRepository repository;
    private final UserNotificationPushPublisher pushPublisher;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(UUID userId, NotificationQuery query) {
        return PageResponse.of(repository
                .findAll(NotificationSpecifications.inbox(userId, query, clock.getZone()), query.toPageable())
                .map(NotificationResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return repository.countByUserIdAndReadAtIsNullAndDeletedAtIsNull(userId);
    }

    @Override
    @Transactional
    public NotificationResponse get(UUID userId, UUID id) {
        return markRead(userId, id);
    }

    @Override
    @Transactional
    public NotificationResponse markRead(UUID userId, UUID id) {
        Notification notification = find(userId, id);
        if (notification.getReadAt() == null) {
            notification.setReadAt(now());
            pushUnreadCountAfterCommit(userId);
        }
        return NotificationResponse.from(notification);
    }

    @Override
    @Transactional
    public NotificationResponse markUnread(UUID userId, UUID id) {
        Notification notification = find(userId, id);
        if (notification.getReadAt() != null) {
            notification.setReadAt(null);
            pushUnreadCountAfterCommit(userId);
        }
        return NotificationResponse.from(notification);
    }

    @Override
    @Transactional
    public int markAllRead(UUID userId) {
        int updated = repository.markAllRead(userId, now());
        if (updated > 0) {
            pushUnreadCountAfterCommit(userId);
        }
        return updated;
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID id) {
        Notification notification = find(userId, id);
        boolean wasUnread = notification.getReadAt() == null;
        notification.setDeletedAt(now());
        if (wasUnread) {
            pushUnreadCountAfterCommit(userId);
        }
    }

    @Override
    @Transactional
    public int deleteAll(UUID userId) {
        int deleted = repository.softDeleteAll(userId, now());
        if (deleted > 0) {
            pushUnreadCountAfterCommit(userId);
        }
        return deleted;
    }

    /** Read notifications do not count towards the badge, so no push is needed. */
    @Override
    @Transactional
    public int deleteRead(UUID userId) {
        return repository.softDeleteRead(userId, now());
    }

    private Notification find(UUID userId, UUID id) {
        return repository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new NotFoundException("Notification not found", ErrorCodes.NOT_FOUND));
    }

    /** Counts inside the transaction (after flushing pending entity changes) and pushes once it commits. */
    private void pushUnreadCountAfterCommit(UUID userId) {
        repository.flush();
        long unread = repository.countByUserIdAndReadAtIsNullAndDeletedAtIsNull(userId);
        AfterCommit.run(() -> pushPublisher.publish(userId, UserNotificationMessage.unreadCount(unread)));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -pl media-service -am test -Dtest=UserNotificationServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (13 tests)

- [ ] **Step 5: Stop for review.** Suggested commit: `feat(notification): add user inbox service with unread-count sync (NOTIF-102)`

---

### Task 4: NotificationController and parameter-error handling

**Files:**
- Create: `backend/media-service/src/main/java/com/bidnow/media/controller/NotificationController.java`
- Create: `backend/media-service/src/main/java/com/bidnow/media/exception/MediaExceptionHandler.java`
- Test: `backend/media-service/src/test/java/com/bidnow/media/controller/NotificationControllerTest.java`

**Interfaces:**
- Consumes: Task 3 `UserNotificationService`; Task 1 `UnreadCountResponse`, `BulkUpdateResponse`; Task 2 `NotificationQuery`; common `@AuthenticatedUserId`, `BaseResponse.success(T)`, `UnauthorizedException(String, String)`, `ErrorCodes.UNAUTHORIZED` / `INVALID_INPUT`, `ErrorResponse`, `UserIdArgumentResolver`, `GlobalExceptionHandler`.
- Produces these HTTP endpoints under `/api/v1/notifications`:

| Method | Path | Response `data` |
|---|---|---|
| GET | `` | `PageResponse<NotificationResponse>` |
| GET | `/unread-count` | `{count}` |
| GET | `/{id}` | `NotificationResponse` (marked read) |
| PUT | `/{id}/read` · `/{id}/unread` | `NotificationResponse` |
| PUT | `/mark-all-read` | `{updated}` |
| DELETE | `/{id}` | `"Notification deleted"` |
| DELETE | `/delete-all` · `/delete-read` | `{updated}` |

- Produces: `MediaExceptionHandler`, which turns `MethodArgumentTypeMismatchException` into 400 `INVALID_INPUT`.

- [ ] **Step 1: Write the failing test**

```java
package com.bidnow.media.controller;

import com.bidnow.common.constant.ErrorCodes;
import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.dto.PaginationMeta;
import com.bidnow.common.exception.GlobalExceptionHandler;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.common.resolver.UserIdArgumentResolver;
import com.bidnow.media.domain.enums.NotificationType;
import com.bidnow.media.dto.request.NotificationQuery;
import com.bidnow.media.dto.response.NotificationResponse;
import com.bidnow.media.exception.MediaExceptionHandler;
import com.bidnow.media.service.UserNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    private static final String USER_HEADER = "X-User-Id";
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");

    @Mock
    private UserNotificationService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new NotificationController(service))
                .setControllerAdvice(new MediaExceptionHandler(), new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new UserIdArgumentResolver())
                .build();
    }

    private static NotificationResponse response(boolean read) {
        return NotificationResponse.builder().id(ID).type("AUCTION_WON").title("You won").read(read).build();
    }

    @Test
    void list_bindsFiltersAndReturnsPage() throws Exception {
        when(service.list(eq(ALICE), any())).thenReturn(PageResponse.<NotificationResponse>builder()
                .data(List.of(response(false)))
                .pagination(PaginationMeta.builder().page(0).limit(20).total(1).totalPages(1).build())
                .build());

        mockMvc.perform(get("/api/v1/notifications").header(USER_HEADER, ALICE)
                        .param("read", "false").param("types", "AUCTION_WON", "AUCTION_LOST")
                        .param("from", "2026-10-01T00:00:00Z").param("search", "watch").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data[0].id").value(ID.toString()))
                .andExpect(jsonPath("$.data.data[0].read").value(false))
                .andExpect(jsonPath("$.data.pagination.total").value(1));

        ArgumentCaptor<NotificationQuery> query = ArgumentCaptor.forClass(NotificationQuery.class);
        verify(service).list(eq(ALICE), query.capture());
        assertThat(query.getValue().getRead()).isFalse();
        assertThat(query.getValue().getTypes()).containsExactly(NotificationType.AUCTION_WON, NotificationType.AUCTION_LOST);
        assertThat(query.getValue().getFrom()).isEqualTo(OffsetDateTime.parse("2026-10-01T00:00:00Z"));
        assertThat(query.getValue().getSearch()).isEqualTo("watch");
        assertThat(query.getValue().getSize()).isEqualTo(50);
    }

    @Test
    void list_sizeOver100_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/notifications").header(USER_HEADER, ALICE).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ErrorCodes.INVALID_INPUT));
        verifyNoInteractions(service);
    }

    @Test
    void list_unknownType_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/notifications").header(USER_HEADER, ALICE).param("types", "NOPE"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void list_withoutUserHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value(ErrorCodes.UNAUTHORIZED));
        verifyNoInteractions(service);
    }

    @Test
    void unreadCount_returnsCount() throws Exception {
        when(service.unreadCount(ALICE)).thenReturn(7L);

        mockMvc.perform(get("/api/v1/notifications/unread-count").header(USER_HEADER, ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(7));
    }

    @Test
    void get_returnsNotificationMarkedRead() throws Exception {
        when(service.get(ALICE, ID)).thenReturn(response(true));

        mockMvc.perform(get("/api/v1/notifications/{id}", ID).header(USER_HEADER, ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(ID.toString()))
                .andExpect(jsonPath("$.data.read").value(true));
    }

    @Test
    void get_invalidUuid_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/{id}", "not-a-uuid").header(USER_HEADER, ALICE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ErrorCodes.INVALID_INPUT));
        verifyNoInteractions(service);
    }

    @Test
    void get_notFound_returns404() throws Exception {
        when(service.get(ALICE, ID)).thenThrow(new NotFoundException("Notification not found", ErrorCodes.NOT_FOUND));

        mockMvc.perform(get("/api/v1/notifications/{id}", ID).header(USER_HEADER, ALICE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(ErrorCodes.NOT_FOUND));
    }

    @Test
    void markRead_andMarkUnread() throws Exception {
        when(service.markRead(ALICE, ID)).thenReturn(response(true));
        when(service.markUnread(ALICE, ID)).thenReturn(response(false));

        mockMvc.perform(put("/api/v1/notifications/{id}/read", ID).header(USER_HEADER, ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true));
        mockMvc.perform(put("/api/v1/notifications/{id}/unread", ID).header(USER_HEADER, ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(false));
    }

    @Test
    void markAllRead_returnsUpdatedCount() throws Exception {
        when(service.markAllRead(ALICE)).thenReturn(4);

        mockMvc.perform(put("/api/v1/notifications/mark-all-read").header(USER_HEADER, ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").value(4));
    }

    @Test
    void delete_single() throws Exception {
        mockMvc.perform(delete("/api/v1/notifications/{id}", ID).header(USER_HEADER, ALICE))
                .andExpect(status().isOk());

        verify(service).delete(ALICE, ID);
    }

    @Test
    void deleteAll_andDeleteRead_returnUpdatedCounts() throws Exception {
        when(service.deleteAll(ALICE)).thenReturn(5);
        when(service.deleteRead(ALICE)).thenReturn(2);

        mockMvc.perform(delete("/api/v1/notifications/delete-all").header(USER_HEADER, ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").value(5));
        mockMvc.perform(delete("/api/v1/notifications/delete-read").header(USER_HEADER, ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").value(2));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -pl media-service -am test -Dtest=NotificationControllerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation FAILURE, because `NotificationController` and `MediaExceptionHandler` do not exist.

- [ ] **Step 3: Implement**

`MediaExceptionHandler.java`:

```java
package com.bidnow.media.exception;

import com.bidnow.common.constant.ErrorCodes;
import com.bidnow.common.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** media-service specific error mapping; everything else falls through to the common GlobalExceptionHandler. */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MediaExceptionHandler {

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest request) {
        log.info("Invalid parameter '{}' on {}: {}", ex.getName(), request.getRequestURI(), ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .errorCode(ErrorCodes.INVALID_INPUT)
                .message("Invalid value for parameter '" + ex.getName() + "'")
                .path(request.getRequestURI())
                .build();
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }
}
```

`NotificationController.java`:

```java
package com.bidnow.media.controller;

import com.bidnow.common.annotation.AuthenticatedUserId;
import com.bidnow.common.constant.ErrorCodes;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.exception.UnauthorizedException;
import com.bidnow.media.dto.request.NotificationQuery;
import com.bidnow.media.dto.response.BulkUpdateResponse;
import com.bidnow.media.dto.response.NotificationResponse;
import com.bidnow.media.dto.response.UnreadCountResponse;
import com.bidnow.media.service.UserNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "The signed-in user's notification inbox")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final UserNotificationService notificationService;

    @Operation(summary = "List my notifications", description = "Newest first. Filters: read, types, from, to, search.")
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<NotificationResponse>>> list(
            @AuthenticatedUserId UUID userId, @Valid @ModelAttribute NotificationQuery query) {
        return ResponseEntity.ok(BaseResponse.success(notificationService.list(requireUser(userId), query)));
    }

    @Operation(summary = "Count my unread notifications")
    @GetMapping("/unread-count")
    public ResponseEntity<BaseResponse<UnreadCountResponse>> unreadCount(@AuthenticatedUserId UUID userId) {
        return ResponseEntity.ok(BaseResponse.success(new UnreadCountResponse(notificationService.unreadCount(requireUser(userId)))));
    }

    @Operation(summary = "Get one notification", description = "Also marks it as read.")
    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<NotificationResponse>> get(@AuthenticatedUserId UUID userId, @PathVariable UUID id) {
        return ResponseEntity.ok(BaseResponse.success(notificationService.get(requireUser(userId), id)));
    }

    @Operation(summary = "Mark a notification as read")
    @PutMapping("/{id}/read")
    public ResponseEntity<BaseResponse<NotificationResponse>> markRead(@AuthenticatedUserId UUID userId, @PathVariable UUID id) {
        return ResponseEntity.ok(BaseResponse.success(notificationService.markRead(requireUser(userId), id)));
    }

    @Operation(summary = "Mark a notification as unread")
    @PutMapping("/{id}/unread")
    public ResponseEntity<BaseResponse<NotificationResponse>> markUnread(@AuthenticatedUserId UUID userId, @PathVariable UUID id) {
        return ResponseEntity.ok(BaseResponse.success(notificationService.markUnread(requireUser(userId), id)));
    }

    @Operation(summary = "Mark all my notifications as read")
    @PutMapping("/mark-all-read")
    public ResponseEntity<BaseResponse<BulkUpdateResponse>> markAllRead(@AuthenticatedUserId UUID userId) {
        return ResponseEntity.ok(BaseResponse.success(new BulkUpdateResponse(notificationService.markAllRead(requireUser(userId)))));
    }

    @Operation(summary = "Delete a notification")
    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse<String>> delete(@AuthenticatedUserId UUID userId, @PathVariable UUID id) {
        notificationService.delete(requireUser(userId), id);
        return ResponseEntity.ok(BaseResponse.success("Notification deleted"));
    }

    @Operation(summary = "Delete all my notifications")
    @DeleteMapping("/delete-all")
    public ResponseEntity<BaseResponse<BulkUpdateResponse>> deleteAll(@AuthenticatedUserId UUID userId) {
        return ResponseEntity.ok(BaseResponse.success(new BulkUpdateResponse(notificationService.deleteAll(requireUser(userId)))));
    }

    @Operation(summary = "Delete all my read notifications")
    @DeleteMapping("/delete-read")
    public ResponseEntity<BaseResponse<BulkUpdateResponse>> deleteRead(@AuthenticatedUserId UUID userId) {
        return ResponseEntity.ok(BaseResponse.success(new BulkUpdateResponse(notificationService.deleteRead(requireUser(userId)))));
    }

    /** The gateway and Spring Security already reject anonymous calls; this keeps the controller safe on its own. */
    private static UUID requireUser(UUID userId) {
        if (userId == null) {
            throw new UnauthorizedException("Authentication required", ErrorCodes.UNAUTHORIZED);
        }
        return userId;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -pl media-service -am test -Dtest=NotificationControllerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (12 tests).

`list_unknownType_returns400` relies on the `@ModelAttribute` binding error for `types=NOPE` surfacing as a `BindException`, which `GlobalExceptionHandler` maps to 400. If it surfaces as a 500 instead, add a `BindException` handler to `MediaExceptionHandler` that returns 400 `INVALID_INPUT` with the field errors, and note it in the report.

- [ ] **Step 5: Stop for review.** Suggested commit: `feat(notification): add user notification inbox endpoints (NOTIF-102)`

---

### Task 5: Gateway route

**Files:**
- Modify: `backend/api-gateway/src/main/resources/application.yml` (the `media-service` route)
- Test: `backend/api-gateway/src/test/java/com/bidnow/gateway/GatewayRoutesTest.java`

**Interfaces:**
- Produces: the gateway forwards `/api/v1/notifications/**` to `lb://media-service`. JWT checks and internal-path blocking are unchanged, because `AuthenticationFilter` is global.

- [ ] **Step 1: Write the failing test.** Append to `GatewayRoutesTest`:

```java
    @Test
    void mediaRouteServesUserNotifications() {
        Route media = routes().stream()
                .filter(r -> r.getId().equals("media-service"))
                .findFirst()
                .orElseThrow();
        assertThat(media.getPredicate().toString()).contains("/api/v1/notifications/**");
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -pl api-gateway -am test -Dtest=GatewayRoutesTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL in `mediaRouteServesUserNotifications`, because the predicate does not contain `/api/v1/notifications/**`.

- [ ] **Step 3: Implement.** In `api-gateway/src/main/resources/application.yml`, change the `media-service` route predicate line to:

```yaml
            - Path=/api/v1/media/**, /api/v1/admin/templates/**, /api/v1/admin/email-logs/**, /api/v1/admin/audit-logs, /api/v1/notifications/**
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -pl api-gateway -am test -Dtest=GatewayRoutesTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS. All existing `GatewayRoutesTest` tests stay green, including `noRouteMatchesOnServiceIdPathPrefix` and `explicitRoutesArePresent`.

- [ ] **Step 5: Run the gateway suite**

Run: `mvn -q -pl api-gateway -am test`
Expected: PASS. This includes the `AuthenticationFilter` tests, which prove `/api/v1/**/internal/**` is still blocked.

- [ ] **Step 6: Stop for review.** Suggested commit: `feat(gateway): route user notification endpoints to media-service (NOTIF-102)`

---

### Task 6: Verification and roadmap update

**Files:**
- Modify: `docs/superpowers/plans/2026-09-30-notification-epic-roadmap.md` (Story 2 section)

- [ ] **Step 1: Run the affected suites**

Run: `mvn -q -pl media-service,api-gateway -am test`
Expected: PASS

- [ ] **Step 2: Run both Postgres ITs (Docker required)**

Run: `mvn -q -pl media-service -am test -Dtest='NotificationPersistencePostgresIT,NotificationRepositoryPostgresIT' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (10 + 7 tests)

- [ ] **Step 3: Update the roadmap's Story 2 section** so it matches what was built. In `docs/superpowers/plans/2026-09-30-notification-epic-roadmap.md`:
  - Replace `` `dto/request/criteria/NotificationCriteria.java` (`read: Boolean`, `types: List<String>`, `from/to: OffsetDateTime`, `search: String`) `` with `` `dto/request/NotificationQuery.java` (`page`, `size` 1–100, `read: Boolean`, `types: List<NotificationType>`, `from/to: OffsetDateTime`, `search` ≤ 100 chars) ``.
  - Replace the bullet `` `SecurityConfig`: `/api/v1/notifications/**` authenticated `` with `` `SecurityConfig`: no change (`anyRequest().authenticated()` already covers it); `exception/MediaExceptionHandler.java` maps invalid path/query values to 400 ``.
  - In the sentence starting "Every mutation that changes the unread count publishes a push", append: ` Only mutations that can change the count push: get/read/unread (when the state flips), mark-all-read and delete-all (when rows changed), and delete of an unread notification. delete-read never pushes.`
  - In task 2.5, replace the text with: `**2.5 API docs.** Regenerate media-service/api-docs.json from the running service (manual, see the NOTIF-102 plan Task 6). The Postman collection moves to Story 9.`
  - Tick tasks 2.1–2.4.

- [ ] **Step 4: Manual checks (the user runs these; agents skip them).** With the stack running via docker-compose, and a user JWT in `$TOKEN`:
  1. `curl -s -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/v1/notifications?size=5"` → 200 with `data.data[]` and `data.pagination`.
  2. `curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/notifications/unread-count` → `{"data":{"count":N}}`.
  3. With a browser tab subscribed to `/user/queue/notifications` (see the NOTIF-101 smoke steps), run `curl -s -X PUT -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/notifications/mark-all-read`. The tab receives `{"type":"UNREAD_COUNT","notification":null,"unreadCount":0}`.
  4. `curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/notifications` without a token → 401.
  5. Regenerate the API docs from `backend/` with `curl -s http://localhost:8086/api/v1/media/v3/api-docs -o media-service/api-docs.json`, and check that the diff adds the `/api/v1/notifications` paths.

- [ ] **Step 5: Stop for review.** Suggested commit: `docs(notification): record NOTIF-102 refinements in roadmap`
