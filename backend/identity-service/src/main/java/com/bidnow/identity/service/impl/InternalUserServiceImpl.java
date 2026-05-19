package com.bidnow.identity.service.impl;

import com.bidnow.common.dto.UserDto;
import com.bidnow.identity.domain.entity.User;
import com.bidnow.identity.domain.enums.AccountStatus;
import com.bidnow.identity.repository.UserRepository;
import com.bidnow.identity.service.InternalUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InternalUserServiceImpl implements InternalUserService {

    private final UserRepository userRepository;

    @Override
    public List<String> getActiveUserEmails() {
        log.info("Fetching all active user emails internally");
        return userRepository.findEmailsByAccountStatus(AccountStatus.ACTIVE);
    }

    @Override
    public Map<UUID, String> getEmailsByUserIds(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        log.info("Fetching emails for {} user IDs internally", userIds.size());
        List<User> users = userRepository.findAllById(userIds);
        return users.stream()
                .collect(Collectors.toMap(User::getId, User::getEmail));
    }

    @Override
    public Optional<UserDto> getUserById(UUID userId) {
        log.info("Fetching user by ID internally: {}", userId);
        return userRepository.findById(userId)
                .map(user -> UserDto.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .role(user.getRole())
                        .isActive(user.getIsActive())
                        .build());
    }

    @Override
    public Optional<UUID> getUserIdByEmail(String email) {
        log.info("Fetching user ID by email internally: {}", email);
        return userRepository.findByEmail(email).map(User::getId);
    }

    @Override
    public List<UUID> findUserIdsByEmailContaining(String email) {
        log.info("Fetching user IDs by email pattern internally: {}", email);
        return userRepository.findIdsByEmailContaining(email);
    }
}
