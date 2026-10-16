package com.bidnow.bidding.service;

import com.bidnow.bidding.dto.AuctionBidSummary;
import com.bidnow.bidding.dto.response.MyActiveBidCounts;
import com.bidnow.bidding.dto.response.MyActiveBidResponse;
import com.bidnow.bidding.dto.response.MyActiveBidsResponse;
import com.bidnow.bidding.repository.BidRepository;
import com.bidnow.bidding.repository.projection.BidderAuctionBid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The active auctions a bidder is bidding on. bids live here and auction state lives in
 * auction-service, so this reads the bidder's auctions from {@code bids}, asks auction-service which of
 * them are still ACTIVE, and joins the two.
 */
@Service
@RequiredArgsConstructor
public class MyActiveBidsService {

    /** How many of the bidder's most recently bid-on auctions are checked for being active. */
    static final int MAX_SCANNED_AUCTIONS = 300;
    /** Cap on the returned list. */
    static final int MAX_ITEMS = 100;

    private final BidRepository bidRepository;
    private final AuctionBidGateway auctionBidGateway;

    public MyActiveBidsResponse myActiveBids(UUID bidderId) {
        List<BidderAuctionBid> bids =
                bidRepository.findAuctionBidsByBidder(bidderId, PageRequest.of(0, MAX_SCANNED_AUCTIONS));
        if (bids.isEmpty()) {
            return response(List.of());
        }
        Map<UUID, BidderAuctionBid> bidByAuction = new LinkedHashMap<>();
        bids.forEach(bid -> bidByAuction.put(bid.auctionId(), bid));

        List<MyActiveBidResponse> items = auctionBidGateway.activeSummaries(List.copyOf(bidByAuction.keySet())).stream()
                .filter(summary -> bidByAuction.containsKey(summary.getAuctionId()))
                .map(summary -> toItem(bidderId, summary, bidByAuction.get(summary.getAuctionId())))
                .sorted(Comparator.comparing(MyActiveBidResponse::getEndTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(MAX_ITEMS)
                .toList();
        return response(items);
    }

    private static MyActiveBidResponse toItem(UUID bidderId, AuctionBidSummary summary, BidderAuctionBid bid) {
        return MyActiveBidResponse.builder()
                .auctionId(summary.getAuctionId())
                .title(summary.getTitle())
                .primaryImageUrl(summary.getPrimaryImageUrl())
                .currentPrice(summary.getCurrentPrice())
                .myHighestBid(bid.highestAmount())
                .totalBids(summary.getTotalBids())
                .endTime(summary.getEndTime())
                .lastBidAt(bid.lastBidAt().atZone(ZoneId.systemDefault()).toOffsetDateTime())
                .winning(bidderId.equals(summary.getCurrentWinnerId()))
                .build();
    }

    private static MyActiveBidsResponse response(List<MyActiveBidResponse> items) {
        int winning = (int) items.stream().filter(MyActiveBidResponse::isWinning).count();
        return MyActiveBidsResponse.builder()
                .items(items)
                .counts(MyActiveBidCounts.builder()
                        .all(items.size())
                        .winning(winning)
                        .outbid(items.size() - winning)
                        .build())
                .build();
    }
}
