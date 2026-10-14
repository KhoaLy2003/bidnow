package com.bidnow.wallet.repository;

import com.bidnow.wallet.domain.entity.Wallet;
import com.bidnow.wallet.domain.enums.WalletStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    Optional<Wallet> findByUserId(UUID userId);

    /**
     * Loads the wallet with SELECT ... FOR UPDATE. Must be the first read of this wallet in the
     * transaction — an earlier unlocked read would leave a stale cached entity in the persistence context.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.userId = :userId")
    Optional<Wallet> findByUserIdForUpdate(@Param("userId") UUID userId);

    /** Loads the wallet by id with SELECT ... FOR UPDATE. Same first-read rule as findByUserIdForUpdate. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findByIdForUpdate(@Param("id") UUID id);

    /** Wallet id only — does not load the entity, so it never pollutes the persistence context before a row lock. */
    @Query("SELECT w.id FROM Wallet w WHERE w.userId = :userId")
    Optional<UUID> findIdByUserId(@Param("userId") UUID userId);

    long countByStatusAndUserIdNot(WalletStatus status, UUID userId);

    @Query("SELECT COALESCE(SUM(w.lockedBalance), 0.0) FROM Wallet w")
    BigDecimal sumLockedBalance();
}
