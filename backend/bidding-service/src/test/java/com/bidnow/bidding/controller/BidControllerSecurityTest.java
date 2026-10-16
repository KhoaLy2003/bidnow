package com.bidnow.bidding.controller;

import com.bidnow.bidding.config.SecurityConfig;
import com.bidnow.bidding.dto.request.BidHistoryQuery;
import com.bidnow.bidding.dto.request.BuyNowRequest;
import com.bidnow.bidding.dto.response.BidHistoryResponse;
import com.bidnow.bidding.dto.response.BuyNowResponse;
import com.bidnow.bidding.exception.BiddingExceptionHandler;
import com.bidnow.bidding.service.BidHistoryService;
import com.bidnow.bidding.service.BidService;
import com.bidnow.bidding.service.BuyNowService;
import com.bidnow.bidding.service.MyActiveBidsService;
import com.bidnow.bidding.dto.response.MyActiveBidCounts;
import com.bidnow.bidding.dto.response.MyActiveBidsResponse;
import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.dto.PaginationMeta;
import com.bidnow.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Security-slice test: public auction history vs. authenticated my-bids under the real SecurityConfig. */
@WebMvcTest(BidController.class)
@Import({SecurityConfig.class, BiddingExceptionHandler.class, GlobalExceptionHandler.class})
class BidControllerSecurityTest {

    private static final UUID AUCTION_ID = UUID.fromString("b0000000-0000-0000-0000-000000000005");
    private static final UUID BIDDER_ID = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BidService bidService;

    @MockBean
    private BidHistoryService bidHistoryService;

    @MockBean
    private BuyNowService buyNowService;

    @MockBean
    private MyActiveBidsService myActiveBidsService;

    @Test
    void myActiveBids_withoutUserId_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/bids/me/active")).andExpect(status().isUnauthorized());
        verifyNoInteractions(myActiveBidsService);
    }

    @Test
    void myActiveBids_withUserId_returns200() throws Exception {
        when(myActiveBidsService.myActiveBids(BIDDER_ID)).thenReturn(MyActiveBidsResponse.builder()
                .items(List.of()).counts(MyActiveBidCounts.builder().build()).build());

        mockMvc.perform(get("/api/v1/bids/me/active").header("X-User-Id", BIDDER_ID.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void auctionHistory_withoutUserId_isPublic() throws Exception {
        when(bidHistoryService.auctionHistory(eq(AUCTION_ID), any(BidHistoryQuery.class)))
                .thenReturn(emptyPage());

        mockMvc.perform(get("/api/v1/bids/auction/" + AUCTION_ID)).andExpect(status().isOk());
    }

    @Test
    void myBids_withoutUserId_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/bids/auction/" + AUCTION_ID + "/my-bids"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(bidHistoryService);
    }

    @Test
    void myBids_withUserId_returns200() throws Exception {
        when(bidHistoryService.myBids(eq(AUCTION_ID), eq(BIDDER_ID), any(BidHistoryQuery.class)))
                .thenReturn(emptyPage());

        mockMvc.perform(get("/api/v1/bids/auction/" + AUCTION_ID + "/my-bids")
                .header("X-User-Id", BIDDER_ID.toString())).andExpect(status().isOk());
    }

    @Test
    void buyNow_withoutUserId_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/bids/buy-now").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"auctionId\": \"" + AUCTION_ID + "\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(buyNowService);
    }

    @Test
    void buyNow_withUserId_returns201() throws Exception {
        when(buyNowService.buyNow(eq(BIDDER_ID), any(BuyNowRequest.class))).thenReturn(BuyNowResponse.builder()
                .auctionId(AUCTION_ID).price(new BigDecimal("200.00"))
                .endedAt(OffsetDateTime.parse("2026-10-01T12:00:00Z")).build());

        mockMvc.perform(post("/api/v1/bids/buy-now").header("X-User-Id", BIDDER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"auctionId\": \"" + AUCTION_ID + "\"}"))
                .andExpect(status().isCreated());
    }

    private static PageResponse<BidHistoryResponse> emptyPage() {
        return PageResponse.<BidHistoryResponse>builder()
                .data(List.of())
                .pagination(PaginationMeta.builder().page(0).limit(20).total(0).totalPages(0).build())
                .build();
    }
}
