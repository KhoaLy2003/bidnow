# NOTIF-104 — Event → Notification Handlers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Each auction, bid, payment and refund event that concerns a user produces its in-app notification (and email where the matrix says so) exactly once per event, through the NOTIF-101 dispatcher.

**Architecture:** `NotificationKafkaConsumer` routes each topic to one of three handlers: `AuctionNotificationHandler`, `BidNotificationHandler` and `PaymentNotificationHandler`. Each handler builds `NotificationIntent`s and calls `NotificationDispatcher.dispatch/dispatchAll`.
- **Titles, sellers and bidders** come from the event, or from media's own projection through a small `AuctionLookup`.
- **Email copy** comes from the seeded EN/VI templates. The dispatcher fills `{userName}` from the recipient's display name, which the user-service preferences endpoint now also returns.
- **In-app copy** is English, written in each handler.

**Tech Stack:** Java 17, Spring Boot 3.2.4, Spring Kafka, Spring Data JPA/JDBC, PostgreSQL + Liquibase, commons-text `StringSubstitutor`, JUnit 5, Mockito, AssertJ, Testcontainers (for the `*PostgresIT` only).

**Spec:** `docs/superpowers/plans/2026-09-30-notification-epic-roadmap.md`: Story 4, and Decisions 3, 4, 5, 6 and 8. It builds on NOTIF-101 (`5d9cd4f`) and NOTIF-102 (`fc90c6e`).

This plan refines Story 4 in the following places. Task 7 records each one in the roadmap.
1. **No new `PAYMENT_REQUIRED` template.** The seeded `AUCTION_WON` template already says "Congratulations… please complete payment before {paymentDeadline}". It is exactly Decision 3's single winner email, so `PaymentEvent REQUIRED` sends `AUCTION_WON`. Story 6 can reuse the seeded `PAYMENT_REMINDER_2` ("final notice") for the 24 h reminder, so it needs no new template either.
2. **New templates are only `AUCTION_CANCELLED`, `PAYMENT_FAILED` and `SALE_PAYMENT_RECEIVED` (EN/VI).** They ship in migration `07-event-templates.sql`. `SALE_PAYMENT_RECEIVED` tells the seller the buyer paid. Wallet credits the seller exactly the winning bid (no fee), so the email states that amount.
3. **`{userName}` in every template** is filled by the dispatcher from a new `Recipient.displayName`. That comes from `UserNotificationPreferenceResponse.displayName`, an additive field. user-service now builds that response from the user's profile, joined with their preferences, so users without a preferences row are still returned with defaults. Handlers never pass `userName`, except welcome, which already does. If the name is unknown, the fallback is "there".
4. **Refund emails only for `AUCTION_LOST`.** The seeded `DEPOSIT_REFUNDED` copy says "because you did not win". A refund with reason `AUCTION_CANCELLED` is in-app only, because the `AUCTION_CANCELLED` email already tells the bidder their deposit is coming back.
5. **Extension dedup key.** `DedupKeys.extended(auctionId, newEndTime)` gives exactly one notification per extension per user, and Kafka redelivery is a no-op. Real extensions are always 5 minutes apart, so the 5-minute bucket never merges two different ones.
6. **In-app copy lives in each handler.** There is no shared `Messages.java`. It is EN only (post-MVP: `IN_APP` templates).
7. **Payment and refund links go to `/wallet`.** The seller's "buyer paid" notification links to `/seller/auctions`. Auction notifications link to `/auctions/{id}`.
8. **New dedup key `DedupKeys.unsold(auctionId)`** gives `AUCTION_UNSOLD:{id}`.

## Global Constraints

- **Idempotency:** every intent's `dedupKey` comes from `DedupKeys`. The same event redelivered produces no second row, email or push.
- **Recipients** (Decision 4): losers, cancelled and extended recipients come from `media_auction_participants` (via `AuctionLookup.participants`), never from `AuctionEndedEvent.loserIds` and never from another service.
- **Winner email** (Decision 3): the winner's email is sent on `PaymentEvent REQUIRED`, not on `AuctionEndedEvent`. `AuctionEndedEvent` gives the winner an in-app `AUCTION_WON` only.
- **Transactional emails** (Decision 5) ignore the user's email opt-out. These are the winner/payment-required, payment-successful, payment-failed and seller sale-payment-received emails. Engagement emails respect it: auction created, lost, cancelled and deposit refunded.
- **Extension notifications** are in-app only, sent to participants plus the seller, and deduplicated per extension.
- **Template names** are `{BASE}_{EN|VI}`. Every template variable a handler relies on must be supplied (Task 6 checks this). The dispatcher adds `userName`.
- **Link paths:**
  - In-app `actionUrl` is a frontend-relative path.
  - Email `actionUrl` is `app.frontend.base-url` + that path.
- **Money** is formatted `$#,##0.00` (US grouping). Deadlines are formatted `yyyy-MM-dd HH:mm 'UTC'`.
- **Fallback title:** a missing auction title falls back to the projection's title, then to `"your auction"`.
- **Consumer group:** handlers run on the shared notification group (`${spring.kafka.consumer.group-id}`). Do not change the consumer groups of the projection, realtime or push consumers.
- Maven runs from `backend/`. Default unit tests must not need Docker. `*PostgresIT` tests run only when named explicitly.
- **Agents do not commit.** Each task ends with a review stop.

## File Structure

**common**
- Modify `backend/common/src/main/java/com/bidnow/common/dto/UserNotificationPreferenceResponse.java`: add `displayName`.

**user-service**
- Modify `service/impl/UserProfileServiceImpl.java` (`getNotificationPreferences`) and its test.

**media-service** (`backend/media-service/src/main/java/com/bidnow/media/…`)
- Modify `notification/Recipient.java`: add `displayName`, keeping a 4-argument constructor.
- Modify `notification/RecipientDirectory.java`: map `displayName`.
- Modify `notification/NotificationDispatcher.java`: fill `userName`.
- Modify `notification/DedupKeys.java`: add `unsold`.
- Create `notification/NotificationFormats.java`: money and deadline formatting.
- Create `notification/NotificationLinks.java`: frontend paths and absolute URLs.
- Create `projection/AuctionLookup.java`: title with fallback, seller, participants.
- Modify `domain/enums/NotificationType.java`: add `AUCTION_UNSOLD`.
- Create `resources/db/changelog/migrations/07-event-templates.sql` and include it in the master changelog.
- Create `notification/handler/AuctionNotificationHandler.java`, `BidNotificationHandler.java` and `PaymentNotificationHandler.java`.
- Modify `kafka/NotificationKafkaConsumer.java`: route topics to the handlers and add 3 listeners.
- Modify `service/NotificationService.java` and `service/impl/NotificationServiceImpl.java`: drop the four stub handlers.
- Tests under `src/test/java/com/bidnow/media/…`, listed per task. They include `notification/NotificationTemplatesPostgresIT.java`.

**docs**
- Modify `docs/superpowers/plans/2026-09-30-notification-epic-roadmap.md` (Task 7).

---

### Task 1: Recipient display names for `{userName}`

**Files:**
- Modify: `backend/common/src/main/java/com/bidnow/common/dto/UserNotificationPreferenceResponse.java`
- Modify: `backend/user-service/src/main/java/com/bidnow/user/service/impl/UserProfileServiceImpl.java`
- Test: `backend/user-service/src/test/java/com/bidnow/user/service/impl/UserProfileServiceImplTest.java`
- Modify: `backend/media-service/src/main/java/com/bidnow/media/notification/Recipient.java`
- Modify: `backend/media-service/src/main/java/com/bidnow/media/notification/RecipientDirectory.java`
- Modify: `backend/media-service/src/main/java/com/bidnow/media/notification/NotificationDispatcher.java`
- Test: `backend/media-service/src/test/java/com/bidnow/media/notification/RecipientDirectoryTest.java`
- Test: `backend/media-service/src/test/java/com/bidnow/media/notification/NotificationDispatcherTest.java`

**Interfaces:**
- Produces: `UserNotificationPreferenceResponse.getDisplayName(): String`.
- Produces: `POST /api/v1/users/internal/notification-preferences` returns one entry per requested user who **has a profile**. A missing preferences row gives `language`/`emailNotifications` as null (consumers treat that as EN / opt-in).
- Produces: `record Recipient(UUID userId, String email, NotificationLanguage language, boolean emailOptIn, String displayName)`, plus the constructor `Recipient(UUID, String, NotificationLanguage, boolean)` with `displayName = null`.
- Produces: the dispatcher's email variables always contain `userName`. It is the handler's value if present, else the recipient's display name, else `"there"`.

- [ ] **Step 1: Write the failing tests**

In `UserProfileServiceImplTest`, replace the test `getNotificationPreferences_returnsExistingPreferencesWithOneQuery` with:

```java
    @Test
    void getNotificationPreferences_joinsProfilesWithPreferences() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        when(userProfileRepository.findByUserIdIn(any())).thenReturn(List.of(
                UserProfile.builder().userId(alice).displayName("Alice").build(),
                UserProfile.builder().userId(bob).displayName("Bob").build()));
        when(userPreferencesRepository.findByUserIdIn(any())).thenReturn(List.of(
                UserPreferences.builder().userId(alice).language("vi").emailNotifications(false).build()));

        List<UserNotificationPreferenceResponse> result =
                userProfileService.getNotificationPreferences(List.of(alice, bob, unknown, alice));

        assertThat(result).extracting(UserNotificationPreferenceResponse::getUserId).containsExactlyInAnyOrder(alice, bob);
        assertThat(result).filteredOn(p -> p.getUserId().equals(alice)).singleElement().satisfies(p -> {
            assertThat(p.getDisplayName()).isEqualTo("Alice");
            assertThat(p.getLanguage()).isEqualTo("vi");
            assertThat(p.getEmailNotifications()).isFalse();
        });
        assertThat(result).filteredOn(p -> p.getUserId().equals(bob)).singleElement().satisfies(p -> {
            assertThat(p.getDisplayName()).isEqualTo("Bob");
            assertThat(p.getLanguage()).isNull();
            assertThat(p.getEmailNotifications()).isNull();
        });
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> profileIds = ArgumentCaptor.forClass(Collection.class);
        verify(userProfileRepository).findByUserIdIn(profileIds.capture());
        assertThat(profileIds.getValue()).containsExactlyInAnyOrder(alice, bob, unknown);
    }
```

In `RecipientDirectoryTest`, add:

```java
    @Test
    void resolve_carriesDisplayName() {
        when(identityServiceClient.getEmailsByUserIds(anyList()))
                .thenReturn(BaseResponse.success(Map.of(ALICE, "alice@example.com")));
        when(userServiceClient.getNotificationPreferences(any())).thenReturn(BaseResponse.success(List.of(
                UserNotificationPreferenceResponse.builder().userId(ALICE).displayName("Alice").build())));

        assertThat(directory.resolve(List.of(ALICE)).get(ALICE).displayName()).isEqualTo("Alice");
    }
```

In `NotificationDispatcherTest`, add these two tests. They use `ArgumentCaptor`, `eq`, `any` and `anyString` from Mockito. The first three are already imported. Add `import static org.mockito.ArgumentMatchers.anyString;` if it is missing:

