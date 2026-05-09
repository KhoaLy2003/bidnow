package com.bidnow.identity.service.impl;

import com.bidnow.common.constant.ErrorCodes;
import com.bidnow.common.dto.request.CreateUserProfileRequest;
import com.bidnow.common.exception.BadRequestException;
import com.bidnow.common.exception.UnauthorizedException;
import com.bidnow.identity.domain.entity.RefreshToken;
import com.bidnow.identity.domain.entity.User;
import com.bidnow.identity.dto.request.LoginRequest;
import com.bidnow.identity.dto.request.RegisterRequest;
import com.bidnow.identity.dto.response.LoginResponse;
import com.bidnow.identity.dto.response.RegisterResponse;
import com.bidnow.identity.feign.UserServiceClient;
import com.bidnow.identity.kafka.IdentityKafkaProducer;
import com.bidnow.identity.mapper.UserMapper;
import com.bidnow.identity.repository.RefreshTokenRepository;
import com.bidnow.identity.repository.UserRepository;
import com.bidnow.identity.security.JwtService;
import com.bidnow.identity.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 15;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdentityKafkaProducer kafkaProducer;
    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final UserServiceClient userServiceClient;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${jwt.refresh-expiration}")
    private long jwtRefreshExpiration;

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already registered", ErrorCodes.INVALID_INPUT);
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .isEmailVerified(false)
                .isActive(true)
                .failedLoginAttempts(0)
                .build();

        user = userRepository.save(user);

        userServiceClient.createUserProfile(CreateUserProfileRequest.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .build());

        log.info("User registered successfully: {}", user.getEmail());
        return userMapper.toRegisterResponse(user);
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials", ErrorCodes.UNAUTHORIZED));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new UnauthorizedException("Account is disabled", ErrorCodes.UNAUTHORIZED);
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new UnauthorizedException("Account is temporarily locked", ErrorCodes.UNAUTHORIZED);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
                log.warn("Account locked due to too many failed attempts: {}", user.getEmail());
            }
            userRepository.save(user);
            throw new UnauthorizedException("Invalid credentials", ErrorCodes.UNAUTHORIZED);
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String accessToken = jwtService.generateToken(user);
        String rawRefreshToken = UUID.randomUUID().toString();
        String tokenHash = DigestUtils.md5DigestAsHex(rawRefreshToken.getBytes());

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtRefreshExpiration / 1000))
                .isRevoked(false)
                .createdAt(LocalDateTime.now())
                .build();
        refreshTokenRepository.save(refreshToken);

        log.info("User logged in successfully: {}", user.getEmail());
        return LoginResponse.builder()
                .accessToken(accessToken)
                .expiresIn(jwtExpiration)
                .userId(user.getId())
                .email(user.getEmail())
                .refreshToken(rawRefreshToken)
                .build();
    }

    @Override
    @Transactional
    public LoginResponse refresh(String rawRefreshToken) {
        String tokenHash = DigestUtils.md5DigestAsHex(rawRefreshToken.getBytes());

        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token", ErrorCodes.UNAUTHORIZED));

        if (Boolean.TRUE.equals(stored.getIsRevoked())) {
            throw new UnauthorizedException("Refresh token has been revoked", ErrorCodes.UNAUTHORIZED);
        }

        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("Refresh token has expired", ErrorCodes.UNAUTHORIZED);
        }

        stored.setIsRevoked(true);
        refreshTokenRepository.save(stored);

        User user = stored.getUser();
        String newRawToken = UUID.randomUUID().toString();
        String newHash = DigestUtils.md5DigestAsHex(newRawToken.getBytes());

        RefreshToken newRefreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(newHash)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtRefreshExpiration / 1000))
                .isRevoked(false)
                .createdAt(LocalDateTime.now())
                .build();
        refreshTokenRepository.save(newRefreshToken);

        String accessToken = jwtService.generateToken(user);
        return LoginResponse.builder()
                .accessToken(accessToken)
                .expiresIn(jwtExpiration)
                .userId(user.getId())
                .email(user.getEmail())
                .refreshToken(newRawToken)
                .build();
    }

    @Override
    @Transactional
    public void logout(String rawRefreshToken) {
        String tokenHash = DigestUtils.md5DigestAsHex(rawRefreshToken.getBytes());
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setIsRevoked(true);
            refreshTokenRepository.save(token);
        });
    }
}
