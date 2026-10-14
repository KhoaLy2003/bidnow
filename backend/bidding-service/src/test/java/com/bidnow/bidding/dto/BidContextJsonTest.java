package com.bidnow.bidding.dto;

import com.bidnow.common.dto.BaseResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contract test: auction-service's JSON shape and the Redis cache format, using Boot-like Jackson config. */
class BidContextJsonTest {

    private static final String AUCTION_SERVICE_JSON = """
            {"timestamp":"2026-10-01T05:00:00Z","status":200,"message":"Success","data":{
            "auctionId":"b0000000-0000-0000-0000-000000000005","title":"Watch",
            "sellerId":"550e8400-e29b-41d4-a716-446655440001","status":"ACTIVE",
            "currentPrice":100.00,"bidIncrement":5.00,"depositAmount":20.00,"currentWinnerId":null,
            "totalBids":1,"endTime":"2026-10-01T12:00:00+07:00","someFutureField":"x"}}
            """;

    // The bare builder registers JavaTimeModule but leaves timestamp-dates on; Spring Boot's
    // JacksonAutoConfiguration turns them off (spring.jackson.serialization.write-dates-as-timestamps=false).
    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();

    @Test
    void deserializesAuctionServiceResponse() throws Exception {
        BaseResponse<BidContext> response = mapper.readValue(AUCTION_SERVICE_JSON, new TypeReference<>() { });

        BidContext ctx = response.getData();
        assertThat(ctx.getAuctionId()).isEqualTo(UUID.fromString("b0000000-0000-0000-0000-000000000005"));
        assertThat(ctx.getTitle()).isEqualTo("Watch");
        assertThat(ctx.getSellerId()).isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440001"));
        assertThat(ctx.getStatus()).isEqualTo("ACTIVE");
        assertThat(ctx.getCurrentPrice()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(ctx.getBidIncrement()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(ctx.getDepositAmount()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(ctx.getCurrentWinnerId()).isNull();
        assertThat(ctx.getTotalBids()).isEqualTo(1);
        assertThat(ctx.getEndTime().toInstant()).isEqualTo(Instant.parse("2026-10-01T05:00:00Z"));
    }

    @Test
    void roundTripsThroughCacheFormat() throws Exception {
        BidContext ctx = BidContext.builder()
                .auctionId(UUID.fromString("b0000000-0000-0000-0000-000000000005")).title("Watch")
                .sellerId(UUID.fromString("550e8400-e29b-41d4-a716-446655440001")).status("ACTIVE")
                .currentPrice(new BigDecimal("100.00")).bidIncrement(new BigDecimal("5.00"))
                .depositAmount(new BigDecimal("20.00")).totalBids(1)
                .endTime(OffsetDateTime.parse("2026-10-01T05:00:00Z")).build();

        String json = mapper.writeValueAsString(ctx);

        assertThat(json).contains("\"endTime\":\"");
        assertThat(mapper.readValue(json, BidContext.class)).isEqualTo(ctx);
    }
}
