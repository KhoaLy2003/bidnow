package com.bidnow.auction.repository;

import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.repository.projection.CategoryAuctionCount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuctionItemRepository extends JpaRepository<AuctionItem, UUID>, JpaSpecificationExecutor<AuctionItem> {

    Optional<AuctionItem> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Loads a non-deleted auction with a {@code SELECT … FOR UPDATE} row lock. Every write that depends
     * on {@code status} or {@code end_time} (apply-bid, closure, cancel, force-close) must use this so
     * those operations serialize on the auction row.
     * <p>
     * Caveat: this must be the first load of that row in the transaction (or be followed by
     * {@code refresh}); otherwise Hibernate returns the cached, stale entity while still taking the lock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AuctionItem a WHERE a.id = :id AND a.deletedAt IS NULL")
    Optional<AuctionItem> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT ac.id AS categoryId, ac.name AS categoryName, ac.slug AS slug, " +
            "COUNT(ai.id) AS count " +
            "FROM AuctionCategory ac " +
            "LEFT JOIN AuctionItem ai ON ai.category = ac " +
            "AND ai.status = :status " +
            "AND ai.deletedAt IS NULL " +
            "WHERE ac.isActive = true " +
            "GROUP BY ac.id, ac.name, ac.slug")
    List<CategoryAuctionCount> countByStatusGroupByCategory(@Param("status") AuctionStatus status);

    List<AuctionItem> findByStatusAndEndTimeBeforeAndDeletedAtIsNull(AuctionStatus status, OffsetDateTime endTime);

    List<AuctionItem> findByStatusAndStartTimeBeforeAndDeletedAtIsNull(AuctionStatus status, OffsetDateTime startTime);
}
