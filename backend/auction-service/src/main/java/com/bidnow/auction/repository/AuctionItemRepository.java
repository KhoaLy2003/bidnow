package com.bidnow.auction.repository;

import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.repository.projection.CategoryAuctionCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuctionItemRepository extends JpaRepository<AuctionItem, UUID>, JpaSpecificationExecutor<AuctionItem> {

    Optional<AuctionItem> findByIdAndDeletedAtIsNull(UUID id);

    @Query("SELECT ac.id AS categoryId, ac.name AS categoryName, ac.slug AS slug, " +
            "COUNT(ai.id) AS count " +
            "FROM AuctionCategory ac " +
            "LEFT JOIN AuctionItem ai ON ai.category = ac " +
            "AND ai.status = :status " +
            "AND ai.deletedAt IS NULL " +
            "WHERE ac.isActive = true " +
            "GROUP BY ac.id, ac.name, ac.slug")
    List<CategoryAuctionCount> countByStatusGroupByCategory(@Param("status") AuctionStatus status);
}
