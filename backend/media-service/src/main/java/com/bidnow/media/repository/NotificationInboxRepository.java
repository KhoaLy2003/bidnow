package com.bidnow.media.repository;

import com.bidnow.media.domain.entity.Notification;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.LocalDateTime;
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
                 CAST(:metadata AS jsonb), :dedupKey, CASE WHEN :status = 'SENT' THEN :createdAt END, 0, :createdAt, :createdAt)
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
        LocalDateTime createdAt = row.getCreatedAt() != null ? row.getCreatedAt() : LocalDateTime.now();
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
                .addValue("createdAt", createdAt, Types.TIMESTAMP);
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