```java
    @Test
    @SuppressWarnings("unchecked")
    void dispatch_emailWithoutUserName_greetsRecipientByDisplayName() {
        when(inboxRepository.insertIfAbsent(any())).thenReturn(true);
        when(recipientDirectory.resolve(any())).thenReturn(Map.of(ALICE,
                new Recipient(ALICE, "alice@example.com", NotificationLanguage.EN, true, "Alice Smith")));
        when(templateResolver.resolve(anyString(), any())).thenReturn(Optional.of(template));
        NotificationIntent.EmailSpec spec = new NotificationIntent.EmailSpec("AUCTION_LOST", Map.of("auctionTitle", "Watch"), false);

        dispatcher.dispatch(intent(ALICE, spec));
        commit();

        ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.forClass(Map.class);
        verify(emailService).sendTemplateEmail(any(UUID.class), eq("alice@example.com"), eq(template), variables.capture());
        assertThat(variables.getValue())
                .containsEntry("userName", "Alice Smith")
                .containsEntry("auctionTitle", "Watch");
    }

    @Test
    @SuppressWarnings("unchecked")
    void dispatch_unknownDisplayName_greetsThere() {
        when(inboxRepository.insertIfAbsent(any())).thenReturn(true);
        when(recipientDirectory.resolve(any())).thenReturn(Map.of(ALICE,
                new Recipient(ALICE, "alice@example.com", NotificationLanguage.EN, true)));
        when(templateResolver.resolve(anyString(), any())).thenReturn(Optional.of(template));
        NotificationIntent.EmailSpec spec = new NotificationIntent.EmailSpec("AUCTION_LOST", Map.of("auctionTitle", "Watch"), false);

        dispatcher.dispatch(intent(ALICE, spec));
        commit();

        ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.forClass(Map.class);
        verify(emailService).sendTemplateEmail(any(UUID.class), eq("alice@example.com"), eq(template), variables.capture());
        assertThat(variables.getValue()).containsEntry("userName", "there");
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q -pl user-service,media-service -am test -Dtest='UserProfileServiceImplTest,RecipientDirectoryTest,NotificationDispatcherTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation FAILURE, because `getDisplayName`/`displayName(...)` and the 5-argument `Recipient` do not exist.

- [ ] **Step 3: Implement**

In `UserNotificationPreferenceResponse`, add after `emailNotifications`:

```java
    @Schema(description = "Display name, used to greet the user in emails")
    private String displayName;
```

In `UserProfileServiceImpl`, replace `getNotificationPreferences` with the code below. Add these imports: `java.util.Map`, `java.util.Set`, `java.util.function.Function`, `java.util.stream.Collectors`.

```java
    @Override
    @Transactional(readOnly = true)
    public List<UserNotificationPreferenceResponse> getNotificationPreferences(List<UUID> userIds) {
        Set<UUID> ids = new LinkedHashSet<>(userIds);
        Map<UUID, UserPreferences> preferences = userPreferencesRepository.findByUserIdIn(ids).stream()
                .collect(Collectors.toMap(UserPreferences::getUserId, Function.identity(), (first, second) -> first));
        return userProfileRepository.findByUserIdIn(ids).stream()
                .map(profile -> {
                    UserPreferences preference = preferences.get(profile.getUserId());
                    return UserNotificationPreferenceResponse.builder()
                            .userId(profile.getUserId())
                            .displayName(profile.getDisplayName())
                            .language(preference == null ? null : preference.getLanguage())
                            .emailNotifications(preference == null ? null : preference.getEmailNotifications())
                            .build();
                })
                .toList();
    }
```

Replace `Recipient.java` with:

```java
package com.bidnow.media.notification;

import com.bidnow.media.domain.enums.NotificationLanguage;

import java.util.UUID;

/**
 * Where and how to email a user. {@code email} is null when unknown; {@code emailOptIn} gates non-transactional
 * emails; {@code displayName} (nullable) greets the user as the templates' {@code {userName}}.
 */
public record Recipient(UUID userId, String email, NotificationLanguage language, boolean emailOptIn, String displayName) {

    public Recipient(UUID userId, String email, NotificationLanguage language, boolean emailOptIn) {
        this(userId, email, language, emailOptIn, null);
    }

    public static Recipient unknown(UUID userId) {
        return new Recipient(userId, null, NotificationLanguage.EN, true);
    }
}
```

In `RecipientDirectory.resolve`, add the fifth argument to the `new Recipient(...)` call:

```java
            recipients.put(id, new Recipient(
                    id,
                    emails.get(id),
                    NotificationLanguage.fromCode(preference == null ? null : preference.getLanguage()),
                    preference == null || !Boolean.FALSE.equals(preference.getEmailNotifications()),
                    preference == null ? null : preference.getDisplayName()));
