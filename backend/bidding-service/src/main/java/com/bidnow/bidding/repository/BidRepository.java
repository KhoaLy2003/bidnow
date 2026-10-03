package com.bidnow.bidding.repository;

import com.bidnow.bidding.domain.entity.Bid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BidRepository extends JpaRepository<Bid, UUID> {

    Page<Bid> findByAuctionId(UUID auctionId, Pageable pageable);

    Page<Bid> findByAuctionIdAndBidderId(UUID auctionId, UUID bidderId, Pageable pageable);
}
