package com.bidnow.identity.mapper;

import com.bidnow.identity.domain.entity.User;
import com.bidnow.identity.dto.response.RegisterResponse;
import com.bidnow.identity.dto.response.VerifyOtpResponse;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class UserMapper {

    public RegisterResponse toRegisterResponse(User user) {
        return RegisterResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .accountStatus(user.getAccountStatus().name())
                .isEmailVerified(user.getIsEmailVerified())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .build();
    }

    public VerifyOtpResponse toVerifyOtpResponse(User user) {
        return VerifyOtpResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .accountStatus(user.getAccountStatus().name())
                .isEmailVerified(user.getIsEmailVerified())
                .verifiedAt(LocalDateTime.now())
                .build();
    }
}
