package com.bidnow.media.repository;

import com.bidnow.media.domain.entity.Notification;
import com.bidnow.media.domain.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findByUserId(UUID userId, Pageable pageable);

    long countByUserIdAndStatus(UUID userId, NotificationStatus status);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);
}
