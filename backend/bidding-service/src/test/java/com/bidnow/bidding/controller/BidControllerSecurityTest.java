package com.bidnow.bidding.controller;

import com.bidnow.bidding.config.SecurityConfig;
import com.bidnow.bidding.dto.request.BidHistoryQuery;
import com.bidnow.bidding.dto.response.BidHistoryResponse;
import com.bidnow.bidding.exception.BiddingExceptionHandler;
import com.bidnow.bidding.service.BidHistoryService;
import com.bidnow.bidding.service.BidService;
import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.dto.PaginationMeta;
import com.bidnow.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    private static PageResponse<BidHistoryResponse> emptyPage() {
        return PageResponse.<BidHistoryResponse>builder()
                .data(List.of())
                .pagination(PaginationMeta.builder().page(0).limit(20).total(0).totalPages(0).build())
                .build();
    }
}
