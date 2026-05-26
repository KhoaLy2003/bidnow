package com.bidnow.auction.service;

import com.bidnow.auction.dto.request.CreateAuctionRequest;
import com.bidnow.auction.dto.request.UpdateAuctionRequest;
import com.bidnow.auction.dto.response.AuctionCategoryResponse;
import com.bidnow.auction.dto.response.AuctionResponse;
import com.bidnow.auction.dto.response.AuctionSummaryResponse;
import com.bidnow.common.dto.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface AuctionService {

    AuctionResponse createAuction(UUID sellerId, CreateAuctionRequest request);

    PageResponse<AuctionSummaryResponse> getMyAuctions(UUID sellerId, String type, UUID categoryId, Pageable pageable);

    AuctionResponse updateAuction(UUID sellerId, UUID auctionId, UpdateAuctionRequest request);

    void deleteAuction(UUID sellerId, UUID auctionId);
}
