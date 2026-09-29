package com.bidnow.bidding.controller;

import com.bidnow.bidding.constant.BiddingErrorCodes;
import com.bidnow.bidding.dto.request.PlaceBidRequest;
import com.bidnow.bidding.dto.response.PlaceBidResponse;
import com.bidnow.bidding.exception.BidTooLowException;
import com.bidnow.bidding.exception.BiddingExceptionHandler;
import com.bidnow.bidding.exception.ConflictException;
import com.bidnow.bidding.exception.InsufficientBalanceException;
import com.bidnow.bidding.exception.ServiceUnavailableException;
import com.bidnow.bidding.service.BidService;
import com.bidnow.common.exception.ForbiddenException;
import com.bidnow.common.exception.GlobalExceptionHandler;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.common.resolver.UserIdArgumentResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BidControllerTest {

    private static final UUID AUCTION_ID = UUID.fromString("b0000000-0000-0000-0000-000000000005");
    private static final UUID BIDDER_ID = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Mock
    private BidService bidService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BidController(bidService))
                .setControllerAdvice(new BiddingExceptionHandler(), new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new UserIdArgumentResolver())
                .build();
    }

    private ResultActions postBid(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/bids").header("X-User-Id", BIDDER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String body(String auctionId, String amount) {
        return """
                {"auctionId": %s, "amount": %s}
                """.formatted(auctionId, amount);
    }

    private static String validBody() {
        return body("\"" + AUCTION_ID + "\"", "105.00");
    }

    private void serviceThrows(RuntimeException ex) {
        when(bidService.placeBid(eq(BIDDER_ID), any(PlaceBidRequest.class))).thenThrow(ex);
    }

    @Test
    void acceptedBid_returns201WithBody() throws Exception {
        UUID bidId = UUID.randomUUID();
        when(bidService.placeBid(eq(BIDDER_ID), any(PlaceBidRequest.class))).thenReturn(PlaceBidResponse.builder()
                .bidId(bidId).auctionId(AUCTION_ID).amount(new BigDecimal("105.00"))
                .placedAt(OffsetDateTime.parse("2026-10-01T12:00:00Z"))
                .currentPrice(new BigDecimal("105.00")).totalBids(2)
                .endTime(OffsetDateTime.parse("2026-10-01T13:00:00Z")).extended(false).build());

        postBid(validBody())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("Bid placed"))
                .andExpect(jsonPath("$.data.bidId").value(bidId.toString()))
                .andExpect(jsonPath("$.data.auctionId").value(AUCTION_ID.toString()))
                .andExpect(jsonPath("$.data.totalBids").value(2))
                .andExpect(jsonPath("$.data.extended").value(false));

        ArgumentCaptor<PlaceBidRequest> captor = ArgumentCaptor.forClass(PlaceBidRequest.class);
        verify(bidService).placeBid(eq(BIDDER_ID), captor.capture());
        assertThat(captor.getValue().getAuctionId()).isEqualTo(AUCTION_ID);
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("105.00");
    }

    @Test
    void bidTooLow_returns400WithMinimumBid() throws Exception {
        serviceThrows(new BidTooLowException(new BigDecimal("110.00")));

        postBid(validBody())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(BiddingErrorCodes.BID_TOO_LOW))
                .andExpect(jsonPath("$.errors.minimumBid").value("110.00"));
    }

    @Test
    void insufficientBalance_returns403WithWalletDetails() throws Exception {
        serviceThrows(new InsufficientBalanceException(Map.of("availableBalance", "10.00", "required", "20.00")));

        postBid(validBody())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(BiddingErrorCodes.BID_INSUFFICIENT_BALANCE))
                .andExpect(jsonPath("$.errors.availableBalance").value("10.00"))
                .andExpect(jsonPath("$.errors.required").value("20.00"));
    }

    @Test
    void ownAuction_returns403() throws Exception {
        serviceThrows(new ForbiddenException("own", BiddingErrorCodes.BID_OWN_AUCTION));

        postBid(validBody())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(BiddingErrorCodes.BID_OWN_AUCTION));
    }

    @Test
    void walletNotActive_returns403() throws Exception {
        serviceThrows(new ForbiddenException("inactive", BiddingErrorCodes.WALLET_NOT_ACTIVE));

        postBid(validBody())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(BiddingErrorCodes.WALLET_NOT_ACTIVE));
    }

    @Test
    void unknownAuction_returns404() throws Exception {
        serviceThrows(new NotFoundException("nf", BiddingErrorCodes.AUCTION_NOT_FOUND));

        postBid(validBody())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(BiddingErrorCodes.AUCTION_NOT_FOUND));
    }

    @Test
    void auctionNotOpen_returns409() throws Exception {
        serviceThrows(new ConflictException("closed", BiddingErrorCodes.AUCTION_NOT_OPEN));

        postBid(validBody())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(BiddingErrorCodes.AUCTION_NOT_OPEN));
    }

    @Test
    void depositLockClosed_returns409() throws Exception {
        serviceThrows(new ConflictException("closed", BiddingErrorCodes.DEPOSIT_LOCK_CLOSED));

        postBid(validBody())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(BiddingErrorCodes.DEPOSIT_LOCK_CLOSED));
    }

    @Test
    void downstreamUnavailable_returns503() throws Exception {
        serviceThrows(new ServiceUnavailableException("down"));

        postBid(validBody())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value(BiddingErrorCodes.SERVICE_UNAVAILABLE));
    }

    @Test
    void missingAuctionId_returns400InvalidInput() throws Exception {
        postBid(body("null", "105.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors.auctionId").exists());
        verifyNoInteractions(bidService);
    }

    @Test
    void nonPositiveAmount_returns400InvalidInput() throws Exception {
        postBid(body("\"" + AUCTION_ID + "\"", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
        verifyNoInteractions(bidService);
    }

    @Test
    void amountWithThreeDecimals_returns400InvalidInput() throws Exception {
        postBid(body("\"" + AUCTION_ID + "\"", "105.001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
        verifyNoInteractions(bidService);
    }

    @Test
    void malformedAuctionId_returns400InvalidInput() throws Exception {
        postBid(body("\"not-a-uuid\"", "105.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
        verifyNoInteractions(bidService);
    }
}
