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
                end_time   = GREATEST(EXCLUDED.end_time, media_auctions.end_time),
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

    /** A null title/seller keeps the stored value; end_time only moves forward. */
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
