package com.bidnow.auction.service;

import com.bidnow.auction.dto.response.AuctionCategoryResponse;

import java.util.List;

public interface AuctionCategoryService {
    List<AuctionCategoryResponse> getCategories();

}
