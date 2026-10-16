package com.bidnow.bidding.repository;

import com.bidnow.bidding.domain.entity.Bid;
import com.bidnow.bidding.repository.projection.BidderAuctionBid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BidRepository extends JpaRepository<Bid, UUID> {

    Page<Bid> findByAuctionId(UUID auctionId, Pageable pageable);

    Page<Bid> findByAuctionIdAndBidderId(UUID auctionId, UUID bidderId, Pageable pageable);

    /**
     * The auctions a bidder has bid on, most recently bid-on first, with their highest amount in each.
     * Served by {@code idx_bids_bidder_auction_created}; the page bounds how far back it scans.
     */
    @Query("SELECT new com.bidnow.bidding.repository.projection.BidderAuctionBid(b.auctionId, MAX(b.amount), MAX(b.createdAt)) "
            + "FROM Bid b WHERE b.bidderId = :bidderId GROUP BY b.auctionId ORDER BY MAX(b.createdAt) DESC")
    List<BidderAuctionBid> findAuctionBidsByBidder(@Param("bidderId") UUID bidderId, Pageable pageable);
}
