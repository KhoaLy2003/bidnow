package com.bidnow.auction.service.impl;

import com.bidnow.auction.config.CacheConfig;
import com.bidnow.auction.dto.response.AuctionCategoryResponse;
import com.bidnow.auction.mapper.AuctionMapper;
import com.bidnow.auction.repository.AuctionCategoryRepository;
import com.bidnow.auction.service.AuctionCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuctionCategoryServiceImpl implements AuctionCategoryService {
    private final AuctionCategoryRepository auctionCategoryRepository;
    private final AuctionMapper auctionMapper;

    @Override
    @Cacheable(value = CacheConfig.CACHE_CATEGORIES, key = "'all'")
    public List<AuctionCategoryResponse> getCategories() {
        return auctionCategoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(auctionMapper::toCategory)
                .toList();
    }
}
