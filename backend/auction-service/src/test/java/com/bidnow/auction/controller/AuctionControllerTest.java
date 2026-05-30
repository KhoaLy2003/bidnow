package com.bidnow.auction.controller;

import com.bidnow.auction.dto.response.AuctionBrowseItem;
import com.bidnow.auction.dto.response.AuctionDetailResponse;
import com.bidnow.auction.dto.response.CategoryCountResponse;
import com.bidnow.auction.service.AuctionService;
import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.dto.PaginationMeta;
import com.bidnow.common.dto.UserSummaryResponse;
import com.bidnow.common.exception.GlobalExceptionHandler;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.common.resolver.UserIdArgumentResolver;

import static com.bidnow.common.constant.ErrorCodes.NOT_FOUND;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuctionControllerTest {

    @Mock
    private AuctionService auctionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuctionController(auctionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new UserIdArgumentResolver())
                .build();
    }

    private PageResponse<AuctionBrowseItem> emptyPage() {
        return PageResponse.<AuctionBrowseItem>builder()
                .data(List.of())
                .pagination(PaginationMeta.builder()
                        .page(0).limit(20).total(0L).totalPages(0)
                        .build())
                .build();
    }

    // -------------------------------------------------------
    // GET /api/v1/auctions/public
    // -------------------------------------------------------

    @Test
    void browseAuctions_noParams_returns200() throws Exception {
        when(auctionService.browseAuctions(any())).thenReturn(emptyPage());

        mockMvc.perform(get("/api/v1/auctions/public"))
                .andExpect(status().isOk());

        verify(auctionService, times(1)).browseAuctions(any());
    }

    @Test
    void browseAuctions_validFilters_returns200() throws Exception {
        when(auctionService.browseAuctions(any())).thenReturn(emptyPage());

        mockMvc.perform(get("/api/v1/auctions/public")
                        .param("minPrice", "10")
                        .param("maxPrice", "500")
                        .param("sortBy", "PRICE_LOW_HIGH")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk());
    }

    @Test
    void browseAuctions_sizeExceedsCap_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/auctions/public")
                        .param("size", "200"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void browseAuctions_sizeZero_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/auctions/public")
                        .param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void browseAuctions_invalidSortBy_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/auctions/public")
                        .param("sortBy", "INVALID_SORT"))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------
    // GET /api/v1/auctions/public/{id}
    // -------------------------------------------------------

    @Test
    void getAuctionById_knownId_returns200WithSellerInfo() throws Exception {
        UUID auctionId = UUID.randomUUID();
        UserSummaryResponse seller = UserSummaryResponse.builder()
                .id(UUID.randomUUID()).name("Alice").build();
        AuctionDetailResponse detail = AuctionDetailResponse.builder()
                .id(auctionId).title("Vintage Watch").seller(seller).build();

        when(auctionService.getAuctionById(auctionId)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/auctions/public/{id}", auctionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(auctionId.toString()))
                .andExpect(jsonPath("$.data.title").value("Vintage Watch"))
                .andExpect(jsonPath("$.data.seller.name").value("Alice"));
    }

    @Test
    void getAuctionById_unknownId_returns404() throws Exception {
        UUID auctionId = UUID.randomUUID();
        when(auctionService.getAuctionById(auctionId))
                .thenThrow(new NotFoundException("Auction not found", NOT_FOUND));

        mockMvc.perform(get("/api/v1/auctions/public/{id}", auctionId))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAuctionById_userServiceDown_returns200WithNullSeller() throws Exception {
        UUID auctionId = UUID.randomUUID();
        AuctionDetailResponse detail = AuctionDetailResponse.builder()
                .id(auctionId).seller(null).build();

        when(auctionService.getAuctionById(auctionId)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/auctions/public/{id}", auctionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seller").doesNotExist());
    }

    // -------------------------------------------------------
    // GET /api/v1/auctions/public/category-counts
    // -------------------------------------------------------

    @Test
    void getCategoryAuctionCounts_returns200WithCounts() throws Exception {
        UUID catId = UUID.randomUUID();
        CategoryCountResponse count = new CategoryCountResponse(catId, "Electronics", "electronics", 3L);
        when(auctionService.getCategoryAuctionCounts()).thenReturn(List.of(count));

        mockMvc.perform(get("/api/v1/auctions/public/category-counts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].categoryName").value("Electronics"))
                .andExpect(jsonPath("$.data[0].count").value(3));

        verify(auctionService, times(1)).getCategoryAuctionCounts();
    }
}
