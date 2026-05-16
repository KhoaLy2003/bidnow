package com.bidnow.auction.repository;

import com.bidnow.auction.domain.entity.AuctionStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuctionStatusHistoryRepository extends JpaRepository<AuctionStatusHistory, UUID> {
}
