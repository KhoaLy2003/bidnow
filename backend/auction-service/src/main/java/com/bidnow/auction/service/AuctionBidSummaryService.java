package com.bidnow.auction.service;

import com.bidnow.auction.domain.entity.AuctionImage;
import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.dto.response.AuctionBidSummaryResponse;
import com.bidnow.auction.repository.AuctionImageRepository;
import com.bidnow.auction.repository.AuctionItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Batch read used by bidding-service to enrich a bidder's auctions. Only {@code ACTIVE}, non-deleted
 * auctions are returned; any other id is silently omitted, so callers can pass every auction a user ever
 * bid on and get back just the live ones.
 */
@Service
@RequiredArgsConstructor
public class AuctionBidSummaryService {

    private final AuctionItemRepository auctionItemRepository;
    private final AuctionImageRepository auctionImageRepository;

    @Transactional(readOnly = true)
    public List<AuctionBidSummaryResponse> getActiveSummaries(Collection<UUID> auctionIds) {
        if (auctionIds.isEmpty()) {
            return List.of();
        }
        List<AuctionItem> active =
                auctionItemRepository.findByIdInAndStatusAndDeletedAtIsNull(auctionIds, AuctionStatus.ACTIVE);
        if (active.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<AuctionImage>> imagesByAuction = auctionImageRepository
                .findByAuctionIdInOrderByDisplayOrderAsc(active.stream().map(AuctionItem::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(img -> img.getAuction().getId()));
        return active.stream().map(item -> toSummary(item, imagesByAuction.getOrDefault(item.getId(), List.of()))).toList();
    }

    private AuctionBidSummaryResponse toSummary(AuctionItem item, List<AuctionImage> images) {
        AuctionImage primary = images.stream()
                .filter(AuctionImage::getIsPrimary)
                .findFirst()
                .orElse(images.isEmpty() ? null : images.get(0));
        return AuctionBidSummaryResponse.builder()
                .auctionId(item.getId())
                .title(item.getTitle())
                .primaryImageUrl(primary == null ? null : primary.getImageUrl())
                .currentPrice(item.getCurrentPrice())
                .currentWinnerId(item.getCurrentWinnerId())
                .totalBids(item.getTotalBids())
                .endTime(item.getEndTime())
                .build();
    }
}
