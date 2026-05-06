package com.bidnow.identity.service.impl;

import com.bidnow.common.constant.ErrorCodes;
import com.bidnow.common.dto.event.UserRegisteredEvent;
import com.bidnow.common.exception.BadRequestException;
import com.bidnow.identity.domain.entity.User;
import com.bidnow.identity.domain.enums.UserRole;
import com.bidnow.identity.domain.enums.UserStatus;
import com.bidnow.identity.dto.request.RegisterRequest;
import com.bidnow.identity.dto.response.RegisterResponse;
import com.bidnow.identity.kafka.IdentityKafkaProducer;
import com.bidnow.identity.mapper.UserMapper;
import com.bidnow.identity.repository.UserRepository;
import com.bidnow.identity.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdentityKafkaProducer kafkaProducer;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already registered", ErrorCodes.INVALID_INPUT);
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .role(UserRole.USER)
                .status(UserStatus.PENDING)
                .build();

        user = userRepository.save(user);

        kafkaProducer.publishUserRegisteredEvent(UserRegisteredEvent.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .registeredAt(user.getCreatedAt())
                .build());

        log.info("User registered successfully: {}", user.getEmail());
        return userMapper.toRegisterResponse(user);
    }
}
