/*
 * BidNow Auction System
 */
package com.bidnow.user.repository;

import com.bidnow.user.domain.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    Optional<UserProfile> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    List<UserProfile> findByUserIdIn(Collection<UUID> userIds);
}
