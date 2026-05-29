package com.bidnow.auction.repository;

import com.bidnow.auction.domain.entity.AuctionImage;
import com.bidnow.auction.domain.entity.AuctionItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuctionImageRepository extends JpaRepository<AuctionImage, UUID> {

    List<AuctionImage> findByAuctionOrderByDisplayOrderAsc(AuctionItem auction);

    List<AuctionImage> findByAuctionInOrderByDisplayOrderAsc(List<AuctionItem> auctions);

    void deleteByAuction(AuctionItem auction);
}
