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
    void insertIfAbsent_sentAtIsSetOnlyForSentRows() {
        Notification sent = row(alice, "S", auctionId, null);
        Notification pending = row(alice, "P", auctionId, null);
        pending.setStatus(NotificationStatus.PENDING);
        inbox.insertIfAbsent(sent);
        inbox.insertIfAbsent(pending);

        Map<String, Object> s = jdbc.getJdbcTemplate().queryForMap(
                "SELECT sent_at, created_at FROM media_notifications WHERE id = ?", sent.getId());
        Map<String, Object> p = jdbc.getJdbcTemplate().queryForMap(
                "SELECT sent_at FROM media_notifications WHERE id = ?", pending.getId());
        assertThat(s.get("sent_at")).isEqualTo(s.get("created_at"));
        assertThat(p.get("sent_at")).isNull();
    }

    @Test
    void insertIfAbsent_nullCreatedAtDefaultsToNow() {
        Notification n = row(alice, "N", auctionId, null);
        n.setCreatedAt(null);

        assertThat(inbox.insertIfAbsent(n)).isTrue();

        Object createdAt = jdbc.getJdbcTemplate().queryForObject(
                "SELECT created_at FROM media_notifications WHERE id = ?", Object.class, n.getId());
        assertThat(createdAt).isNotNull();
    }

    @Test
    void upsertAuction_olderEndTimeNeverRollsBackAnExtension() {
        OffsetDateTime extended = OffsetDateTime.parse("2026-10-01T12:05:00Z");
        OffsetDateTime older = OffsetDateTime.parse("2026-10-01T12:00:00Z");

        projection.upsertAuction(auctionId, "Vintage Watch", null, extended);
        projection.upsertAuction(auctionId, null, null, older);

        assertThat(projection.findAuction(auctionId).orElseThrow().endTime().toInstant())
                .isEqualTo(extended.toInstant());
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
