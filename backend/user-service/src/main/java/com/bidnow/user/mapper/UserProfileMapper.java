/*
 * BidNow Auction System
 */
package com.bidnow.user.mapper;

import com.bidnow.user.domain.entity.UserPreferences;
import com.bidnow.user.domain.entity.UserProfile;
import com.bidnow.user.domain.entity.UserRole;
import com.bidnow.user.dto.response.UserProfileResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserProfileMapper {

    public UserProfileResponse toResponse(UserProfile profile, List<UserRole> roles, UserPreferences preferences) {
        UserProfileResponse.UserProfileResponseBuilder builder = UserProfileResponse.builder()
                .userId(profile.getUserId())
                .displayName(profile.getDisplayName())
                .avatarUrl(profile.getAvatarUrl())
                .phoneNumber(profile.getPhoneNumber())
                .address(profile.getAddress())
                .city(profile.getCity())
                .country(profile.getCountry())
                .postalCode(profile.getPostalCode())
                .bio(profile.getBio())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .roles(roles.stream().map(r -> r.getRole().name()).toList());

        if (preferences != null) {
            builder.language(preferences.getLanguage())
                    .timezone(preferences.getTimezone())
                    .currency(preferences.getCurrency())
                    .emailNotifications(preferences.getEmailNotifications())
                    .pushNotifications(preferences.getPushNotifications())
                    .smsNotifications(preferences.getSmsNotifications());
        }

        return builder.build();
    }
}
