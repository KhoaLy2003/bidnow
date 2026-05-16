package com.bidnow.identity.service.impl;

import com.bidnow.common.constant.ErrorCodes;
import com.bidnow.common.dto.event.UserRegisteredEvent;
import com.bidnow.common.dto.event.UserVerificationRequestedEvent;
import com.bidnow.common.dto.request.CreateUserProfileRequest;
import com.bidnow.common.exception.BadRequestException;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.common.exception.UnauthorizedException;
import com.bidnow.common.util.StringUtils;
import com.bidnow.identity.domain.entity.RefreshToken;
import com.bidnow.identity.domain.entity.User;
import com.bidnow.identity.domain.enums.AccountStatus;
import com.bidnow.identity.dto.request.LoginRequest;
import com.bidnow.identity.dto.request.RegisterRequest;
import com.bidnow.identity.dto.request.ResendOtpRequest;
import com.bidnow.identity.dto.request.VerifyOtpRequest;
import com.bidnow.identity.dto.response.LoginResponse;
import com.bidnow.identity.dto.response.RegisterResponse;
import com.bidnow.identity.dto.response.ResendOtpResponse;
import com.bidnow.identity.dto.response.VerifyOtpResponse;
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
    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final int OTP_MAX_FAILED_ATTEMPTS = 5;

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

    // -------------------------------------------------------------------------
    // Phase 1 – Issue #36: Register → generate OTP → PENDING_VERIFICATION
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already registered", ErrorCodes.INVALID_INPUT);
        }

        String otp = StringUtils.generateOtp();
        LocalDateTime otpExpiresAt = LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES);

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .isEmailVerified(false)
                .isActive(false)
                .accountStatus(AccountStatus.PENDING_VERIFICATION)
                .verificationOtp(otp)
                .otpExpiresAt(otpExpiresAt)
                .otpFailedAttempts(0)
                .failedLoginAttempts(0)
                .build();

        user = userRepository.save(user);

        kafkaProducer.publishUserVerificationRequestedEvent(
                UserVerificationRequestedEvent.builder()
                        .userId(user.getId())
                        .email(user.getEmail())
                        .otp(otp)
                        .otpExpiresAt(otpExpiresAt)
                        .requestedAt(LocalDateTime.now())
                        .build()
        );

        log.info("User registered, OTP sent for verification: {}", user.getEmail());
        return userMapper.toRegisterResponse(user);
    }

    // -------------------------------------------------------------------------
    // Phase 2 – Issue #37: Verify OTP → ACTIVE → create profile → USER_REGISTERED
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public VerifyOtpResponse verifyOtp(VerifyOtpRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new NotFoundException("User not found", ErrorCodes.NOT_FOUND));

        if (user.getAccountStatus() != AccountStatus.PENDING_VERIFICATION) {
            throw new BadRequestException("Account is not pending verification", ErrorCodes.ACCOUNT_NOT_PENDING);
        }

        // Scenario 3: Retry limit exceeded
        if (user.getOtpFailedAttempts() >= OTP_MAX_FAILED_ATTEMPTS) {
            throw new BadRequestException(
                    "Maximum OTP verification attempts exceeded. Please register again.",
                    ErrorCodes.OTP_MAX_ATTEMPTS
            );
        }

        // Scenario 2a: OTP expired
        if (user.getOtpExpiresAt() == null || LocalDateTime.now().isAfter(user.getOtpExpiresAt())) {
            throw new BadRequestException("OTP has expired", ErrorCodes.OTP_EXPIRED);
        }

        // Scenario 2b: OTP invalid
        if (!request.getOtp().equals(user.getVerificationOtp())) {
            user.setOtpFailedAttempts(user.getOtpFailedAttempts() + 1);
            userRepository.save(user);
            log.warn("Invalid OTP attempt ({}/{}) for user: {}",
                    user.getOtpFailedAttempts(), OTP_MAX_FAILED_ATTEMPTS, user.getEmail());
            throw new BadRequestException("Invalid OTP", ErrorCodes.OTP_INVALID);
        }

        // Scenario 1: Valid OTP — activate account and clear OTP fields
        user.setIsEmailVerified(true);
        user.setIsActive(true);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setVerificationOtp(null);
        user.setOtpExpiresAt(null);
        user.setOtpFailedAttempts(0);
        user = userRepository.save(user);

        // Synchronously create default profile in User Service
        userServiceClient.createUserProfile(CreateUserProfileRequest.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .build());

        // Emit USER_REGISTERED event (triggers Welcome Email in Notification Service)
        kafkaProducer.publishUserRegisteredEvent(
                UserRegisteredEvent.builder()
                        .userId(user.getId())
                        .email(user.getEmail())
                        .registeredAt(LocalDateTime.now())
                        .build()
        );

        log.info("User email verified and account activated: {}", user.getEmail());
        return userMapper.toVerifyOtpResponse(user);
    }

    // -------------------------------------------------------------------------
    // Resend OTP
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public ResendOtpResponse resendOtp(ResendOtpRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new NotFoundException("User not found", ErrorCodes.NOT_FOUND));

        if (user.getAccountStatus() != AccountStatus.PENDING_VERIFICATION) {
            throw new BadRequestException("Account is not pending verification", ErrorCodes.ACCOUNT_NOT_PENDING);
        }

        // Block resend while the current OTP is still valid — prevents spam
        if (user.getOtpExpiresAt() != null && LocalDateTime.now().isBefore(user.getOtpExpiresAt())) {
            throw new BadRequestException(
                    "Current OTP is still valid. Please wait until it expires before requesting a new one.",
                    ErrorCodes.OTP_NOT_EXPIRED
            );
        }

        String otp = StringUtils.generateOtp();
        LocalDateTime otpExpiresAt = LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES);

        user.setVerificationOtp(otp);
        user.setOtpExpiresAt(otpExpiresAt);
        user.setOtpFailedAttempts(0); // reset failed counter on resend
        userRepository.save(user);

        kafkaProducer.publishUserVerificationRequestedEvent(
                UserVerificationRequestedEvent.builder()
                        .userId(user.getId())
                        .email(user.getEmail())
                        .otp(otp)
                        .otpExpiresAt(otpExpiresAt)
                        .requestedAt(LocalDateTime.now())
                        .build()
        );

        log.info("OTP resent for user: {}", user.getEmail());
        return ResendOtpResponse.builder()
                .email(user.getEmail())
                .otpExpiresAt(otpExpiresAt)
                .build();
    }

    // -------------------------------------------------------------------------
    // Login
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials", ErrorCodes.UNAUTHORIZED));

        if (user.getAccountStatus() == AccountStatus.PENDING_VERIFICATION) {
            throw new UnauthorizedException("Email not verified. Please verify your OTP first.", ErrorCodes.UNAUTHORIZED);
        }

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
                .role(user.getRole())
                .refreshToken(rawRefreshToken)
                .build();
    }

    // -------------------------------------------------------------------------
    // Refresh & Logout
    // -------------------------------------------------------------------------

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
                .role(user.getRole())
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
