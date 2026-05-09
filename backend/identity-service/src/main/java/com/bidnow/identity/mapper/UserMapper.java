package com.bidnow.identity.mapper;

import com.bidnow.identity.domain.entity.User;
import com.bidnow.identity.dto.response.RegisterResponse;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public RegisterResponse toRegisterResponse(User user) {
        return RegisterResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .isEmailVerified(user.getIsEmailVerified())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
