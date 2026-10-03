package com.bidnow.media.repository;

import com.bidnow.media.domain.entity.Notification;
import com.bidnow.media.domain.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification> {
    Page<Notification> findByUserId(UUID userId, Pageable pageable);

    long countByUserIdAndStatus(UUID userId, NotificationStatus status);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    Optional<Notification> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    long countByUserIdAndReadAtIsNullAndDeletedAtIsNull(UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Notification n set n.readAt = :now, n.updatedAt = :now
            where n.userId = :userId and n.readAt is null and n.deletedAt is null""")
    int markAllRead(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Notification n set n.deletedAt = :now, n.updatedAt = :now
            where n.userId = :userId and n.deletedAt is null""")
    int softDeleteAll(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Notification n set n.deletedAt = :now, n.updatedAt = :now
            where n.userId = :userId and n.readAt is not null and n.deletedAt is null""")
    int softDeleteRead(@Param("userId") UUID userId, @Param("now") LocalDateTime now);
}
