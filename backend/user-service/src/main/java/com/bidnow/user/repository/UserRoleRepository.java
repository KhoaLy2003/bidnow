/*
 * BidNow Auction System
 */
package com.bidnow.user.repository;

import com.bidnow.user.domain.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    List<UserRole> findByUserId(UUID userId);
}
