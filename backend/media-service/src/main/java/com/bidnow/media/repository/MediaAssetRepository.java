/*
 * BidNow Auction System
 */
package com.bidnow.media.repository;

import com.bidnow.media.domain.entity.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {

    List<MediaAsset> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    List<MediaAsset> findByEntityIdAndEntityType(UUID entityId, String entityType);

    Optional<MediaAsset> findByS3Key(String s3Key);
}
