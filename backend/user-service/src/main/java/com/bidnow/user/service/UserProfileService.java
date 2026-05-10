/*
 * BidNow Auction System
 */
package com.bidnow.user.service;

import com.bidnow.common.dto.request.CreateUserProfileRequest;
import com.bidnow.user.dto.response.UserProfileResponse;

import java.util.UUID;

public interface UserProfileService {

    UserProfileResponse createUserProfile(CreateUserProfileRequest request);

    UserProfileResponse getUserProfile(UUID userId);
}
