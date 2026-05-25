package com.bidnow.auction.repository;

import com.bidnow.auction.domain.entity.AuctionCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuctionCategoryRepository extends JpaRepository<AuctionCategory, UUID> {
}
