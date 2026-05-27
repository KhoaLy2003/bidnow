package com.bidnow.auction.repository;

import com.bidnow.auction.domain.entity.AuctionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface AuctionItemRepository extends JpaRepository<AuctionItem, UUID>, JpaSpecificationExecutor<AuctionItem> {

    Optional<AuctionItem> findByIdAndDeletedAtIsNull(UUID id);
}