```

In `NotificationDispatcher.sendEmail`, change the `emailService.sendTemplateEmail(...)` call's last argument from `spec.variables()` to `withUserName(spec.variables(), recipient)`. Then add this method below `sendEmail`, with the imports `java.util.HashMap` and `org.springframework.util.StringUtils`:

```java
    /** Templates greet {userName}; handlers rarely know it, so it is filled from the recipient's display name. */
    private static Map<String, Object> withUserName(Map<String, Object> variables, Recipient recipient) {
        Map<String, Object> merged = new HashMap<>(variables == null ? Map.of() : variables);
        merged.putIfAbsent("userName", StringUtils.hasText(recipient.displayName()) ? recipient.displayName() : "there");
        return merged;
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q -pl user-service,media-service -am test -Dtest='UserProfileServiceImplTest,UserProfileControllerTest,RecipientDirectoryTest,NotificationDispatcherTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS. The existing dispatcher tests still pass, because their `VARS` already contain `userName`, and `putIfAbsent` leaves it unchanged.

- [ ] **Step 5: Stop for review.** Suggested commit: `feat(notification): greet email recipients by display name (NOTIF-104)`

---

### Task 2: Shared lookup, formatting, links, dedup key, type and templates

**Files:**
- Create: `backend/media-service/src/main/java/com/bidnow/media/projection/AuctionLookup.java`
- Create: `backend/media-service/src/main/java/com/bidnow/media/notification/NotificationFormats.java`
- Create: `backend/media-service/src/main/java/com/bidnow/media/notification/NotificationLinks.java`
- Modify: `backend/media-service/src/main/java/com/bidnow/media/notification/DedupKeys.java`
- Modify: `backend/media-service/src/main/java/com/bidnow/media/domain/enums/NotificationType.java`
- Create: `backend/media-service/src/main/resources/db/changelog/migrations/07-event-templates.sql`
- Modify: `backend/media-service/src/main/resources/db/changelog/db.changelog-master.xml`
- Test: `backend/media-service/src/test/java/com/bidnow/media/projection/AuctionLookupTest.java`
- Test: `backend/media-service/src/test/java/com/bidnow/media/notification/NotificationFormatsTest.java`
- Test: `backend/media-service/src/test/java/com/bidnow/media/notification/DedupKeysTest.java` (add one assertion)

**Interfaces:**
- Consumes: `AuctionProjectionRepository.findAuction(UUID): Optional<AuctionRef>` and `findParticipantIds(UUID): List<UUID>`, and `record AuctionRef(UUID auctionId, String title, UUID sellerId, OffsetDateTime endTime)`.
- Produces: `AuctionLookup.title(UUID auctionId, String eventTitle): String`, `sellerId(UUID): Optional<UUID>`, `participants(UUID): List<UUID>`, and the constant `AuctionLookup.UNKNOWN_TITLE = "your auction"`.
- Produces: `NotificationFormats.money(BigDecimal): String` (for example `"$1,234.50"`, or `""` for null) and `NotificationFormats.deadline(Instant): String` (for example `"2026-10-03 10:00 UTC"`, or `""` for null).
- Produces: `NotificationLinks` (a Spring bean, constructor `NotificationLinks(@Value("${app.frontend.base-url:http://localhost:3000}") String)`) with:
  - `absolute(String path): String`
  - static `auctionPath(UUID): String`, giving `"/auctions/{id}"`
  - constants `WALLET_PATH = "/wallet"`, `AUCTIONS_PATH = "/auctions"` and `SELLER_AUCTIONS_PATH = "/seller/auctions"`
- Produces: `DedupKeys.unsold(UUID): String`, giving `"AUCTION_UNSOLD:{id}"`.
- Produces: `NotificationType.AUCTION_UNSOLD`.
- Produces: the templates `AUCTION_CANCELLED_{EN,VI}` (variables `userName, auctionTitle, actionUrl`), `PAYMENT_FAILED_{EN,VI}` (variables `userName, auctionTitle, depositAmount, actionUrl`) and `SALE_PAYMENT_RECEIVED_{EN,VI}` (variables `userName, auctionTitle, bidAmount, actionUrl`).

- [ ] **Step 1: Write the failing tests**

`AuctionLookupTest.java`:

```java
package com.bidnow.media.projection;

import com.bidnow.media.repository.AuctionProjectionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionLookupTest {

    private static final UUID AUCTION = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000005");

    @Mock
    private AuctionProjectionRepository repository;

    @InjectMocks
    private AuctionLookup lookup;

    @Test
    void title_prefersTheEventTitle() {
        assertThat(lookup.title(AUCTION, "Vintage Watch")).isEqualTo("Vintage Watch");
        verifyNoInteractions(repository);
    }

    @Test
    void title_fallsBackToProjectionThenDefault() {
        when(repository.findAuction(AUCTION)).thenReturn(Optional.of(new AuctionRef(AUCTION, "Camera", SELLER, null)));
        assertThat(lookup.title(AUCTION, " ")).isEqualTo("Camera");

        when(repository.findAuction(AUCTION)).thenReturn(Optional.empty());
        assertThat(lookup.title(AUCTION, null)).isEqualTo(AuctionLookup.UNKNOWN_TITLE);
    }

    @Test
    void sellerAndParticipants_comeFromTheProjection() {
        UUID bidder = UUID.randomUUID();
        when(repository.findAuction(AUCTION)).thenReturn(Optional.of(new AuctionRef(AUCTION, "Camera", SELLER, null)));
        when(repository.findParticipantIds(AUCTION)).thenReturn(List.of(bidder));

        assertThat(lookup.sellerId(AUCTION)).contains(SELLER);
        assertThat(lookup.participants(AUCTION)).containsExactly(bidder);
    }
}
```

`NotificationFormatsTest.java`:

```java
package com.bidnow.media.notification;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationFormatsTest {

    @Test
    void money_usesDollarsWithGroupingAndTwoDecimals() {
        assertThat(NotificationFormats.money(new BigDecimal("1234.5"))).isEqualTo("$1,234.50");
        assertThat(NotificationFormats.money(new BigDecimal("105"))).isEqualTo("$105.00");
        assertThat(NotificationFormats.money(null)).isEmpty();
    }

    @Test
    void deadline_isUtcToTheMinute() {
        assertThat(NotificationFormats.deadline(Instant.parse("2026-10-03T10:00:59Z"))).isEqualTo("2026-10-03 10:00 UTC");
        assertThat(NotificationFormats.deadline(null)).isEmpty();
    }

    @Test
    void links_buildRelativeAndAbsoluteUrls() {
        NotificationLinks links = new NotificationLinks("http://localhost:3000/");
        java.util.UUID id = java.util.UUID.fromString("a0000000-0000-0000-0000-000000000001");

        assertThat(NotificationLinks.auctionPath(id)).isEqualTo("/auctions/" + id);
        assertThat(links.absolute(NotificationLinks.WALLET_PATH)).isEqualTo("http://localhost:3000/wallet");
    }
}
```

In `DedupKeysTest.formatsAreFixed`, add:

```java
        assertThat(DedupKeys.unsold(A)).isEqualTo("AUCTION_UNSOLD:" + A);
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q -pl media-service -am test -Dtest='AuctionLookupTest,NotificationFormatsTest,DedupKeysTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation FAILURE, because the new classes and `DedupKeys.unsold` do not exist.

- [ ] **Step 3: Implement**

`AuctionLookup.java`:

```java
package com.bidnow.media.projection;

import com.bidnow.media.repository.AuctionProjectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read side of media's auction projection, as notification handlers need it. */
@Component
@RequiredArgsConstructor
public class AuctionLookup {

    public static final String UNKNOWN_TITLE = "your auction";

    private final AuctionProjectionRepository repository;

    /** The event's title when present, else the projected title, else {@link #UNKNOWN_TITLE}. */
    public String title(UUID auctionId, String eventTitle) {
        if (StringUtils.hasText(eventTitle)) {
            return eventTitle;
        }
        return repository.findAuction(auctionId)
                .map(AuctionRef::title)
                .filter(StringUtils::hasText)
                .orElse(UNKNOWN_TITLE);
    }

    public Optional<UUID> sellerId(UUID auctionId) {
        return repository.findAuction(auctionId).map(AuctionRef::sellerId);
    }

    /** Everyone who has bid on the auction (from bid-placed events). */
    public List<UUID> participants(UUID auctionId) {
        return repository.findParticipantIds(auctionId);
    }
}
```

`NotificationFormats.java`:

```java
package com.bidnow.media.notification;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Value formats shared by notification copy and email variables. */
public final class NotificationFormats {

    private static final DateTimeFormatter DEADLINE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private NotificationFormats() {
    }

    public static String money(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return "$" + format.format(amount.setScale(2, RoundingMode.HALF_UP));
    }

    public static String deadline(Instant instant) {
        return instant == null ? "" : DEADLINE.format(instant);
    }
}
```

`NotificationLinks.java`:

```java
package com.bidnow.media.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Frontend links: relative paths for in-app notifications, absolute URLs for emails. */
@Component
public class NotificationLinks {

    public static final String AUCTIONS_PATH = "/auctions";
    public static final String WALLET_PATH = "/wallet";
    public static final String SELLER_AUCTIONS_PATH = "/seller/auctions";

    private final String frontendBaseUrl;

    public NotificationLinks(@Value("${app.frontend.base-url:http://localhost:3000}") String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
    }

    public static String auctionPath(UUID auctionId) {
        return AUCTIONS_PATH + "/" + auctionId;
    }

    public String absolute(String path) {
        return frontendBaseUrl + path;
    }
}
```

In `DedupKeys.java`, add after `lost(...)`:

```java
    public static String unsold(UUID auctionId) {
        return "AUCTION_UNSOLD:" + auctionId;
    }
```

In `NotificationType.java`, append `AUCTION_UNSOLD` after `PAYMENT_FAILED`. Remember the comma after `PAYMENT_FAILED`.

Create `07-event-templates.sql`. The seeded templates in `02-initial-templates.sql` are the style reference.

```sql
-- liquibase formatted sql

-- Variables per template (NotificationTemplatesPostgresIT checks that handlers supply them all):
--   AUCTION_CANCELLED_{EN,VI}: userName, auctionTitle, actionUrl
--   PAYMENT_FAILED_{EN,VI}:    userName, auctionTitle, depositAmount, actionUrl
--   SALE_PAYMENT_RECEIVED_{EN,VI}: userName, auctionTitle, bidAmount, actionUrl

-- changeset hiep.nguyen:event-templates-auction-cancelled
INSERT INTO media_notification_templates (id, name, type, language, subject, body_html, body_text, variables, active)
VALUES (gen_random_uuid(), 'AUCTION_CANCELLED_EN', 'EMAIL', 'EN', 'Auction "{auctionTitle}" was cancelled',
        '<div style="font-family: Arial, sans-serif; color: #333;"><h2>Auction Cancelled</h2><p>Hello <strong>{userName}</strong>,</p><p>The auction for <strong>"{auctionTitle}"</strong> you bid on has been cancelled.</p><p>Any deposit you placed is being refunded to your wallet.</p><a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #17a2b8; color: #fff; text-decoration: none; border-radius: 5px;">Go To Wallet</a><p>The BidNow Team</p></div>',
        'Hello {userName},\n\nThe auction for "{auctionTitle}" you bid on has been cancelled.\nAny deposit you placed is being refunded to your wallet: {actionUrl}\n\nThe BidNow Team',
        '["userName", "auctionTitle", "actionUrl"]', true),

       (gen_random_uuid(), 'AUCTION_CANCELLED_VI', 'EMAIL', 'VI', 'Phiên đấu giá "{auctionTitle}" đã bị hủy',
        '<div style="font-family: Arial, sans-serif; color: #333;"><h2>Phiên đấu giá đã bị hủy</h2><p>Xin chào <strong>{userName}</strong>,</p><p>Phiên đấu giá <strong>"{auctionTitle}"</strong> mà bạn tham gia đã bị hủy.</p><p>Tiền cọc của bạn (nếu có) đang được hoàn về ví.</p><a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #17a2b8; color: #fff; text-decoration: none; border-radius: 5px;">Đến Ví của bạn</a><p>Đội ngũ BidNow</p></div>',
        'Xin chào {userName},\n\nPhiên đấu giá "{auctionTitle}" mà bạn tham gia đã bị hủy.\nTiền cọc của bạn (nếu có) đang được hoàn về ví: {actionUrl}\n\nĐội ngũ BidNow',
        '["userName", "auctionTitle", "actionUrl"]', true);

-- changeset hiep.nguyen:event-templates-payment-failed
INSERT INTO media_notification_templates (id, name, type, language, subject, body_html, body_text, variables, active)
VALUES (gen_random_uuid(), 'PAYMENT_FAILED_EN', 'EMAIL', 'EN', 'Payment deadline missed for "{auctionTitle}"',
        '<div style="font-family: Arial, sans-serif; color: #333;"><h2 style="color: #dc3545;">Payment Deadline Missed</h2><p>Hello <strong>{userName}</strong>,</p><p>The payment deadline for <strong>"{auctionTitle}"</strong> has passed, so your win has been cancelled.</p><p>Your deposit of <strong>{depositAmount}</strong> has been forfeited.</p><a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #6c757d; color: #fff; text-decoration: none; border-radius: 5px;">View Wallet</a><p>The BidNow Team</p></div>',
        'Hello {userName},\n\nThe payment deadline for "{auctionTitle}" has passed, so your win has been cancelled.\nYour deposit of {depositAmount} has been forfeited.\nView your wallet: {actionUrl}\n\nThe BidNow Team',
        '["userName", "auctionTitle", "depositAmount", "actionUrl"]', true),

       (gen_random_uuid(), 'PAYMENT_FAILED_VI', 'EMAIL', 'VI', 'Đã quá hạn thanh toán cho "{auctionTitle}"',
        '<div style="font-family: Arial, sans-serif; color: #333;"><h2 style="color: #dc3545;">Đã quá hạn thanh toán</h2><p>Xin chào <strong>{userName}</strong>,</p><p>Đã quá hạn thanh toán cho <strong>"{auctionTitle}"</strong>, vì vậy kết quả thắng của bạn đã bị hủy.</p><p>Tiền cọc <strong>{depositAmount}</strong> của bạn đã bị tịch thu.</p><a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #6c757d; color: #fff; text-decoration: none; border-radius: 5px;">Xem Ví</a><p>Đội ngũ BidNow</p></div>',
        'Xin chào {userName},\n\nĐã quá hạn thanh toán cho "{auctionTitle}", vì vậy kết quả thắng của bạn đã bị hủy.\nTiền cọc {depositAmount} của bạn đã bị tịch thu.\nXem ví của bạn: {actionUrl}\n\nĐội ngũ BidNow',
        '["userName", "auctionTitle", "depositAmount", "actionUrl"]', true);

-- changeset hiep.nguyen:event-templates-sale-payment-received
INSERT INTO media_notification_templates (id, name, type, language, subject, body_html, body_text, variables, active)
VALUES (gen_random_uuid(), 'SALE_PAYMENT_RECEIVED_EN', 'EMAIL', 'EN', 'The buyer paid for "{auctionTitle}"',
        '<div style="font-family: Arial, sans-serif; color: #333;"><h2>Payment Received</h2><p>Hello <strong>{userName}</strong>,</p><p>The buyer has paid <strong>{bidAmount}</strong> for <strong>"{auctionTitle}"</strong>. The amount has been credited to your wallet.</p><p>Please prepare the item for shipment.</p><a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #28a745; color: #fff; text-decoration: none; border-radius: 5px;">View My Auctions</a><p>The BidNow Team</p></div>',
        'Hello {userName},

The buyer has paid {bidAmount} for "{auctionTitle}". The amount has been credited to your wallet.
Please prepare the item for shipment: {actionUrl}

The BidNow Team',
        '["userName", "auctionTitle", "bidAmount", "actionUrl"]', true),

       (gen_random_uuid(), 'SALE_PAYMENT_RECEIVED_VI', 'EMAIL', 'VI', 'Người mua đã thanh toán cho "{auctionTitle}"',
        '<div style="font-family: Arial, sans-serif; color: #333;"><h2>Đã nhận thanh toán</h2><p>Xin chào <strong>{userName}</strong>,</p><p>Người mua đã thanh toán <strong>{bidAmount}</strong> cho <strong>"{auctionTitle}"</strong>. Số tiền đã được cộng vào ví của bạn.</p><p>Vui lòng chuẩn bị gửi hàng.</p><a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #28a745; color: #fff; text-decoration: none; border-radius: 5px;">Xem phiên đấu giá của tôi</a><p>Đội ngũ BidNow</p></div>',
        'Xin chào {userName},

Người mua đã thanh toán {bidAmount} cho "{auctionTitle}". Số tiền đã được cộng vào ví của bạn.
Vui lòng chuẩn bị gửi hàng: {actionUrl}

Đội ngũ BidNow',
        '["userName", "auctionTitle", "bidAmount", "actionUrl"]', true);
```

In `db.changelog-master.xml`, add after the `06-…` include:

```xml
    <include file="db/changelog/migrations/07-event-templates.sql"/>
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q -pl media-service -am test -Dtest='AuctionLookupTest,NotificationFormatsTest,DedupKeysTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS

- [ ] **Step 5: Verify the migration applies (Docker required)**

Run: `mvn -q -pl media-service -am test -Dtest=NotificationPersistencePostgresIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS. That IT runs the full changelog, including `07`, on a fresh Postgres.

- [ ] **Step 6: Stop for review.** Suggested commit: `feat(notification): add auction lookup, notification formats and event email templates (NOTIF-104)`

---

### Task 3: AuctionNotificationHandler

**Files:**
- Create: `backend/media-service/src/main/java/com/bidnow/media/notification/handler/AuctionNotificationHandler.java`
- Test: `backend/media-service/src/test/java/com/bidnow/media/notification/handler/AuctionNotificationHandlerTest.java`

**Interfaces:**
- Consumes: `NotificationDispatcher.dispatch(NotificationIntent)` / `dispatchAll(List<NotificationIntent>)`; `NotificationIntent(userId, type, dedupKey, auctionId, title, message, actionUrl, metadata, email)` and `NotificationIntent.EmailSpec(templateBaseName, variables, transactional)`; `DedupKeys.auctionCreated/won/lost/unsold/cancelled/extended`; Task 2's `AuctionLookup`, `NotificationLinks` and `NotificationFormats`; the events `AuctionCreatedEvent {auctionId, sellerId, title}`, `AuctionEndedEvent {auctionId, auctionTitle, sellerId, winnerId, winningBidAmount}`, `AuctionCancelledEvent {auctionId, auctionTitle}` and `AuctionExtendedEvent {auctionId, auctionTitle, newEndTime: Instant}`.
- Produces `AuctionNotificationHandler` with:
  - `auctionCreated(AuctionCreatedEvent)`: seller gets `AUCTION_CREATED`, in-app + email `AUCTION_CREATED` (engagement).
  - `auctionEnded(AuctionEndedEvent)`:
    - With a winner: the winner gets an in-app `AUCTION_WON` with no email, and each other participant gets `AUCTION_LOST`, in-app + email `AUCTION_LOST` (engagement).
    - With no winner: the seller gets an in-app `AUCTION_UNSOLD`.
  - `auctionCancelled(AuctionCancelledEvent)`: each participant gets `AUCTION_CANCELLED`, in-app + email `AUCTION_CANCELLED` (engagement).
  - `auctionExtended(AuctionExtendedEvent)`: participants plus the seller get an in-app `AUCTION_EXTENDED`.

- [ ] **Step 1: Write the failing test**

```java
package com.bidnow.media.notification.handler;

import com.bidnow.common.dto.event.AuctionCancelledEvent;
import com.bidnow.common.dto.event.AuctionCreatedEvent;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import com.bidnow.common.dto.event.AuctionExtendedEvent;
import com.bidnow.media.domain.enums.NotificationType;
import com.bidnow.media.notification.DedupKeys;
import com.bidnow.media.notification.NotificationDispatcher;
import com.bidnow.media.notification.NotificationIntent;
import com.bidnow.media.notification.NotificationLinks;
import com.bidnow.media.projection.AuctionLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionNotificationHandlerTest {

    private static final UUID AUCTION = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID WINNER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID LOSER1 = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID LOSER2 = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    @Mock
    private NotificationDispatcher dispatcher;
    @Mock
    private AuctionLookup auctions;

    private AuctionNotificationHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AuctionNotificationHandler(dispatcher, auctions, new NotificationLinks("http://localhost:3000"));
    }

    @SuppressWarnings("unchecked")
    private List<NotificationIntent> dispatchedBatch() {
        ArgumentCaptor<List<NotificationIntent>> batch = ArgumentCaptor.forClass(List.class);
        verify(dispatcher).dispatchAll(batch.capture());
        return batch.getValue();
    }

    private NotificationIntent dispatchedSingle() {
        ArgumentCaptor<NotificationIntent> intent = ArgumentCaptor.forClass(NotificationIntent.class);
        verify(dispatcher).dispatch(intent.capture());
        return intent.getValue();
    }

    @Test
    void created_notifiesSellerInAppAndByEngagementEmail() {
        when(auctions.title(AUCTION, "Vintage Watch")).thenReturn("Vintage Watch");

        handler.auctionCreated(AuctionCreatedEvent.builder().auctionId(AUCTION).sellerId(SELLER).title("Vintage Watch").build());

        NotificationIntent intent = dispatchedSingle();
        assertThat(intent.userId()).isEqualTo(SELLER);
        assertThat(intent.type()).isEqualTo(NotificationType.AUCTION_CREATED);
        assertThat(intent.dedupKey()).isEqualTo(DedupKeys.auctionCreated(AUCTION));
        assertThat(intent.actionUrl()).isEqualTo("/auctions/" + AUCTION);
        assertThat(intent.email().templateBaseName()).isEqualTo("AUCTION_CREATED");
        assertThat(intent.email().transactional()).isFalse();
        assertThat(intent.email().variables())
                .containsEntry("auctionTitle", "Vintage Watch")
                .containsEntry("actionUrl", "http://localhost:3000/auctions/" + AUCTION);
    }

    @Test
    void ended_withWinner_winnerInAppOnly_losersInAppAndEmail() {
        when(auctions.title(AUCTION, "Vintage Watch")).thenReturn("Vintage Watch");
        when(auctions.participants(AUCTION)).thenReturn(List.of(WINNER, LOSER1, LOSER2));

        handler.auctionEnded(AuctionEndedEvent.builder().auctionId(AUCTION).auctionTitle("Vintage Watch")
                .sellerId(SELLER).winnerId(WINNER).winningBidAmount(new BigDecimal("150")).build());

        List<NotificationIntent> batch = dispatchedBatch();
        assertThat(batch).hasSize(3);
        NotificationIntent won = batch.get(0);
        assertThat(won.userId()).isEqualTo(WINNER);
        assertThat(won.type()).isEqualTo(NotificationType.AUCTION_WON);
        assertThat(won.dedupKey()).isEqualTo(DedupKeys.won(AUCTION));
        assertThat(won.message()).contains("$150.00");
        assertThat(won.email()).isNull();
        assertThat(batch.subList(1, 3)).allSatisfy(lost -> {
            assertThat(lost.type()).isEqualTo(NotificationType.AUCTION_LOST);
            assertThat(lost.dedupKey()).isEqualTo(DedupKeys.lost(AUCTION));
            assertThat(lost.email().templateBaseName()).isEqualTo("AUCTION_LOST");
            assertThat(lost.email().transactional()).isFalse();
            assertThat(lost.email().variables()).containsEntry("actionUrl", "http://localhost:3000/auctions");
        });
        assertThat(batch.subList(1, 3)).extracting(NotificationIntent::userId).containsExactly(LOSER1, LOSER2);
    }

    @Test
    void ended_withoutWinner_notifiesSellerUnsold() {
        when(auctions.title(AUCTION, "Vintage Watch")).thenReturn("Vintage Watch");

        handler.auctionEnded(AuctionEndedEvent.builder().auctionId(AUCTION).auctionTitle("Vintage Watch")
                .sellerId(SELLER).build());

        List<NotificationIntent> batch = dispatchedBatch();
        assertThat(batch).singleElement().satisfies(unsold -> {
            assertThat(unsold.userId()).isEqualTo(SELLER);
            assertThat(unsold.type()).isEqualTo(NotificationType.AUCTION_UNSOLD);
            assertThat(unsold.dedupKey()).isEqualTo(DedupKeys.unsold(AUCTION));
            assertThat(unsold.email()).isNull();
        });
    }

    @Test
    void cancelled_notifiesEachParticipantWithEmailToWallet() {
        when(auctions.title(AUCTION, "Vintage Watch")).thenReturn("Vintage Watch");
        when(auctions.participants(AUCTION)).thenReturn(List.of(LOSER1, LOSER2));

        handler.auctionCancelled(AuctionCancelledEvent.builder().auctionId(AUCTION).auctionTitle("Vintage Watch").build());

        List<NotificationIntent> batch = dispatchedBatch();
        assertThat(batch).extracting(NotificationIntent::userId).containsExactly(LOSER1, LOSER2);
        assertThat(batch).allSatisfy(intent -> {
            assertThat(intent.type()).isEqualTo(NotificationType.AUCTION_CANCELLED);
            assertThat(intent.dedupKey()).isEqualTo(DedupKeys.cancelled(AUCTION));
            assertThat(intent.actionUrl()).isEqualTo("/wallet");
            assertThat(intent.email().templateBaseName()).isEqualTo("AUCTION_CANCELLED");
            assertThat(intent.email().variables()).containsEntry("actionUrl", "http://localhost:3000/wallet");
        });
    }

    @Test
    void cancelled_withoutParticipants_dispatchesNothing() {
        when(auctions.title(AUCTION, null)).thenReturn(AuctionLookup.UNKNOWN_TITLE);
        when(auctions.participants(AUCTION)).thenReturn(List.of());

        handler.auctionCancelled(AuctionCancelledEvent.builder().auctionId(AUCTION).build());

        verifyNoInteractions(dispatcher);
    }

    @Test
    void extended_notifiesParticipantsAndSellerInAppOnce() {
        Instant newEnd = Instant.parse("2026-10-01T12:05:00Z");
        when(auctions.title(AUCTION, "Vintage Watch")).thenReturn("Vintage Watch");
        when(auctions.participants(AUCTION)).thenReturn(List.of(LOSER1, LOSER2));
        when(auctions.sellerId(AUCTION)).thenReturn(Optional.of(SELLER));

        handler.auctionExtended(AuctionExtendedEvent.builder().auctionId(AUCTION).auctionTitle("Vintage Watch")
                .newEndTime(newEnd).build());

        List<NotificationIntent> batch = dispatchedBatch();
        assertThat(batch).extracting(NotificationIntent::userId).containsExactly(LOSER1, LOSER2, SELLER);
        assertThat(batch).allSatisfy(intent -> {
            assertThat(intent.type()).isEqualTo(NotificationType.AUCTION_EXTENDED);
            assertThat(intent.dedupKey()).isEqualTo(DedupKeys.extended(AUCTION, newEnd));
            assertThat(intent.message()).contains("2026-10-01 12:05 UTC");
            assertThat(intent.email()).isNull();
        });
    }

    @Test
    void extended_withoutNewEndTime_dispatchesNothing() {
        handler.auctionExtended(AuctionExtendedEvent.builder().auctionId(AUCTION).build());

        verifyNoInteractions(dispatcher);
    }

    @Test
    void created_withoutSeller_dispatchesNothing() {
        handler.auctionCreated(AuctionCreatedEvent.builder().auctionId(AUCTION).title("Vintage Watch").build());

        verifyNoInteractions(dispatcher);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -pl media-service -am test -Dtest=AuctionNotificationHandlerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation FAILURE, because `AuctionNotificationHandler` does not exist.

- [ ] **Step 3: Implement**

```java
package com.bidnow.media.notification.handler;

import com.bidnow.common.dto.event.AuctionCancelledEvent;
import com.bidnow.common.dto.event.AuctionCreatedEvent;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import com.bidnow.common.dto.event.AuctionExtendedEvent;
import com.bidnow.media.domain.enums.NotificationType;
import com.bidnow.media.notification.DedupKeys;
import com.bidnow.media.notification.NotificationDispatcher;
import com.bidnow.media.notification.NotificationIntent;
import com.bidnow.media.notification.NotificationIntent.EmailSpec;
import com.bidnow.media.notification.NotificationLinks;
import com.bidnow.media.projection.AuctionLookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.bidnow.media.notification.NotificationFormats.deadline;
import static com.bidnow.media.notification.NotificationFormats.money;

/** Auction lifecycle events → seller / bidder notifications (roadmap Story 4 matrix). */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionNotificationHandler {

    private final NotificationDispatcher dispatcher;
    private final AuctionLookup auctions;
    private final NotificationLinks links;

    public void auctionCreated(AuctionCreatedEvent event) {
        UUID auctionId = event.getAuctionId();
        if (event.getSellerId() == null) {
            log.warn("AuctionCreatedEvent for auction {} has no seller - notification skipped", auctionId);
            return;
        }
        String title = auctions.title(auctionId, event.getTitle());
        String path = NotificationLinks.auctionPath(auctionId);
        dispatcher.dispatch(new NotificationIntent(event.getSellerId(), NotificationType.AUCTION_CREATED,
                DedupKeys.auctionCreated(auctionId), auctionId,
                "Your auction is live", "\"" + title + "\" is now open for bidding.", path, null,
                new EmailSpec("AUCTION_CREATED", Map.of("auctionTitle", title, "actionUrl", links.absolute(path)), false)));
    }

    /** Winner: in-app only (the "you won, please pay" email comes from PaymentEvent REQUIRED). Losers: in-app + email. */
    public void auctionEnded(AuctionEndedEvent event) {
        UUID auctionId = event.getAuctionId();
        String title = auctions.title(auctionId, event.getAuctionTitle());
        String path = NotificationLinks.auctionPath(auctionId);
        List<NotificationIntent> intents = new ArrayList<>();
        UUID winner = event.getWinnerId();

        if (winner == null) {
            if (event.getSellerId() != null) {
                intents.add(new NotificationIntent(event.getSellerId(), NotificationType.AUCTION_UNSOLD,
                        DedupKeys.unsold(auctionId), auctionId, "Auction ended without a sale",
                        "\"" + title + "\" ended with no winning bid.", path, null, null));
            }
        } else {
            intents.add(new NotificationIntent(winner, NotificationType.AUCTION_WON, DedupKeys.won(auctionId), auctionId,
                    "You won!", "You won \"" + title + "\" with a bid of " + money(event.getWinningBidAmount())
                            + ". Check your email for payment details.", path, null, null));
            EmailSpec lostEmail = new EmailSpec("AUCTION_LOST",
                    Map.of("auctionTitle", title, "actionUrl", links.absolute(NotificationLinks.AUCTIONS_PATH)), false);
            for (UUID bidder : auctions.participants(auctionId)) {
                if (!bidder.equals(winner)) {
                    intents.add(new NotificationIntent(bidder, NotificationType.AUCTION_LOST, DedupKeys.lost(auctionId),
                            auctionId, "Auction ended", "\"" + title + "\" has ended. You were not the highest bidder.",
                            path, null, lostEmail));
                }
            }
        }
        dispatchIfAny(intents);
    }

    public void auctionCancelled(AuctionCancelledEvent event) {
        UUID auctionId = event.getAuctionId();
        String title = auctions.title(auctionId, event.getAuctionTitle());
        EmailSpec email = new EmailSpec("AUCTION_CANCELLED",
                Map.of("auctionTitle", title, "actionUrl", links.absolute(NotificationLinks.WALLET_PATH)), false);
        List<NotificationIntent> intents = new ArrayList<>();
        for (UUID bidder : auctions.participants(auctionId)) {
            intents.add(new NotificationIntent(bidder, NotificationType.AUCTION_CANCELLED, DedupKeys.cancelled(auctionId),
                    auctionId, "Auction cancelled",
                    "\"" + title + "\" was cancelled. Any deposit you placed is being refunded to your wallet.",
                    NotificationLinks.WALLET_PATH, null, email));
        }
        dispatchIfAny(intents);
    }

    /** In-app only, to bidders and the seller; one notification per extension (keyed on the new end time). */
    public void auctionExtended(AuctionExtendedEvent event) {
        UUID auctionId = event.getAuctionId();
        if (event.getNewEndTime() == null) {
            log.warn("AuctionExtendedEvent for auction {} has no new end time - notification skipped", auctionId);
            return;
        }
        String title = auctions.title(auctionId, event.getAuctionTitle());
        Set<UUID> recipients = new LinkedHashSet<>(auctions.participants(auctionId));
        auctions.sellerId(auctionId).ifPresent(recipients::add);
        String message = "\"" + title + "\" was extended to " + deadline(event.getNewEndTime())
                + " after a last-minute bid.";
        String dedupKey = DedupKeys.extended(auctionId, event.getNewEndTime());
        List<NotificationIntent> intents = new ArrayList<>();
        for (UUID userId : recipients) {
            intents.add(new NotificationIntent(userId, NotificationType.AUCTION_EXTENDED, dedupKey, auctionId,
                    "Auction extended", message, NotificationLinks.auctionPath(auctionId), null, null));
        }
        dispatchIfAny(intents);
    }

    private void dispatchIfAny(List<NotificationIntent> intents) {
        if (!intents.isEmpty()) {
            dispatcher.dispatchAll(intents);
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -pl media-service -am test -Dtest=AuctionNotificationHandlerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (8 tests)

- [ ] **Step 5: Stop for review.** Suggested commit: `feat(notification): notify sellers and bidders of auction lifecycle events (NOTIF-104)`

---

### Task 4: PaymentNotificationHandler

**Files:**
- Create: `backend/media-service/src/main/java/com/bidnow/media/notification/handler/PaymentNotificationHandler.java`
- Test: `backend/media-service/src/test/java/com/bidnow/media/notification/handler/PaymentNotificationHandlerTest.java`

**Interfaces:**
- Consumes: the same dispatcher, intent, `DedupKeys` (`payment(String, UUID)`, `refund(UUID)`), `AuctionLookup`, `NotificationLinks` and `NotificationFormats` as Task 3. Also:
  - `PaymentEvent {auctionId, auctionTitle, userId (winner), sellerId, amount, depositAmount, remaining, deadline: Instant, insufficientFunds: Boolean, paymentType: "REQUIRED"|"COMPLETED"|"FAILED"|…}`
  - `DepositRefundedEvent {userId, auctionId, amount, reason: "AUCTION_LOST"|"AUCTION_CANCELLED"}`
- Produces `PaymentNotificationHandler` with:
  - `paymentEvent(PaymentEvent)`:
    - `REQUIRED`: the winner gets `PAYMENT_REQUIRED`, in-app + transactional email `AUCTION_WON` (variables `auctionTitle, bidAmount, paymentDeadline, actionUrl`).
    - `COMPLETED`:
      - the winner gets `PAYMENT_RECEIVED`, in-app + transactional email `PAYMENT_SUCCESSFUL` (variables `auctionTitle, bidAmount, actionUrl`);
      - the seller gets `PAYMENT_RECEIVED`, in-app + transactional email `SALE_PAYMENT_RECEIVED` (variables `auctionTitle, bidAmount, actionUrl` → `/seller/auctions`).
    - `FAILED`: the winner gets `PAYMENT_FAILED`, in-app + transactional email `PAYMENT_FAILED` (variables `auctionTitle, depositAmount, actionUrl`).
    - Any other type is logged at debug, with no notification.
  - `depositRefunded(DepositRefundedEvent)`: the user gets `DEPOSIT_REFUNDED` in-app. The engagement email `DEPOSIT_REFUNDED` goes out only when the reason is `AUCTION_LOST`.

- [ ] **Step 1: Write the failing test**

```java
package com.bidnow.media.notification.handler;

import com.bidnow.common.dto.event.DepositRefundedEvent;
import com.bidnow.common.dto.event.PaymentEvent;
import com.bidnow.media.domain.enums.NotificationType;
import com.bidnow.media.notification.DedupKeys;
import com.bidnow.media.notification.NotificationDispatcher;
import com.bidnow.media.notification.NotificationIntent;
import com.bidnow.media.notification.NotificationLinks;
import com.bidnow.media.projection.AuctionLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentNotificationHandlerTest {

    private static final UUID AUCTION = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID WINNER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final Instant DEADLINE = Instant.parse("2026-10-03T10:00:00Z");

    @Mock
    private NotificationDispatcher dispatcher;
    @Mock
    private AuctionLookup auctions;

    private PaymentNotificationHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PaymentNotificationHandler(dispatcher, auctions, new NotificationLinks("http://localhost:3000"));
    }

    private NotificationIntent dispatchedSingle() {
        ArgumentCaptor<NotificationIntent> intent = ArgumentCaptor.forClass(NotificationIntent.class);
        verify(dispatcher).dispatch(intent.capture());
        return intent.getValue();
    }

    private static PaymentEvent.PaymentEventBuilder payment(String type) {
        return PaymentEvent.builder().auctionId(AUCTION).userId(WINNER).sellerId(SELLER).paymentType(type)
                .amount(new BigDecimal("1500")).depositAmount(new BigDecimal("150")).remaining(new BigDecimal("1350"))
                .deadline(DEADLINE);
    }

    @Test
    void required_sendsTheWinnerEmailAsTransactional() {
        when(auctions.title(AUCTION, null)).thenReturn("Vintage Watch");

        handler.paymentEvent(payment("REQUIRED").insufficientFunds(false).build());

        NotificationIntent intent = dispatchedSingle();
        assertThat(intent.userId()).isEqualTo(WINNER);
        assertThat(intent.type()).isEqualTo(NotificationType.PAYMENT_REQUIRED);
        assertThat(intent.dedupKey()).isEqualTo(DedupKeys.payment("REQUIRED", AUCTION));
        assertThat(intent.actionUrl()).isEqualTo("/wallet");
        assertThat(intent.message()).contains("$1,350.00").contains("2026-10-03 10:00 UTC").doesNotContain("top up");
        assertThat(intent.email().templateBaseName()).isEqualTo("AUCTION_WON");
        assertThat(intent.email().transactional()).isTrue();
        assertThat(intent.email().variables())
                .containsEntry("auctionTitle", "Vintage Watch")
                .containsEntry("bidAmount", "$1,500.00")
                .containsEntry("paymentDeadline", "2026-10-03 10:00 UTC")
                .containsEntry("actionUrl", "http://localhost:3000/wallet");
    }

    @Test
    void required_withInsufficientFunds_asksToTopUp() {
        when(auctions.title(AUCTION, null)).thenReturn("Vintage Watch");

        handler.paymentEvent(payment("REQUIRED").insufficientFunds(true).build());

        assertThat(dispatchedSingle().message()).contains("top up");
    }

    @Test
    @SuppressWarnings("unchecked")
    void completed_emailsWinnerAndSeller() {
        when(auctions.title(AUCTION, null)).thenReturn("Vintage Watch");

        handler.paymentEvent(payment("COMPLETED").build());

        ArgumentCaptor<List<NotificationIntent>> batch = ArgumentCaptor.forClass(List.class);
        verify(dispatcher).dispatchAll(batch.capture());
        assertThat(batch.getValue()).hasSize(2);
        NotificationIntent winner = batch.getValue().get(0);
        assertThat(winner.userId()).isEqualTo(WINNER);
        assertThat(winner.type()).isEqualTo(NotificationType.PAYMENT_RECEIVED);
        assertThat(winner.email().templateBaseName()).isEqualTo("PAYMENT_SUCCESSFUL");
        assertThat(winner.email().transactional()).isTrue();
        assertThat(winner.email().variables()).containsEntry("bidAmount", "$1,500.00");
        NotificationIntent seller = batch.getValue().get(1);
        assertThat(seller.userId()).isEqualTo(SELLER);
        assertThat(seller.type()).isEqualTo(NotificationType.PAYMENT_RECEIVED);
        assertThat(seller.actionUrl()).isEqualTo("/seller/auctions");
        assertThat(seller.email().templateBaseName()).isEqualTo("SALE_PAYMENT_RECEIVED");
        assertThat(seller.email().transactional()).isTrue();
        assertThat(seller.email().variables())
                .containsEntry("bidAmount", "$1,500.00")
                .containsEntry("actionUrl", "http://localhost:3000/seller/auctions");
        assertThat(batch.getValue()).extracting(NotificationIntent::dedupKey)
                .containsOnly(DedupKeys.payment("COMPLETED", AUCTION));
    }

    @Test
    void failed_sendsTransactionalForfeitEmail() {
        when(auctions.title(AUCTION, null)).thenReturn("Vintage Watch");

        handler.paymentEvent(payment("FAILED").build());

        NotificationIntent intent = dispatchedSingle();
        assertThat(intent.type()).isEqualTo(NotificationType.PAYMENT_FAILED);
        assertThat(intent.dedupKey()).isEqualTo(DedupKeys.payment("FAILED", AUCTION));
        assertThat(intent.email().templateBaseName()).isEqualTo("PAYMENT_FAILED");
        assertThat(intent.email().transactional()).isTrue();
        assertThat(intent.email().variables()).containsEntry("depositAmount", "$150.00");
    }

    @Test
    void unknownPaymentType_dispatchesNothing() {
        handler.paymentEvent(payment("REMINDER_24H").build());

        verifyNoInteractions(dispatcher);
    }

    @Test
    void refund_afterLoss_emailsWithProjectedTitle() {
        when(auctions.title(AUCTION, null)).thenReturn("Camera");

        handler.depositRefunded(DepositRefundedEvent.builder().userId(WINNER).auctionId(AUCTION)
                .amount(new BigDecimal("150")).reason("AUCTION_LOST").build());

        NotificationIntent intent = dispatchedSingle();
        assertThat(intent.type()).isEqualTo(NotificationType.DEPOSIT_REFUNDED);
        assertThat(intent.dedupKey()).isEqualTo(DedupKeys.refund(AUCTION));
        assertThat(intent.message()).contains("$150.00").contains("Camera");
        assertThat(intent.email().templateBaseName()).isEqualTo("DEPOSIT_REFUNDED");
        assertThat(intent.email().transactional()).isFalse();
        assertThat(intent.email().variables()).containsEntry("auctionTitle", "Camera");
    }

    @Test
    void refund_afterCancellation_isInAppOnly() {
        when(auctions.title(AUCTION, null)).thenReturn(AuctionLookup.UNKNOWN_TITLE);

        handler.depositRefunded(DepositRefundedEvent.builder().userId(WINNER).auctionId(AUCTION)
                .amount(new BigDecimal("150")).reason("AUCTION_CANCELLED").build());

        NotificationIntent intent = dispatchedSingle();
        assertThat(intent.message()).contains("your auction");
        assertThat(intent.email()).isNull();
    }

    @Test
    void paymentWithoutWinner_dispatchesNothing() {
        handler.paymentEvent(PaymentEvent.builder().auctionId(AUCTION).paymentType("REQUIRED").build());

        verifyNoInteractions(dispatcher);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -pl media-service -am test -Dtest=PaymentNotificationHandlerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation FAILURE, because `PaymentNotificationHandler` does not exist.

- [ ] **Step 3: Implement**

```java
package com.bidnow.media.notification.handler;

import com.bidnow.common.dto.event.DepositRefundedEvent;
import com.bidnow.common.dto.event.PaymentEvent;
import com.bidnow.media.domain.enums.NotificationType;
import com.bidnow.media.notification.DedupKeys;
import com.bidnow.media.notification.NotificationDispatcher;
import com.bidnow.media.notification.NotificationIntent;
import com.bidnow.media.notification.NotificationIntent.EmailSpec;
import com.bidnow.media.notification.NotificationLinks;
import com.bidnow.media.projection.AuctionLookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.bidnow.media.notification.NotificationFormats.deadline;
import static com.bidnow.media.notification.NotificationFormats.money;

/**
 * Wallet events → winner / seller / bidder notifications. PaymentEvent REQUIRED carries the single
 * "you won, please pay" email (roadmap Decision 3); payment emails are transactional (Decision 5).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentNotificationHandler {

    private final NotificationDispatcher dispatcher;
    private final AuctionLookup auctions;
    private final NotificationLinks links;

    public void paymentEvent(PaymentEvent event) {
        if (event.getUserId() == null || event.getAuctionId() == null) {
            log.warn("PaymentEvent {} without winner or auction - notification skipped", event.getPaymentType());
            return;
        }
        switch (String.valueOf(event.getPaymentType())) {
            case "REQUIRED" -> paymentRequired(event);
            case "COMPLETED" -> paymentCompleted(event);
            case "FAILED" -> paymentFailed(event);
            default -> log.debug("PaymentEvent {} for auction {} has no notification",
                    event.getPaymentType(), event.getAuctionId());
        }
    }

    public void depositRefunded(DepositRefundedEvent event) {
        UUID auctionId = event.getAuctionId();
        String title = auctions.title(auctionId, null);
        // The seeded DEPOSIT_REFUNDED copy says "because you did not win"; cancellations are covered by AUCTION_CANCELLED
        EmailSpec email = "AUCTION_LOST".equals(event.getReason())
                ? new EmailSpec("DEPOSIT_REFUNDED",
                        Map.of("auctionTitle", title, "actionUrl", links.absolute(NotificationLinks.WALLET_PATH)), false)
                : null;
        dispatcher.dispatch(new NotificationIntent(event.getUserId(), NotificationType.DEPOSIT_REFUNDED,
                DedupKeys.refund(auctionId), auctionId, "Deposit refunded",
                "Your deposit of " + money(event.getAmount()) + " for \"" + title + "\" was refunded to your wallet.",
                NotificationLinks.WALLET_PATH, null, email));
    }

    private void paymentRequired(PaymentEvent event) {
        UUID auctionId = event.getAuctionId();
        String title = auctions.title(auctionId, event.getAuctionTitle());
        String due = deadline(event.getDeadline());
        String message = Boolean.TRUE.equals(event.getInsufficientFunds())
                ? "You won \"" + title + "\". Please top up your wallet and pay " + money(event.getRemaining())
                        + " by " + due + "."
                : "You won \"" + title + "\". Please complete your payment of " + money(event.getRemaining())
                        + " by " + due + ".";
        dispatcher.dispatch(new NotificationIntent(event.getUserId(), NotificationType.PAYMENT_REQUIRED,
                DedupKeys.payment("REQUIRED", auctionId), auctionId, "Payment required", message,
                NotificationLinks.WALLET_PATH, null,
                new EmailSpec("AUCTION_WON", Map.of(
                        "auctionTitle", title,
                        "bidAmount", money(event.getAmount()),
                        "paymentDeadline", due,
                        "actionUrl", links.absolute(NotificationLinks.WALLET_PATH)), true)));
    }

    private void paymentCompleted(PaymentEvent event) {
        UUID auctionId = event.getAuctionId();
        String title = auctions.title(auctionId, event.getAuctionTitle());
        String dedupKey = DedupKeys.payment("COMPLETED", auctionId);
        List<NotificationIntent> intents = new ArrayList<>();
        intents.add(new NotificationIntent(event.getUserId(), NotificationType.PAYMENT_RECEIVED, dedupKey, auctionId,
                "Payment complete", "You paid " + money(event.getAmount()) + " for \"" + title + "\".",
                NotificationLinks.WALLET_PATH, null,
                new EmailSpec("PAYMENT_SUCCESSFUL", Map.of(
                        "auctionTitle", title,
                        "bidAmount", money(event.getAmount()),
                        "actionUrl", links.absolute(NotificationLinks.WALLET_PATH)), true)));
        UUID seller = event.getSellerId() != null ? event.getSellerId() : auctions.sellerId(auctionId).orElse(null);
        if (seller != null) {
            intents.add(new NotificationIntent(seller, NotificationType.PAYMENT_RECEIVED, dedupKey, auctionId,
                    "Buyer paid", "The buyer paid " + money(event.getAmount()) + " for \"" + title
                            + "\". The amount was credited to your wallet.",
                    NotificationLinks.SELLER_AUCTIONS_PATH, null,
                    new EmailSpec("SALE_PAYMENT_RECEIVED", Map.of(
                            "auctionTitle", title,
                            "bidAmount", money(event.getAmount()),
                            "actionUrl", links.absolute(NotificationLinks.SELLER_AUCTIONS_PATH)), true)));
        }
        dispatcher.dispatchAll(intents);
    }

    private void paymentFailed(PaymentEvent event) {
        UUID auctionId = event.getAuctionId();
        String title = auctions.title(auctionId, event.getAuctionTitle());
        dispatcher.dispatch(new NotificationIntent(event.getUserId(), NotificationType.PAYMENT_FAILED,
                DedupKeys.payment("FAILED", auctionId), auctionId, "Payment deadline missed",
                "The payment deadline for \"" + title + "\" has passed. Your deposit of "
                        + money(event.getDepositAmount()) + " was forfeited.",
                NotificationLinks.WALLET_PATH, null,
                new EmailSpec("PAYMENT_FAILED", Map.of(
                        "auctionTitle", title,
                        "depositAmount", money(event.getDepositAmount()),
                        "actionUrl", links.absolute(NotificationLinks.WALLET_PATH)), true)));
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -pl media-service -am test -Dtest=PaymentNotificationHandlerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (8 tests)

- [ ] **Step 5: Stop for review.** Suggested commit: `feat(notification): notify winners, sellers and bidders of payment and refund events (NOTIF-104)`

---

### Task 5: BidNotificationHandler and consumer wiring

**Files:**
- Create: `backend/media-service/src/main/java/com/bidnow/media/notification/handler/BidNotificationHandler.java`
- Modify: `backend/media-service/src/main/java/com/bidnow/media/kafka/NotificationKafkaConsumer.java`
- Modify: `backend/media-service/src/main/java/com/bidnow/media/service/NotificationService.java`
- Modify: `backend/media-service/src/main/java/com/bidnow/media/service/impl/NotificationServiceImpl.java`
- Test: `backend/media-service/src/test/java/com/bidnow/media/notification/handler/BidNotificationHandlerTest.java`
- Test: `backend/media-service/src/test/java/com/bidnow/media/kafka/NotificationKafkaConsumerTest.java`

**Interfaces:**
- Consumes: Task 3 `AuctionNotificationHandler` (4 methods), Task 4 `PaymentNotificationHandler` (2 methods), the existing `NotificationService.handleUserVerificationRequested/handleUserRegistered`, and `BidPlacedEvent {auctionId, auctionTitle, bidAmount, totalBids: Integer}`.
- Produces: `BidNotificationHandler.bidPlaced(BidPlacedEvent)`. When `totalBids == 1`, the seller from the projection gets an in-app `FIRST_BID` (dedup `DedupKeys.firstBid`). Anything else does nothing, until Story 5.
- Produces: `NotificationKafkaConsumer`, one listener per topic on `${spring.kafka.consumer.group-id}`:

| Topic | Delegates to |
|---|---|
| `user-verification-requested-topic` | `NotificationService.handleUserVerificationRequested` |
| `user-registered-topic` | `NotificationService.handleUserRegistered` |
| `auction-created-topic` | `AuctionNotificationHandler.auctionCreated` |
| `bid-placed-topic` | `BidNotificationHandler.bidPlaced` |
| `auction-ended-topic` | `AuctionNotificationHandler.auctionEnded` |
| `auction-cancelled-topic` | `AuctionNotificationHandler.auctionCancelled` |
| `auction-extended-topic` | `AuctionNotificationHandler.auctionExtended` |
| `payment-event-topic` | `PaymentNotificationHandler.paymentEvent` |
| `deposit-refunded-topic` | `PaymentNotificationHandler.depositRefunded` |

- Produces: `NotificationService` keeps only `handleUserVerificationRequested` and `handleUserRegistered`.

- [ ] **Step 1: Write the failing tests**

`BidNotificationHandlerTest.java`:

```java
package com.bidnow.media.notification.handler;

import com.bidnow.common.dto.event.BidPlacedEvent;
import com.bidnow.media.domain.enums.NotificationType;
import com.bidnow.media.notification.DedupKeys;
import com.bidnow.media.notification.NotificationDispatcher;
import com.bidnow.media.notification.NotificationIntent;
import com.bidnow.media.projection.AuctionLookup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidNotificationHandlerTest {

    private static final UUID AUCTION = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000005");

    @Mock
    private NotificationDispatcher dispatcher;
    @Mock
    private AuctionLookup auctions;

    @InjectMocks
    private BidNotificationHandler handler;

    private static BidPlacedEvent bid(int totalBids) {
        return BidPlacedEvent.builder().auctionId(AUCTION).auctionTitle("Vintage Watch")
                .bidAmount(new BigDecimal("100")).totalBids(totalBids).build();
    }

    @Test
    void firstBid_notifiesSellerInApp() {
        when(auctions.sellerId(AUCTION)).thenReturn(Optional.of(SELLER));
        when(auctions.title(AUCTION, "Vintage Watch")).thenReturn("Vintage Watch");

        handler.bidPlaced(bid(1));

        ArgumentCaptor<NotificationIntent> intent = ArgumentCaptor.forClass(NotificationIntent.class);
        verify(dispatcher).dispatch(intent.capture());
        assertThat(intent.getValue().userId()).isEqualTo(SELLER);
        assertThat(intent.getValue().type()).isEqualTo(NotificationType.FIRST_BID);
        assertThat(intent.getValue().dedupKey()).isEqualTo(DedupKeys.firstBid(AUCTION));
        assertThat(intent.getValue().message()).contains("$100.00");
        assertThat(intent.getValue().email()).isNull();
    }

    @Test
    void laterBids_doNothingYet() {
        handler.bidPlaced(bid(2));

        verifyNoInteractions(dispatcher, auctions);
    }

    @Test
    void firstBid_unknownSeller_isSkipped() {
        when(auctions.sellerId(AUCTION)).thenReturn(Optional.empty());

        handler.bidPlaced(bid(1));

        verifyNoInteractions(dispatcher);
    }
}
```

`NotificationKafkaConsumerTest.java`:

```java
package com.bidnow.media.kafka;

import com.bidnow.common.dto.event.AuctionCancelledEvent;
import com.bidnow.common.dto.event.AuctionCreatedEvent;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import com.bidnow.common.dto.event.AuctionExtendedEvent;
import com.bidnow.common.dto.event.BidPlacedEvent;
import com.bidnow.common.dto.event.DepositRefundedEvent;
import com.bidnow.common.dto.event.PaymentEvent;
import com.bidnow.common.dto.event.UserRegisteredEvent;
import com.bidnow.common.dto.event.UserVerificationRequestedEvent;
import com.bidnow.media.notification.handler.AuctionNotificationHandler;
import com.bidnow.media.notification.handler.BidNotificationHandler;
import com.bidnow.media.notification.handler.PaymentNotificationHandler;
import com.bidnow.media.service.NotificationService;
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
class NotificationKafkaConsumerTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private AuctionNotificationHandler auctionHandler;
    @Mock
    private BidNotificationHandler bidHandler;
    @Mock
    private PaymentNotificationHandler paymentHandler;

    @InjectMocks
    private NotificationKafkaConsumer consumer;

    @Test
    void delegatesEachTopicToItsHandler() {
        UUID id = UUID.randomUUID();
        UserVerificationRequestedEvent otp = UserVerificationRequestedEvent.builder().userId(id).build();
        UserRegisteredEvent registered = UserRegisteredEvent.builder().userId(id).build();
        AuctionCreatedEvent created = AuctionCreatedEvent.builder().auctionId(id).build();
        BidPlacedEvent bid = BidPlacedEvent.builder().auctionId(id).build();
        AuctionEndedEvent ended = AuctionEndedEvent.builder().auctionId(id).build();
        AuctionCancelledEvent cancelled = AuctionCancelledEvent.builder().auctionId(id).build();
        AuctionExtendedEvent extended = AuctionExtendedEvent.builder().auctionId(id).build();
        PaymentEvent payment = PaymentEvent.builder().auctionId(id).build();
        DepositRefundedEvent refunded = DepositRefundedEvent.builder().auctionId(id).build();

        consumer.consumeUserVerificationRequested(otp);
        consumer.consumeUserRegistered(registered);
        consumer.consumeAuctionCreated(created);
        consumer.consumeBidPlaced(bid);
        consumer.consumeAuctionEnded(ended);
        consumer.consumeAuctionCancelled(cancelled);
        consumer.consumeAuctionExtended(extended);
        consumer.consumePaymentEvent(payment);
        consumer.consumeDepositRefunded(refunded);

        verify(notificationService).handleUserVerificationRequested(otp);
        verify(notificationService).handleUserRegistered(registered);
        verify(auctionHandler).auctionCreated(created);
        verify(bidHandler).bidPlaced(bid);
        verify(auctionHandler).auctionEnded(ended);
        verify(auctionHandler).auctionCancelled(cancelled);
        verify(auctionHandler).auctionExtended(extended);
        verify(paymentHandler).paymentEvent(payment);
        verify(paymentHandler).depositRefunded(refunded);
    }

    @Test
    void everyListenerUsesTheSharedNotificationGroup() {
        Map<String, KafkaListener> listeners = Arrays.stream(NotificationKafkaConsumer.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(KafkaListener.class))
                .collect(Collectors.toMap(Method::getName, m -> m.getAnnotation(KafkaListener.class)));

        assertThat(listeners).hasSize(9);
        assertThat(listeners.get("consumeAuctionCancelled").topics()).containsExactly("auction-cancelled-topic");
        assertThat(listeners.get("consumeAuctionExtended").topics()).containsExactly("auction-extended-topic");
        assertThat(listeners.get("consumeDepositRefunded").topics()).containsExactly("deposit-refunded-topic");
        listeners.values().forEach(l -> assertThat(l.groupId()).isEqualTo("${spring.kafka.consumer.group-id}"));
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q -pl media-service -am test -Dtest='BidNotificationHandlerTest,NotificationKafkaConsumerTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation FAILURE, because `BidNotificationHandler` and the new consumer methods and constructor do not exist.

- [ ] **Step 3: Implement**

`BidNotificationHandler.java`:

```java
package com.bidnow.media.notification.handler;

import com.bidnow.common.dto.event.BidPlacedEvent;
import com.bidnow.media.domain.enums.NotificationType;
import com.bidnow.media.notification.DedupKeys;
import com.bidnow.media.notification.NotificationDispatcher;
import com.bidnow.media.notification.NotificationIntent;
import com.bidnow.media.notification.NotificationLinks;
import com.bidnow.media.projection.AuctionLookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

import static com.bidnow.media.notification.NotificationFormats.money;

/** Bid events → seller notifications. Only the first bid for now; outbid / new-bid batching is Story 5. */
@Slf4j
@Component
@RequiredArgsConstructor
public class BidNotificationHandler {

    private final NotificationDispatcher dispatcher;
    private final AuctionLookup auctions;

    public void bidPlaced(BidPlacedEvent event) {
        if (event.getTotalBids() == null || event.getTotalBids() != 1) {
            return;
        }
        UUID auctionId = event.getAuctionId();
        Optional<UUID> seller = auctions.sellerId(auctionId);
        if (seller.isEmpty()) {
            log.warn("First bid on auction {} but its seller is not projected yet - notification skipped", auctionId);
            return;
        }
        String title = auctions.title(auctionId, event.getAuctionTitle());
        dispatcher.dispatch(new NotificationIntent(seller.get(), NotificationType.FIRST_BID,
                DedupKeys.firstBid(auctionId), auctionId, "First bid received",
                "\"" + title + "\" received its first bid: " + money(event.getBidAmount()) + ".",
                NotificationLinks.auctionPath(auctionId), null, null));
    }
}
```

Replace `NotificationService.java` with:

```java
package com.bidnow.media.service;

import com.bidnow.common.dto.event.UserRegisteredEvent;
import com.bidnow.common.dto.event.UserVerificationRequestedEvent;

/** Account-level notifications. Auction, bid and payment events are handled in notification.handler. */
public interface NotificationService {
    void handleUserVerificationRequested(UserVerificationRequestedEvent event);

    void handleUserRegistered(UserRegisteredEvent event);
}
```

In `NotificationServiceImpl.java`, delete the four stub methods `handleAuctionCreated`, `handleBidPlaced`, `handleAuctionEnded` and `handlePaymentEvent`, along with their section comment `// Remaining handlers (stubs — implemented in later issues)` and its divider lines. Also delete the now-unused imports of `AuctionCreatedEvent`, `AuctionEndedEvent`, `BidPlacedEvent` and `PaymentEvent`. Nothing else in the file changes.

Replace `NotificationKafkaConsumer.java` with:

```java
package com.bidnow.media.kafka;

import com.bidnow.common.annotation.Loggable;
import com.bidnow.common.dto.event.AuctionCancelledEvent;
import com.bidnow.common.dto.event.AuctionCreatedEvent;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import com.bidnow.common.dto.event.AuctionExtendedEvent;
import com.bidnow.common.dto.event.BidPlacedEvent;
import com.bidnow.common.dto.event.DepositRefundedEvent;
import com.bidnow.common.dto.event.PaymentEvent;
import com.bidnow.common.dto.event.UserRegisteredEvent;
import com.bidnow.common.dto.event.UserVerificationRequestedEvent;
import com.bidnow.media.notification.handler.AuctionNotificationHandler;
import com.bidnow.media.notification.handler.BidNotificationHandler;
import com.bidnow.media.notification.handler.PaymentNotificationHandler;
import com.bidnow.media.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Domain events → notifications, on the shared notification consumer group (one listener per topic). */
@Component
@RequiredArgsConstructor
@Slf4j
@Loggable
public class NotificationKafkaConsumer {

    private final NotificationService notificationService;
    private final AuctionNotificationHandler auctionHandler;
    private final BidNotificationHandler bidHandler;
    private final PaymentNotificationHandler paymentHandler;

    @KafkaListener(topics = "user-verification-requested-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeUserVerificationRequested(UserVerificationRequestedEvent event) {
        log.info("Received UserVerificationRequestedEvent for user: {}", event.getUserId());
        notificationService.handleUserVerificationRequested(event);
    }

    @KafkaListener(topics = "user-registered-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeUserRegistered(UserRegisteredEvent event) {
        log.info("Received UserRegisteredEvent for user: {}", event.getUserId());
        notificationService.handleUserRegistered(event);
    }

    @KafkaListener(topics = "auction-created-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeAuctionCreated(AuctionCreatedEvent event) {
        log.info("Received AuctionCreatedEvent for auction: {}", event.getAuctionId());
        auctionHandler.auctionCreated(event);
    }

    @KafkaListener(topics = "bid-placed-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeBidPlaced(BidPlacedEvent event) {
        log.info("Received BidPlacedEvent for auction: {}", event.getAuctionId());
        bidHandler.bidPlaced(event);
    }

    @KafkaListener(topics = "auction-ended-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeAuctionEnded(AuctionEndedEvent event) {
        log.info("Received AuctionEndedEvent for auction: {}", event.getAuctionId());
        auctionHandler.auctionEnded(event);
    }

    @KafkaListener(topics = "auction-cancelled-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeAuctionCancelled(AuctionCancelledEvent event) {
        log.info("Received AuctionCancelledEvent for auction: {}", event.getAuctionId());
        auctionHandler.auctionCancelled(event);
    }

    @KafkaListener(topics = "auction-extended-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeAuctionExtended(AuctionExtendedEvent event) {
        log.info("Received AuctionExtendedEvent for auction: {}", event.getAuctionId());
        auctionHandler.auctionExtended(event);
    }

    @KafkaListener(topics = "payment-event-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void consumePaymentEvent(PaymentEvent event) {
        log.info("Received PaymentEvent {} for auction: {}", event.getPaymentType(), event.getAuctionId());
        paymentHandler.paymentEvent(event);
    }

    @KafkaListener(topics = "deposit-refunded-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeDepositRefunded(DepositRefundedEvent event) {
        log.info("Received DepositRefundedEvent for auction: {}", event.getAuctionId());
        paymentHandler.depositRefunded(event);
    }
}
```

The existing listeners logged the whole event (`{}` with `event`). The new ones log only IDs, so emails and OTP codes stay out of logs.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q -pl media-service -am test -Dtest='BidNotificationHandlerTest,NotificationKafkaConsumerTest,NotificationServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (3 + 2 + 4 tests)

- [ ] **Step 5: Run the default suite**

Run: `mvn -q -pl media-service -am test`
Expected: PASS

- [ ] **Step 6: Stop for review.** Suggested commit: `feat(notification): route domain events to notification handlers (NOTIF-104)`

---

### Task 6: Template-variable coverage (Postgres IT)

**Files:**
- Test: `backend/media-service/src/test/java/com/bidnow/media/notification/NotificationTemplatesPostgresIT.java`

**Interfaces:**
- Consumes: Tasks 3–5 handlers (constructed with Mockito mocks); `NotificationServiceImpl` for the welcome email; the Liquibase changelog including `07`; the `PostgresContainerSupport` container.
- Produces: a guarantee that every email a handler can send has an active EN **and** VI template. Each template must render with the handler's variables plus the dispatcher's `userName`, leaving no `{placeholder}` unresolved.

- [ ] **Step 1: Write the test**

```java
package com.bidnow.media.notification;

import com.bidnow.bdd.container.PostgresContainerSupport;
import com.bidnow.common.dto.event.AuctionCancelledEvent;
import com.bidnow.common.dto.event.AuctionCreatedEvent;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import com.bidnow.common.dto.event.DepositRefundedEvent;
import com.bidnow.common.dto.event.PaymentEvent;
import com.bidnow.common.dto.event.UserRegisteredEvent;
import com.bidnow.media.notification.handler.AuctionNotificationHandler;
import com.bidnow.media.notification.handler.PaymentNotificationHandler;
import com.bidnow.media.projection.AuctionLookup;
import com.bidnow.media.service.EmailService;
import com.bidnow.media.service.impl.NotificationServiceImpl;
import liquibase.integration.spring.SpringLiquibase;
import org.apache.commons.text.StringSubstitutor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Every email a handler sends must have active EN and VI templates that render completely with the handler's
 * variables plus the dispatcher-supplied userName. Needs Docker; run explicitly:
 * mvn -q -pl media-service -am test -Dtest=NotificationTemplatesPostgresIT -Dsurefire.failIfNoSpecifiedTests=false
 */
class NotificationTemplatesPostgresIT {

    private static final Pattern UNRESOLVED = Pattern.compile("\\{[A-Za-z]+}");
    private static final UUID AUCTION = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID WINNER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID LOSER = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() throws Exception {
        PostgreSQLContainer<?> pg = PostgresContainerSupport.POSTGRES;
        DriverManagerDataSource dataSource = new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.xml");
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.afterPropertiesSet();
        jdbc = new JdbcTemplate(dataSource);
    }

    /** Runs every handler path that sends email and collects the EmailSpecs it produced. */
    @SuppressWarnings("unchecked")
    private static List<NotificationIntent.EmailSpec> emailSpecsFromAllHandlers() {
        NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);
        AuctionLookup auctions = mock(AuctionLookup.class);
        when(auctions.title(any(), any())).thenReturn("Vintage Watch");
        when(auctions.participants(AUCTION)).thenReturn(List.of(WINNER, LOSER));
        NotificationLinks links = new NotificationLinks("http://localhost:3000");

        AuctionNotificationHandler auctionHandler = new AuctionNotificationHandler(dispatcher, auctions, links);
        PaymentNotificationHandler paymentHandler = new PaymentNotificationHandler(dispatcher, auctions, links);
        NotificationServiceImpl accountHandler = new NotificationServiceImpl(mock(EmailService.class), mock(TemplateResolver.class), dispatcher);
        ReflectionTestUtils.setField(accountHandler, "frontendBaseUrl", "http://localhost:3000");

        accountHandler.handleUserRegistered(UserRegisteredEvent.builder().userId(WINNER).email("a@example.com").firstName("Alice").build());
        auctionHandler.auctionCreated(AuctionCreatedEvent.builder().auctionId(AUCTION).sellerId(SELLER).title("Vintage Watch").build());
        auctionHandler.auctionEnded(AuctionEndedEvent.builder().auctionId(AUCTION).sellerId(SELLER).winnerId(WINNER)
                .winningBidAmount(new BigDecimal("150")).build());
        auctionHandler.auctionCancelled(AuctionCancelledEvent.builder().auctionId(AUCTION).build());
        for (String type : List.of("REQUIRED", "COMPLETED", "FAILED")) {
            paymentHandler.paymentEvent(PaymentEvent.builder().auctionId(AUCTION).userId(WINNER).sellerId(SELLER)
                    .paymentType(type).amount(new BigDecimal("1500")).depositAmount(new BigDecimal("150"))
                    .remaining(new BigDecimal("1350")).deadline(Instant.parse("2026-10-03T10:00:00Z")).build());
        }
        paymentHandler.depositRefunded(DepositRefundedEvent.builder().userId(LOSER).auctionId(AUCTION)
                .amount(new BigDecimal("150")).reason("AUCTION_LOST").build());

        List<NotificationIntent> intents = new ArrayList<>();
        ArgumentCaptor<NotificationIntent> single = ArgumentCaptor.forClass(NotificationIntent.class);
        verify(dispatcher, atLeast(0)).dispatch(single.capture());
        intents.addAll(single.getAllValues());
        ArgumentCaptor<List<NotificationIntent>> batches = ArgumentCaptor.forClass(List.class);
        verify(dispatcher, atLeast(0)).dispatchAll(batches.capture());
        batches.getAllValues().forEach(intents::addAll);

        return intents.stream().map(NotificationIntent::email).filter(java.util.Objects::nonNull).toList();
    }

    @Test
    void everyHandlerEmailHasCompleteEnAndViTemplates() {
        List<NotificationIntent.EmailSpec> specs = emailSpecsFromAllHandlers();
        assertThat(specs).extracting(NotificationIntent.EmailSpec::templateBaseName)
                .contains("WELCOME_EMAIL", "AUCTION_CREATED", "AUCTION_LOST", "AUCTION_CANCELLED",
                        "AUCTION_WON", "PAYMENT_SUCCESSFUL", "PAYMENT_FAILED", "SALE_PAYMENT_RECEIVED", "DEPOSIT_REFUNDED");

        for (NotificationIntent.EmailSpec spec : specs) {
            Map<String, String> variables = new HashMap<>();
            spec.variables().forEach((key, value) -> variables.put(key, String.valueOf(value)));
            variables.putIfAbsent("userName", "Alice"); // the dispatcher always supplies userName
            StringSubstitutor substitutor = new StringSubstitutor(variables, "{", "}");

            for (String language : List.of("EN", "VI")) {
                String name = spec.templateBaseName() + "_" + language;
                List<Map<String, Object>> rows = jdbc.queryForList(
                        "SELECT subject, body_html, body_text FROM media_notification_templates WHERE name = ? AND active", name);
                assertThat(rows).as("active template %s", name).hasSize(1);
                Map<String, Object> row = rows.get(0);
                for (String column : List.of("subject", "body_html", "body_text")) {
                    Object text = row.get(column);
                    if (text != null) {
                        String rendered = substitutor.replace(text.toString());
                        assertThat(UNRESOLVED.matcher(rendered).results().map(r -> r.group()).collect(Collectors.toList()))
                                .as("unresolved placeholders in %s.%s", name, column)
                                .isEmpty();
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Run the test (Docker required)**

Run: `mvn -q -pl media-service -am test -Dtest=NotificationTemplatesPostgresIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

If it fails, the assertion message names the template and the unresolved `{placeholder}`. The fix belongs in the handler, which must supply the variable, or in migration `07`, which must use a variable the handler supplies. Never weaken the test. Report the root cause.

- [ ] **Step 3: Stop for review.** Suggested commit: `test(notification): verify handler emails render against seeded templates (NOTIF-104)`

---

### Task 7: Verification and roadmap update

**Files:**
- Modify: `docs/superpowers/plans/2026-09-30-notification-epic-roadmap.md` (Story 4 section and the Story 6 media-service bullet)

- [ ] **Step 1: Run the affected suites**

Run: `mvn -pl user-service,media-service -am test`
Expected: PASS (report the per-module "Tests run" lines).

- [ ] **Step 2: Run all media-service Postgres ITs (Docker required)**

Run: `mvn -q -pl media-service -am test -Dtest='NotificationPersistencePostgresIT,NotificationRepositoryPostgresIT,NotificationTemplatesPostgresIT' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS

- [ ] **Step 3: Update the roadmap.** In `docs/superpowers/plans/2026-09-30-notification-epic-roadmap.md`:
  - In the Story 4 table, change the `payment-event-topic` REQUIRED row's email cell from `` `PAYMENT_REQUIRED` (transactional) `` to `` `AUCTION_WON` (transactional; the seeded "you won, pay by…" template) ``.
  - In the same table, change the `deposit-refunded-topic` row's email cell to `` `DEPOSIT_REFUNDED` (only for reason `AUCTION_LOST`) ``.
  - In the same table, change the `payment-event-topic` COMPLETED row's email cell from `` `PAYMENT_SUCCESSFUL` (winner) `` to `` `PAYMENT_SUCCESSFUL` (winner) + `SALE_PAYMENT_RECEIVED` (seller), both transactional ``.
  - Replace the Story 4 bullet starting `` `src/main/resources/db/changelog/migrations/07-event-templates.sql` `` with: `` `07-event-templates.sql`: `AUCTION_CANCELLED_{EN,VI}`, `PAYMENT_FAILED_{EN,VI}` and `SALE_PAYMENT_RECEIVED_{EN,VI}` only. The winner email reuses the seeded `AUCTION_WON`. ``
  - Replace the Story 4 bullet starting `` `notification/Messages.java` `` with: `In-app copy (EN) lives in each handler; `projection/AuctionLookup` resolves titles, sellers and participants; `NotificationFormats`/`NotificationLinks` format money, deadlines and links. The dispatcher fills `{userName}` from `Recipient.displayName`, which comes from the user-service notification-preferences endpoint (now profile-based, adding `displayName`).`
  - After the Story 4 table, add the line: `Extension notifications are keyed on the new end time (`DedupKeys.extended(auctionId, newEndTime)`): one per extension per user, redelivery-safe.`
  - Tick tasks 4.1–4.5, and append ` (manual smoke handed to the user)` to 4.5.
  - In Story 6, replace `` `08-payment-reminder-template.sql` (+ master changelog): `PAYMENT_REMINDER_24H_{EN,VI}` (variables `userName, auctionTitle, remaining, paymentDeadline, actionUrl`) `` with `` reuse the seeded `PAYMENT_REMINDER_2_{EN,VI}` template (variables `userName, auctionTitle, bidAmount, paymentDeadline, actionUrl`); no new migration ``. In the Story 6 media-service handler bullet, change `` `PAYMENT_REMINDER_24H` template `` to `` `PAYMENT_REMINDER_2` template ``.

- [ ] **Step 4: Manual smoke (the user runs this; agents skip it).** With the stack running via docker-compose:
  1. Create an auction that goes live immediately. The seller gets an in-app "Your auction is live" and an `AUCTION_CREATED_*` email.
  2. Two users bid. The seller gets a "First bid received" notification after the first bid only.
  3. Let the auction close, or force-close it as admin.
     - The winner gets an in-app "You won!", then "Payment required" plus an `AUCTION_WON_*` email.
     - The other bidder gets "Auction ended" plus `AUCTION_LOST_*`, then "Deposit refunded" plus `DEPOSIT_REFUNDED_*`.
  4. The winner pays from the wallet page. The winner gets "Payment complete" plus `PAYMENT_SUCCESSFUL_*`; the seller gets "Buyer paid" plus `SALE_PAYMENT_RECEIVED_*`.
  5. `SELECT user_id, type, dedup_key FROM media_notifications ORDER BY created_at` shows one row per user per event.

- [ ] **Step 5: Stop for review.** Suggested commit: `docs(notification): record NOTIF-104 refinements in roadmap`
