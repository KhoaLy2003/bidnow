package com.bidnow.bidding.service;

import com.bidnow.bidding.constant.BiddingErrorCodes;
import com.bidnow.bidding.dto.ApplyBidCommand;
import com.bidnow.bidding.dto.ApplyBidResult;
import com.bidnow.bidding.exception.BidTooLowException;
import com.bidnow.bidding.exception.ConflictException;
import com.bidnow.bidding.exception.ServiceUnavailableException;
import com.bidnow.bidding.feign.AuctionServiceClient;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.exception.ForbiddenException;
import com.bidnow.common.exception.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionBidGatewayTest {

    private static final UUID AUCTION_ID = UUID.fromString("b0000000-0000-0000-0000-000000000005");
    private static final ApplyBidCommand COMMAND =
            new ApplyBidCommand(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("105.00"));

    @Mock
    private AuctionServiceClient auctionServiceClient;

    private AuctionBidGateway gateway;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        gateway = new AuctionBidGateway(auctionServiceClient, objectMapper);
    }

    private static Request request() {
        return Request.create(Request.HttpMethod.POST, "/api/v1/internal/auctions/x/bids",
                Map.of(), null, StandardCharsets.UTF_8, null);
    }

    private static byte[] error(int status, String code, String errorsJson) {
        return ("{\"status\":" + status + ",\"errorCode\":\"" + code + "\",\"message\":\"m\""
                + (errorsJson == null ? "" : ",\"errors\":" + errorsJson) + "}").getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void success_returnsResult() {
        ApplyBidResult result = ApplyBidResult.builder().auctionId(AUCTION_ID)
                .currentPrice(new BigDecimal("105.00")).totalBids(2)
                .currentWinnerId(UUID.randomUUID()).endTime(java.time.OffsetDateTime.parse("2026-10-01T12:00:00Z")).build();
        when(auctionServiceClient.applyBid(AUCTION_ID, COMMAND)).thenReturn(BaseResponse.success(result));

        assertThat(gateway.apply(AUCTION_ID, COMMAND)).isEqualTo(result);
    }

    @Test
    void emptyBody_mapsTo503() {
        when(auctionServiceClient.applyBid(AUCTION_ID, COMMAND)).thenReturn(BaseResponse.success(null));

        assertThatThrownBy(() -> gateway.apply(AUCTION_ID, COMMAND)).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void auctionNotOpen_mapsTo409() {
        when(auctionServiceClient.applyBid(AUCTION_ID, COMMAND)).thenThrow(
                new FeignException.Conflict("c", request(), error(409, "AUCTION_NOT_OPEN", null), Map.of()));

        assertThatThrownBy(() -> gateway.apply(AUCTION_ID, COMMAND))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo(BiddingErrorCodes.AUCTION_NOT_OPEN);
    }

    @Test
    void bidTooLow_mapsTo400WithDownstreamMinimum() {
        when(auctionServiceClient.applyBid(AUCTION_ID, COMMAND)).thenThrow(new FeignException.BadRequest("b", request(),
                error(400, "BID_TOO_LOW", "{\"minimumBid\":\"110.00\"}"), Map.of()));

        assertThatThrownBy(() -> gateway.apply(AUCTION_ID, COMMAND))
                .isInstanceOf(BidTooLowException.class)
                .satisfies(ex -> assertThat(((BidTooLowException) ex).getMinimumBid()).isEqualByComparingTo("110.00"));
    }

    @Test
    void bidTooLowWithoutMinimum_mapsTo503() {
        when(auctionServiceClient.applyBid(AUCTION_ID, COMMAND)).thenThrow(
                new FeignException.BadRequest("b", request(), error(400, "BID_TOO_LOW", null), Map.of()));

        assertThatThrownBy(() -> gateway.apply(AUCTION_ID, COMMAND)).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void ownAuction_mapsTo403() {
        when(auctionServiceClient.applyBid(AUCTION_ID, COMMAND)).thenThrow(
                new FeignException.Forbidden("f", request(), error(403, "BID_OWN_AUCTION", null), Map.of()));

        assertThatThrownBy(() -> gateway.apply(AUCTION_ID, COMMAND))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode").isEqualTo(BiddingErrorCodes.BID_OWN_AUCTION);
    }

    @Test
    void notFound_mapsTo404() {
        when(auctionServiceClient.applyBid(AUCTION_ID, COMMAND)).thenThrow(
                new FeignException.NotFound("nf", request(), error(404, "AUCTION_NOT_FOUND", null), Map.of()));

        assertThatThrownBy(() -> gateway.apply(AUCTION_ID, COMMAND))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo(BiddingErrorCodes.AUCTION_NOT_FOUND);
    }

    @Test
    void serverErrorOrLockTimeout_mapsTo503() {
        when(auctionServiceClient.applyBid(AUCTION_ID, COMMAND)).thenThrow(
                new FeignException.InternalServerError("boom", request(), null, Map.of()));

        assertThatThrownBy(() -> gateway.apply(AUCTION_ID, COMMAND)).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void timeout_mapsTo503AndLogsCritical() {
        Logger logger = (Logger) LoggerFactory.getLogger(AuctionBidGateway.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            when(auctionServiceClient.applyBid(AUCTION_ID, COMMAND)).thenThrow(
                    new RetryableException(-1, "Read timed out", Request.HttpMethod.POST, (Long) null, request()));

            assertThatThrownBy(() -> gateway.apply(AUCTION_ID, COMMAND)).isInstanceOf(ServiceUnavailableException.class);
            assertThat(appender.list).anyMatch(e -> e.getLevel() == Level.ERROR
                    && e.getFormattedMessage().contains("CRITICAL: apply-bid outcome unknown"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void partialResult_nullEndTime_mapsTo503() {
        ApplyBidResult partial = ApplyBidResult.builder().auctionId(AUCTION_ID)
                .currentPrice(new BigDecimal("105.00")).currentWinnerId(UUID.randomUUID()).totalBids(2).build();
        when(auctionServiceClient.applyBid(AUCTION_ID, COMMAND)).thenReturn(BaseResponse.success(partial));

        assertThatThrownBy(() -> gateway.apply(AUCTION_ID, COMMAND)).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void bidTooLowWithNonNumericMinimum_mapsTo503() {
        when(auctionServiceClient.applyBid(AUCTION_ID, COMMAND)).thenThrow(new FeignException.BadRequest("b", request(),
                error(400, "BID_TOO_LOW", "{\"minimumBid\":\"abc\"}"), Map.of()));

        assertThatThrownBy(() -> gateway.apply(AUCTION_ID, COMMAND)).isInstanceOf(ServiceUnavailableException.class);
    }
}
