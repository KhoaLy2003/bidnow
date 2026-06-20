package com.bidnow.auction.service;

import com.bidnow.auction.dto.request.AdminAuctionFilterRequest;
import com.bidnow.auction.dto.request.AdminAuctionReasonRequest;
import com.bidnow.auction.dto.response.AdminAuctionDetailResponse;
import com.bidnow.auction.dto.response.AdminAuctionSummaryResponse;
import com.bidnow.auction.dto.response.SellerAuctionResponse;
import com.bidnow.common.dto.PageResponse;

import java.util.UUID;

public interface AdminAuctionService {

    PageResponse<AdminAuctionSummaryResponse> listAuctions(AdminAuctionFilterRequest filter);

    AdminAuctionDetailResponse getAuctionDetail(UUID id);

    SellerAuctionResponse rejectAuction(UUID adminId, UUID id, AdminAuctionReasonRequest request);

    SellerAuctionResponse cancelAuction(UUID adminId, UUID id, AdminAuctionReasonRequest request);

    SellerAuctionResponse forceCloseAuction(UUID adminId, UUID id, AdminAuctionReasonRequest request);
}
