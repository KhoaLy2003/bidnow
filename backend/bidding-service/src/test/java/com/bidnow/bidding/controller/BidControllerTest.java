package com.bidnow.bidding.controller;

import com.bidnow.bidding.constant.BiddingErrorCodes;
import com.bidnow.bidding.dto.BidContext;
import com.bidnow.bidding.exception.BiddingExceptionHandler;
import com.bidnow.bidding.exception.ServiceUnavailableException;
import com.bidnow.bidding.service.AuctionContextCacheService;
import com.bidnow.bidding.service.BidValidationService;
import com.bidnow.common.exception.GlobalExceptionHandler;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.common.resolver.UserIdArgumentResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BidControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final UUID AUCTION_ID = UUID.fromString("b0000000-0000-0000-0000-000000000005");
    private static final UUID SELLER_ID = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final String BIDDER_ID = "00000000-0000-0000-0000-00000000000b";

    @Mock
    private AuctionContextCacheService contextCache;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        BidController controller = new BidController(contextCache, new BidValidationService(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new BiddingExceptionHandler(), new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new UserIdArgumentResolver())
                .build();
    }

    private static BidContext openContext() {
        return BidContext.builder()
                .auctionId(AUCTION_ID).title("Vintage Watch").sellerId(SELLER_ID).status("ACTIVE")
                .currentPrice(new BigDecimal("100.00")).bidIncrement(new BigDecimal("5.00"))
                .depositAmount(new BigDecimal("20.00")).totalBids(1)
                .endTime(OffsetDateTime.ofInstant(NOW.plusSeconds(3600), ZoneOffset.UTC))
                .build();
    }

    private static String body(String auctionId, String amount) {
        return """
                {"auctionId": %s, "amount": %s}
                """.formatted(auctionId, amount);
    }

    private static String validBody(String amount) {
        return body("\"" + AUCTION_ID + "\"", amount);
    }

    @Test
    void validBid_passesPreValidation_returns501UntilStory3() throws Exception {
        when(contextCache.get(AUCTION_ID)).thenReturn(openContext());

        mockMvc.perform(post("/api/v1/bids").header("X-User-Id", BIDDER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody("105.00")))
                .andExpect(status().isNotImplemented());
    }

    @Test
    void bidTooLow_returns400WithMinimumBid() throws Exception {
        when(contextCache.get(AUCTION_ID)).thenReturn(openContext());

        mockMvc.perform(post("/api/v1/bids").header("X-User-Id", BIDDER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody("104.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(BiddingErrorCodes.BID_TOO_LOW))
                .andExpect(jsonPath("$.errors.minimumBid").value("105.00"));
    }

    @Test
    void sellerBid_returns403() throws Exception {
        when(contextCache.get(AUCTION_ID)).thenReturn(openContext());

        mockMvc.perform(post("/api/v1/bids").header("X-User-Id", SELLER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(validBody("500.00")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(BiddingErrorCodes.BID_OWN_AUCTION));
    }

    private static BidContext closedContext() {
        BidContext closed = openContext();
        closed.setStatus("COMPLETED");
        return closed;
    }

    @Test
    void closedAuction_stillClosedAfterRefresh_returns409() throws Exception {
        when(contextCache.get(AUCTION_ID)).thenReturn(closedContext());
        when(contextCache.refresh(AUCTION_ID)).thenReturn(closedContext());

        mockMvc.perform(post("/api/v1/bids").header("X-User-Id", BIDDER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody("105.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(BiddingErrorCodes.AUCTION_NOT_OPEN));
    }

    @Test
    void staleClosedContext_refreshReturnsOpen_returns501() throws Exception {
        when(contextCache.get(AUCTION_ID)).thenReturn(closedContext());
        when(contextCache.refresh(AUCTION_ID)).thenReturn(openContext());

        mockMvc.perform(post("/api/v1/bids").header("X-User-Id", BIDDER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody("105.00")))
                .andExpect(status().isNotImplemented());
    }

    @Test
    void bidTooLowAndOwnAuction_neverTriggerRefresh() throws Exception {
        when(contextCache.get(AUCTION_ID)).thenReturn(openContext());

        mockMvc.perform(post("/api/v1/bids").header("X-User-Id", BIDDER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody("104.00")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/bids").header("X-User-Id", SELLER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(validBody("500.00")))
                .andExpect(status().isForbidden());

        verify(contextCache, never()).refresh(any());
    }

    @Test
    void malformedAuctionId_returns400InvalidInput() throws Exception {
        mockMvc.perform(post("/api/v1/bids").header("X-User-Id", BIDDER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(body("\"not-a-uuid\"", "105.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
        verifyNoInteractions(contextCache);
    }

    @Test
    void unknownAuction_returns404() throws Exception {
        when(contextCache.get(AUCTION_ID))
                .thenThrow(new NotFoundException("Auction not found", BiddingErrorCodes.AUCTION_NOT_FOUND));

        mockMvc.perform(post("/api/v1/bids").header("X-User-Id", BIDDER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody("105.00")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(BiddingErrorCodes.AUCTION_NOT_FOUND));
    }

    @Test
    void auctionServiceDown_returns503() throws Exception {
        when(contextCache.get(AUCTION_ID)).thenThrow(new ServiceUnavailableException("down"));

        mockMvc.perform(post("/api/v1/bids").header("X-User-Id", BIDDER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody("105.00")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value(BiddingErrorCodes.SERVICE_UNAVAILABLE));
    }

    @Test
    void missingAuctionId_returns400InvalidInput() throws Exception {
        mockMvc.perform(post("/api/v1/bids").header("X-User-Id", BIDDER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(body("null", "105.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors.auctionId").exists());
        verifyNoInteractions(contextCache);
    }

    @Test
    void nonPositiveAmount_returns400InvalidInput() throws Exception {
        mockMvc.perform(post("/api/v1/bids").header("X-User-Id", BIDDER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody("0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
        verifyNoInteractions(contextCache);
    }

    @Test
    void amountWithThreeDecimals_returns400InvalidInput() throws Exception {
        mockMvc.perform(post("/api/v1/bids").header("X-User-Id", BIDDER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody("105.001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
        verifyNoInteractions(contextCache);
    }
}
