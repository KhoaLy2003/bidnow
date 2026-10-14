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
