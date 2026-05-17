package com.bidnow.identity.service.impl;

import com.bidnow.identity.domain.enums.AccountStatus;
import com.bidnow.identity.repository.UserRepository;
import com.bidnow.identity.service.InternalUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

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
    public List<String> getEmailsByUserIds(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }
        log.info("Fetching emails for {} user IDs internally", userIds.size());
        return userRepository.findEmailsByIds(userIds);
    }
}
