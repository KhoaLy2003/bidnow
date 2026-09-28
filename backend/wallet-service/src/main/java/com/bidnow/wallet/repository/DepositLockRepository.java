package com.bidnow.wallet.repository;

import com.bidnow.wallet.domain.entity.DepositLock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DepositLockRepository extends JpaRepository<DepositLock, UUID> {
    Optional<DepositLock> findByWalletIdAndAuctionId(UUID walletId, UUID auctionId);
}
