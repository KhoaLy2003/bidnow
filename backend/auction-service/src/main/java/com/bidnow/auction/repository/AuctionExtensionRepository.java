package com.bidnow.auction.repository;

import com.bidnow.auction.domain.entity.AuctionExtension;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuctionExtensionRepository extends JpaRepository<AuctionExtension, UUID> {
}
