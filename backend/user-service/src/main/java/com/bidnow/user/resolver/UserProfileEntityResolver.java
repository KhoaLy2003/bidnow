package com.bidnow.user.resolver;

import com.bidnow.common.aop.EntityResolver;
import com.bidnow.user.domain.entity.UserProfile;
import com.bidnow.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserProfileEntityResolver implements EntityResolver<UserProfile> {

    private final UserProfileRepository userProfileRepository;

    @Override
    public String entityType() {
        return "UserProfile";
    }

    @Override
    public Optional<UserProfile> resolve(String entityId) {
        return userProfileRepository.findById(UUID.fromString(entityId));
    }
}
