package com.bidnow.media.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RealtimeMessageJsonTest {

    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private final UUID auctionId = UUID.randomUUID();

    private JsonNode json(String type, Object payload) throws Exception {
        JsonNode root = mapper.readTree(mapper.writeValueAsString(new AuctionRealtimeMessage(type, auctionId, payload)));
        assertThat(keys(root)).containsExactlyInAnyOrder("type", "auctionId", "payload");
        assertThat(root.get("type").asText()).isEqualTo(type);
        assertThat(root.get("auctionId").asText()).isEqualTo(auctionId.toString());
        return root.get("payload");
    }

    private static Set<String> keys(JsonNode node) {
        Set<String> keys = new TreeSet<>();
        node.fieldNames().forEachRemaining(keys::add);
        return keys;
    }

    @Test
    void bidPlaced() throws Exception {
        OffsetDateTime at = OffsetDateTime.of(2026, 10, 1, 12, 0, 0, 0, ZoneOffset.UTC);
        JsonNode p = json(AuctionRealtimeMessage.BID_PLACED, new RealtimePayloads.BidPlaced(
                UUID.randomUUID(), UUID.randomUUID(), "Alice", new BigDecimal("150.50"), at, 3, at.plusHours(1), true));

        assertThat(keys(p)).containsExactlyInAnyOrder("bidId", "bidderId", "bidderName", "amount", "placedAt",
                "totalBids", "endTime", "antiSnipingTriggered");
        assertThat(p.has("isAntiSnipingTriggered")).isFalse();
        assertThat(p.get("placedAt").isTextual()).isTrue();
        assertThat(p.get("placedAt").asText()).isEqualTo("2026-10-01T12:00:00Z");
        assertThat(p.get("amount").isNumber()).isTrue();
    }

    @Test
    void auctionExtended() throws Exception {
        JsonNode p = json(AuctionRealtimeMessage.AUCTION_EXTENDED, new RealtimePayloads.AuctionExtended(
                Instant.parse("2026-10-01T12:00:00Z"), Instant.parse("2026-10-01T12:05:00Z"), 2));

        assertThat(keys(p)).containsExactlyInAnyOrder("previousEndTime", "newEndTime", "extensionCount");
        assertThat(p.get("previousEndTime").asText()).isEqualTo("2026-10-01T12:00:00Z");
        assertThat(p.get("newEndTime").asText()).isEqualTo("2026-10-01T12:05:00Z");
    }

    @Test
    void auctionEnded() throws Exception {
        JsonNode p = json(AuctionRealtimeMessage.AUCTION_ENDED, new RealtimePayloads.AuctionEnded(
                UUID.randomUUID(), new BigDecimal("99"), Instant.parse("2026-10-01T12:00:00Z")));

        assertThat(keys(p)).containsExactlyInAnyOrder("winnerId", "finalPrice", "endedAt");
        assertThat(p.get("endedAt").asText()).isEqualTo("2026-10-01T12:00:00Z");
        assertThat(p.get("finalPrice").isNumber()).isTrue();
    }

    @Test
    void auctionCancelled() throws Exception {
        JsonNode p = json(AuctionRealtimeMessage.AUCTION_CANCELLED, new RealtimePayloads.AuctionCancelled("fraud"));

        assertThat(keys(p)).containsExactly("reason");
    }

    @Test
    void outbid() throws Exception {
        JsonNode p = json(AuctionRealtimeMessage.OUTBID,
                new RealtimePayloads.Outbid("Vase", new BigDecimal("120"), "Bob"));

        assertThat(keys(p)).containsExactlyInAnyOrder("auctionTitle", "currentPrice", "newLeaderName");
        assertThat(p.get("currentPrice").isNumber()).isTrue();
    }
}
