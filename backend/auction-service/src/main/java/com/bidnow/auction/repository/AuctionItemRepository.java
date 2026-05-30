package com.bidnow.auction.repository;

import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.dto.response.CategoryCountResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuctionItemRepository extends JpaRepository<AuctionItem, UUID>, JpaSpecificationExecutor<AuctionItem> {

    Optional<AuctionItem> findByIdAndDeletedAtIsNull(UUID id);

    @Query("SELECT NEW com.bidnow.auction.dto.response.CategoryCountResponse(" +
            "ai.category.id, ai.category.name, ai.category.slug, COUNT(ai)) " +
            "FROM AuctionItem ai " +
            "WHERE ai.status = :status AND ai.deletedAt IS NULL " +
            "GROUP BY ai.category.id, ai.category.name, ai.category.slug")
    List<CategoryCountResponse> countByStatusGroupByCategory(@Param("status") AuctionStatus status);
}
