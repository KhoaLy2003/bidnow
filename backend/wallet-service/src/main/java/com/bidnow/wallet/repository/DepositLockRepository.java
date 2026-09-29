package com.bidnow.wallet.repository;

import com.bidnow.wallet.domain.entity.DepositLock;
import com.bidnow.wallet.domain.enums.DepositLockStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepositLockRepository extends JpaRepository<DepositLock, UUID> {
    Optional<DepositLock> findByWalletIdAndAuctionId(UUID walletId, UUID auctionId);

    List<DepositLock> findByAuctionIdAndStatus(UUID auctionId, DepositLockStatus status);

    long countByStatus(DepositLockStatus status);

    List<DepositLock> findByWalletIdAndStatus(UUID walletId, DepositLockStatus status);
}
