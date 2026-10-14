/*
 * BidNow Auction System
 */
package com.bidnow.user.repository;

import com.bidnow.user.domain.entity.UserPreferences;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserPreferencesRepository extends JpaRepository<UserPreferences, UUID> {

    Optional<UserPreferences> findByUserId(UUID userId);

    List<UserPreferences> findByUserIdIn(Collection<UUID> userIds);
}
