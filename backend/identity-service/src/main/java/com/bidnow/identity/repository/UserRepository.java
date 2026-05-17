package com.bidnow.identity.repository;

import com.bidnow.identity.domain.entity.User;
import com.bidnow.identity.domain.enums.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    @Query("SELECT u.email FROM User u WHERE u.accountStatus = :status")
    List<String> findEmailsByAccountStatus(@Param("status") AccountStatus status);

    @Query("SELECT u.email FROM User u WHERE u.id IN :ids")
    List<String> findEmailsByIds(@Param("ids") List<UUID> ids);
}
