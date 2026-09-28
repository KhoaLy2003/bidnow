package com.bidnow.wallet.repository;

import com.bidnow.wallet.domain.entity.PaymentHold;
import com.bidnow.wallet.domain.enums.PaymentHoldStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentHoldRepository extends JpaRepository<PaymentHold, UUID> {

    boolean existsByAuctionId(UUID auctionId);

    /** SELECT ... FOR UPDATE on the hold row. Lock order: hold row first, then wallet rows. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM PaymentHold h WHERE h.auctionId = :auctionId")
    Optional<PaymentHold> findByAuctionIdForUpdate(@Param("auctionId") UUID auctionId);

    List<PaymentHold> findByWinnerUserIdAndStatusOrderByDeadlineAsc(UUID winnerUserId, PaymentHoldStatus status);

    /** Auction ids of holds in {@code status} whose deadline is before {@code now}, earliest first. */
    @Query("SELECT h.auctionId FROM PaymentHold h WHERE h.status = :status AND h.deadline < :now ORDER BY h.deadline ASC")
    List<UUID> findExpiredAuctionIds(@Param("status") PaymentHoldStatus status,
                                     @Param("now") LocalDateTime now, Pageable page);

    /** SELECT ... FOR UPDATE SKIP LOCKED (Hibernate lock timeout -2): a hold locked by another instance is skipped. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT h FROM PaymentHold h WHERE h.auctionId = :auctionId")
    Optional<PaymentHold> findByAuctionIdForUpdateSkipLocked(@Param("auctionId") UUID auctionId);
}
