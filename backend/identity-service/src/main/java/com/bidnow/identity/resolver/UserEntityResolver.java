package com.bidnow.identity.resolver;

import com.bidnow.common.aop.EntityResolver;
import com.bidnow.identity.domain.entity.User;
import com.bidnow.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserEntityResolver implements EntityResolver<User> {

    private final UserRepository userRepository;

    @Override
    public String entityType() {
        return "User";
    }

    @Override
    public Optional<User> resolve(String entityId) {
        return userRepository.findById(UUID.fromString(entityId));
    }
}
