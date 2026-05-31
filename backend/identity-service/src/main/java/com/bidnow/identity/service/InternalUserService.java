package com.bidnow.identity.service;

import com.bidnow.common.dto.UserDto;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface InternalUserService {
    List<String> getActiveUserEmails();

    Map<UUID, String> getEmailsByUserIds(List<UUID> userIds);

    Optional<UserDto> getUserById(UUID userId);

    Optional<UUID> getUserIdByEmail(String email);

    List<UUID> findUserIdsByEmailContaining(String email);
}
