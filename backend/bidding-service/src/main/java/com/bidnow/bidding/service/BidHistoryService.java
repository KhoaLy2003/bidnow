package com.bidnow.bidding.service;

import com.bidnow.bidding.domain.entity.Bid;
import com.bidnow.bidding.dto.request.BidHistoryQuery;
import com.bidnow.bidding.dto.response.BidHistoryResponse;
import com.bidnow.bidding.repository.BidRepository;
import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.dto.UserSummaryResponse;
import com.bidnow.common.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Paged bid history; bidder names come from one batch lookup per page (never N+1). */
@Service
@RequiredArgsConstructor
public class BidHistoryService {

    private final BidRepository bidRepository;
    private final UserSummaryCacheService userSummaries;

    public PageResponse<BidHistoryResponse> auctionHistory(UUID auctionId, BidHistoryQuery query) {
        return toResponse(bidRepository.findByAuctionId(auctionId, query.toPageable()));
    }

    public PageResponse<BidHistoryResponse> myBids(UUID auctionId, UUID bidderId, BidHistoryQuery query) {
        return toResponse(bidRepository.findByAuctionIdAndBidderId(auctionId, bidderId, query.toPageable()));
    }

    private PageResponse<BidHistoryResponse> toResponse(Page<Bid> page) {
        if (page.isEmpty()) {
            return PaginationUtils.toPageResponse(page, List.of());
        }
        Map<UUID, UserSummaryResponse> bidders =
                userSummaries.getAll(page.getContent().stream().map(Bid::getBidderId).distinct().toList());
        List<BidHistoryResponse> items = page.getContent().stream().map(bid -> {
            UserSummaryResponse bidder = bidders.get(bid.getBidderId());
            return BidHistoryResponse.builder()
                    .id(bid.getId())
                    .auctionId(bid.getAuctionId())
                    .bidderId(bid.getBidderId())
                    .bidderName(bidder == null ? UserSummaryCacheService.UNKNOWN_BIDDER : bidder.getName())
                    .bidderAvatarUrl(bidder == null ? null : bidder.getAvatarUrl())
                    .amount(bid.getAmount())
                    .placedAt(bid.getCreatedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime())
                    .autoBid(bid.isAutoBid())
                    .antiSnipingTriggered(bid.isAntiSnipingTriggered())
                    .build();
        }).toList();
        return PaginationUtils.toPageResponse(page, items);
    }
}
