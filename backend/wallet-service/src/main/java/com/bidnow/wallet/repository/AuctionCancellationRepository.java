package com.bidnow.wallet.repository;

import com.bidnow.wallet.domain.entity.AuctionCancellation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuctionCancellationRepository extends JpaRepository<AuctionCancellation, UUID> {
}
