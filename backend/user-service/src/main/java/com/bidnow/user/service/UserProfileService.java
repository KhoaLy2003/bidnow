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

    /**
     * Returns the profile of the currently authenticated user.
     * The userId is resolved from the X-User-Id header injected by the API Gateway,
     * never from a client-supplied path variable.
     */
    UserProfileResponse getMyProfile(UUID userId);
}
