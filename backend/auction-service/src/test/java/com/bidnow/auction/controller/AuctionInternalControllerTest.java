package com.bidnow.auction.controller;

import com.bidnow.auction.constant.AuctionErrorCodes;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.dto.request.ApplyBidRequest;
import com.bidnow.auction.dto.response.ApplyBidResponse;
import com.bidnow.auction.dto.response.BidContextResponse;
import com.bidnow.auction.exception.AuctionExceptionHandler;
import com.bidnow.auction.exception.BidTooLowException;
import com.bidnow.auction.exception.ConflictException;
import com.bidnow.auction.service.AuctionBidService;
import com.bidnow.common.exception.ForbiddenException;
import com.bidnow.common.exception.GlobalExceptionHandler;
import com.bidnow.common.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuctionInternalControllerTest {

    private static final UUID AUCTION_ID = UUID.fromString("b0000000-0000-0000-0000-000000000005");
    private static final String BASE = "/api/v1/internal/auctions/" + AUCTION_ID;

    @Mock
    private AuctionBidService auctionBidService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuctionInternalController(auctionBidService))
                .setControllerAdvice(new AuctionExceptionHandler(), new GlobalExceptionHandler())
                .build();
    }

    private static String bidJson(String bidId, String bidderId, String amount) {
        return """
                {"bidId": %s, "bidderId": %s, "amount": %s}
                """.formatted(bidId, bidderId, amount);
    }

    private static String validBidJson() {
        return bidJson("\"" + UUID.randomUUID() + "\"", "\"" + UUID.randomUUID() + "\"", "105.00");
    }

    @Test
    void getBidContext_returns200WithContext() throws Exception {
        when(auctionBidService.getBidContext(AUCTION_ID)).thenReturn(BidContextResponse.builder()
                .auctionId(AUCTION_ID).title("Watch").status(AuctionStatus.ACTIVE)
                .currentPrice(new BigDecimal("100.00")).bidIncrement(new BigDecimal("5.00"))
                .totalBids(2).endTime(OffsetDateTime.parse("2026-10-01T12:00:00Z")).build());

        mockMvc.perform(get(BASE + "/bid-context"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auctionId").value(AUCTION_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.totalBids").value(2));
    }

    @Test
    void getBidContext_unknownAuction_returns404() throws Exception {
        when(auctionBidService.getBidContext(AUCTION_ID))
                .thenThrow(new NotFoundException("Auction not found", AuctionErrorCodes.AUCTION_NOT_FOUND));

        mockMvc.perform(get(BASE + "/bid-context"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(AuctionErrorCodes.AUCTION_NOT_FOUND));
    }

    @Test
    void applyBid_returns200WithNewState() throws Exception {
        UUID winner = UUID.randomUUID();
        when(auctionBidService.applyBid(eq(AUCTION_ID), any(ApplyBidRequest.class))).thenReturn(ApplyBidResponse.builder()
                .auctionId(AUCTION_ID).currentPrice(new BigDecimal("105.00")).currentWinnerId(winner)
                .totalBids(3).extended(false).build());

        mockMvc.perform(post(BASE + "/bids").contentType(MediaType.APPLICATION_JSON).content(validBidJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentPrice").value(105.00))
                .andExpect(jsonPath("$.data.currentWinnerId").value(winner.toString()))
                .andExpect(jsonPath("$.data.totalBids").value(3))
                .andExpect(jsonPath("$.data.extended").value(false));
    }

    @Test
    void applyBid_missingBidId_returns400InvalidInput() throws Exception {
        mockMvc.perform(post(BASE + "/bids").contentType(MediaType.APPLICATION_JSON)
                        .content(bidJson("null", "\"" + UUID.randomUUID() + "\"", "105.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors.bidId").exists());
        verifyNoInteractions(auctionBidService);
    }

    @Test
    void applyBid_nonPositiveAmount_returns400InvalidInput() throws Exception {
        mockMvc.perform(post(BASE + "/bids").contentType(MediaType.APPLICATION_JSON)
                        .content(bidJson("\"" + UUID.randomUUID() + "\"", "\"" + UUID.randomUUID() + "\"", "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
        verifyNoInteractions(auctionBidService);
    }

    @Test
    void applyBid_tooLow_returns400WithMinimumBid() throws Exception {
        when(auctionBidService.applyBid(eq(AUCTION_ID), any(ApplyBidRequest.class)))
                .thenThrow(new BidTooLowException(new BigDecimal("110.00")));

        mockMvc.perform(post(BASE + "/bids").contentType(MediaType.APPLICATION_JSON).content(validBidJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(AuctionErrorCodes.BID_TOO_LOW))
                .andExpect(jsonPath("$.errors.minimumBid").value("110.00"));
    }

    @Test
    void applyBid_ownAuction_returns403() throws Exception {
        when(auctionBidService.applyBid(eq(AUCTION_ID), any(ApplyBidRequest.class)))
                .thenThrow(new ForbiddenException("own", AuctionErrorCodes.BID_OWN_AUCTION));

        mockMvc.perform(post(BASE + "/bids").contentType(MediaType.APPLICATION_JSON).content(validBidJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(AuctionErrorCodes.BID_OWN_AUCTION));
    }

    @Test
    void applyBid_auctionClosed_returns409() throws Exception {
        when(auctionBidService.applyBid(eq(AUCTION_ID), any(ApplyBidRequest.class)))
                .thenThrow(new ConflictException("closed", AuctionErrorCodes.AUCTION_NOT_OPEN));

        mockMvc.perform(post(BASE + "/bids").contentType(MediaType.APPLICATION_JSON).content(validBidJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(AuctionErrorCodes.AUCTION_NOT_OPEN));
    }
}
