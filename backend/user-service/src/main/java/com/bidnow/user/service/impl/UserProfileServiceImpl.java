/*
 * BidNow Auction System
 */
package com.bidnow.user.service.impl;

import com.bidnow.common.annotation.Audit;
import com.bidnow.common.annotation.Loggable;
import com.bidnow.common.constant.ErrorCodes;
import com.bidnow.common.dto.request.CreateUserProfileRequest;
import com.bidnow.common.enums.AuditAction;
import com.bidnow.common.exception.BadRequestException;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.common.util.AuditContextHolder;
import com.bidnow.user.domain.entity.UserPreferences;
import com.bidnow.user.domain.entity.UserProfile;
import com.bidnow.user.domain.entity.UserRole;
import com.bidnow.user.domain.enums.UserRoleType;
import com.bidnow.user.dto.request.UpdateUserProfileRequest;
import com.bidnow.user.dto.response.UserProfileResponse;
import com.bidnow.user.mapper.UserProfileMapper;
import com.bidnow.user.repository.UserPreferencesRepository;
import com.bidnow.user.repository.UserProfileRepository;
import com.bidnow.user.repository.UserRoleRepository;
import com.bidnow.user.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Loggable
public class UserProfileServiceImpl implements UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserPreferencesRepository userPreferencesRepository;
    private final UserProfileMapper userProfileMapper;

    @Override
    @Transactional
    @Audit(action = AuditAction.CREATE, entityType = "UserProfile", reason = "User profile created")
    public UserProfileResponse createUserProfile(CreateUserProfileRequest request) {
        if (userProfileRepository.existsByUserId(request.getUserId())) {
            throw new BadRequestException("User profile already exists", ErrorCodes.INVALID_INPUT);
        }

        UserProfile profile = UserProfile.builder()
                .userId(request.getUserId())
                .build();
        profile = userProfileRepository.save(profile);
        AuditContextHolder.setNewState(profile);

        UserRole role = UserRole.builder()
                .userId(request.getUserId())
                .role(UserRoleType.USER)
                .grantedBy(null)
                .build();
        userRoleRepository.save(role);

        UserPreferences preferences = UserPreferences.builder()
                .userId(request.getUserId())
                .build();
        userPreferencesRepository.save(preferences);

        log.info("Created user profile for userId: {}", request.getUserId());

        List<UserRole> roles = userRoleRepository.findByUserId(request.getUserId());
        UserPreferences savedPrefs = userPreferencesRepository.findByUserId(request.getUserId()).orElse(preferences);
        return userProfileMapper.toResponse(profile, roles, savedPrefs);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(UUID userId) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("User profile not found", ErrorCodes.NOT_FOUND));

        List<UserRole> roles = userRoleRepository.findByUserId(userId);
        UserPreferences preferences = userPreferencesRepository.findByUserId(userId).orElse(null);

        return userProfileMapper.toResponse(profile, roles, preferences);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(UUID userId) {
        return getUserProfile(userId);
    }

    @Override
    @Transactional
    @Audit(action = AuditAction.UPDATE, entityType = "UserProfile", reason = "User profile updated")
    public UserProfileResponse updateMyProfile(UUID userId, UpdateUserProfileRequest request) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("User profile not found", ErrorCodes.NOT_FOUND));

        AuditContextHolder.setOldState(UserProfile.builder()
                .displayName(profile.getDisplayName())
                .avatarUrl(profile.getAvatarUrl())
                .phoneNumber(profile.getPhoneNumber())
                .address(profile.getAddress())
                .city(profile.getCity())
                .country(profile.getCountry())
                .postalCode(profile.getPostalCode())
                .bio(profile.getBio())
                .build());

        userProfileMapper.updateProfileFromRequest(request, profile);

        userProfileRepository.save(profile);

        AuditContextHolder.setNewState(profile);

        List<UserRole> roles = userRoleRepository.findByUserId(userId);
        UserPreferences preferences = userPreferencesRepository.findByUserId(userId).orElse(null);

        log.info("Updated user profile for userId: {}", userId);
        return userProfileMapper.toResponse(profile, roles, preferences);
    }
}
