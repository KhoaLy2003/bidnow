package com.bidnow.auction.repository;

import com.bidnow.auction.domain.entity.AuctionImage;
import com.bidnow.auction.domain.entity.AuctionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AuctionImageRepository extends JpaRepository<AuctionImage, UUID> {

    List<AuctionImage> findByAuctionOrderByDisplayOrderAsc(AuctionItem auction);

    @Query("SELECT img FROM AuctionImage img JOIN FETCH img.auction WHERE img.auction.id IN :auctionIds ORDER BY img.displayOrder ASC")
    List<AuctionImage> findByAuctionIdInOrderByDisplayOrderAsc(@Param("auctionIds") Collection<UUID> auctionIds);

    void deleteByAuction(AuctionItem auction);
}
