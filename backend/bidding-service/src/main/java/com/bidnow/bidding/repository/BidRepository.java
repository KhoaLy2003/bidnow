package com.bidnow.bidding.repository;

import com.bidnow.bidding.domain.entity.Bid;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BidRepository extends JpaRepository<Bid, UUID> {
}
