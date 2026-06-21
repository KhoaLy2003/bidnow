package com.bidnow.auction.repository;

import com.bidnow.auction.domain.entity.AuctionImage;
import com.bidnow.auction.domain.entity.AuctionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AuctionImageRepository extends JpaRepository<AuctionImage, UUID> {

    List<AuctionImage> findByAuctionOrderByDisplayOrderAsc(AuctionItem auction);

    @Query("SELECT img FROM AuctionImage img JOIN FETCH img.auction WHERE img.auction.id IN :auctionIds ORDER BY img.displayOrder ASC")
    List<AuctionImage> findByAuctionIdInOrderByDisplayOrderAsc(@Param("auctionIds") Collection<UUID> auctionIds);

    // clearAutomatically = true flushes the bulk DELETE to the DB and clears the persistence
    // context before saveAll() runs, preventing idx_auction_images_primary constraint violations.
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM AuctionImage img WHERE img.auction = :auction")
    void deleteByAuction(@Param("auction") AuctionItem auction);
}
