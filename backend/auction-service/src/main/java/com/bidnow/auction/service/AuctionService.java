package com.bidnow.auction.service;

import com.bidnow.auction.dto.request.CancelAuctionRequest;
import com.bidnow.auction.dto.request.CreateAuctionRequest;
import com.bidnow.auction.dto.request.PublicAuctionFilterRequest;
import com.bidnow.auction.dto.request.UpdateAuctionRequest;
import com.bidnow.auction.dto.response.AuctionBrowseItem;
import com.bidnow.auction.dto.response.AuctionDetailResponse;
import com.bidnow.auction.dto.response.SellerAuctionResponse;
import com.bidnow.auction.dto.response.AuctionSummaryResponse;
import com.bidnow.auction.dto.response.CategoryCountResponse;
import com.bidnow.common.dto.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface AuctionService {

    AuctionDetailResponse getAuctionById(UUID id);

    SellerAuctionResponse publishAuction(UUID sellerId, UUID id);

    void cancelAuction(UUID sellerId, UUID id, CancelAuctionRequest request);

    SellerAuctionResponse createAuction(UUID sellerId, CreateAuctionRequest request);

    PageResponse<AuctionSummaryResponse> getMyAuctions(UUID sellerId, String type, UUID categoryId, Pageable pageable);

    SellerAuctionResponse updateAuction(UUID sellerId, UUID auctionId, UpdateAuctionRequest request);

    void deleteAuction(UUID sellerId, UUID auctionId);

    PageResponse<AuctionBrowseItem> browseAuctions(PublicAuctionFilterRequest filter);

    List<CategoryCountResponse> getCategoryAuctionCounts();
}
